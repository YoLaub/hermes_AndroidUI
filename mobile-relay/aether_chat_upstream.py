"""Bounded server-to-server Hermes chat adapter; no device-controlled service URL."""
import asyncio
import json
import re
from urllib.parse import urlsplit

import httpx


class ChatUnavailable(Exception):
    """Sanitized failure. Never expose upstream bodies, cookies or provider errors."""


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', value):
        raise ChatUnavailable('Invalid upstream identifier')
    return value


async def events(response):
    """Bound text frames; discard unneeded payloads (including full-session done data)."""
    pending = b''
    name, data, size, seen, skipping = '', [], 0, False, False
    async for chunk in response.aiter_bytes(chunk_size=4096):
        pending += chunk
        if skipping:
            if b'\n' not in pending:
                pending = b''
                continue
            _, pending = pending.split(b'\n', 1)
            skipping = False
        while b'\n' in pending:
            raw, pending = pending.split(b'\n', 1)
            raw = raw.rstrip(b'\r')
            if raw.startswith(b'data:') and name != 'token':
                seen = True  # These event payloads are not forwarded or parsed.
                continue
            size += len(raw) + 1
            if size > 65536: raise ChatUnavailable('Upstream event too large')
            line = raw.decode('utf-8')
            if not line:
                if seen: yield name, json.loads('\n'.join(data)) if name == 'token' else {}
                name, data, size, seen = '', [], 0, False
            elif line.startswith('event:'): name = line[6:].strip()
            elif line.startswith('data:'):
                seen = True
                data.append(line[5:].lstrip(' '))
        if len(pending) + size > 65536:
            if name != 'token' and pending.startswith(b'data:'):
                pending, skipping, seen = b'', True, True
            else: raise ChatUnavailable('Upstream event too large')
    if pending or seen or skipping: raise ChatUnavailable('Incomplete upstream event')


class HermesChat:
    def __init__(self, origin, password, *, transport=None):
        parsed = urlsplit(origin)
        # The fixed Compose service may use its private Docker network; public services require TLS.
        internal = parsed.scheme == 'http' and parsed.hostname in ('hermes-webui', '127.0.0.1', 'localhost')
        if (not parsed.hostname or (parsed.scheme != 'https' and not internal) or
                parsed.username or parsed.password or parsed.path not in ('', '/') or
                parsed.query or parsed.fragment or '?' in origin or '#' in origin):
            raise ValueError('Invalid Hermes service origin')
        parsed.port  # Validate malformed ports now, before a request.
        self.origin, self._password, self.transport = origin.rstrip('/'), password, transport

    async def turn(self, profile, session_id, message):
        profile = identifier(profile)
        stream_id = None
        finished = False
        total = 0
        async with httpx.AsyncClient(base_url=self.origin, transport=self.transport,
                trust_env=False, follow_redirects=False, timeout=30) as http:
            async def post(path, body):
                async with http.stream('POST', path, json=body) as response:
                    if response.status_code != 200: raise ChatUnavailable('Hermes request failed')
                    raw = bytearray()
                    async for chunk in response.aiter_bytes(chunk_size=4096):
                        raw.extend(chunk)
                        if len(raw) > 65536: raise ChatUnavailable('Hermes response too large')
                    result = json.loads(raw)
                    if not isinstance(result, dict): raise ChatUnavailable('Invalid Hermes response')
                    return result
            try:
                async with asyncio.timeout(180):
                    await post('/api/auth/login', {'password': self._password})
                    if session_id is None:
                        created = await post('/api/session/new', {'profile': profile})
                        session_id = identifier(created['session']['session_id'])
                        yield 'session', session_id
                    session_id = identifier(session_id)
                    started = await post('/api/chat/start', {'session_id': session_id, 'profile': profile, 'message': message})
                    if started.get('session_id') != session_id: raise ChatUnavailable('Session mismatch')
                    stream_id = identifier(started['stream_id'])
                    async with http.stream('GET', '/api/chat/stream', params={'stream_id': stream_id}) as response:
                        if response.status_code != 200: raise ChatUnavailable('Hermes stream unavailable')
                        async for name, data in events(response):
                            if not isinstance(data, dict): raise ChatUnavailable('Invalid stream event')
                            if name == 'token':
                                text = data.get('text')
                                if not isinstance(text, str): raise ChatUnavailable('Invalid text')
                                total += len(text.encode('utf-8'))
                                if total > 65536: raise ChatUnavailable('Answer too large')
                                # Keep each downstream event comfortably below the transport limit.
                                for offset in range(0, len(text), 2000): yield 'text', text[offset:offset + 2000]
                            elif name in ('approval', 'clarify'):
                                yield 'needs_action', ''
                                return  # Never answer an approval on the user's behalf.
                            elif name == 'done':
                                finished = True
                                yield 'completed', ''
                                return
                            elif name in ('apperror', 'cancel', 'stream_end'):
                                raise ChatUnavailable('Hermes turn incomplete')
                    raise ChatUnavailable('Hermes stream interrupted')
            except asyncio.CancelledError:
                raise
            except Exception:
                raise ChatUnavailable('Hermes conversation unavailable') from None
            finally:
                if stream_id and not finished:
                    try:
                        async with asyncio.timeout(5):
                            await post('/api/chat/cancel', {'stream_id': stream_id})
                    except Exception:
                        pass  # Cancellation is best effort, never proof that tool effects were undone.

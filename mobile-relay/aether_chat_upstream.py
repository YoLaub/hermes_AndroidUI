"""Bounded server-to-server Hermes chat adapter; no device-controlled service URL."""

import asyncio
import json
import re
from urllib.parse import urlsplit

import httpx


class ChatUnavailable(Exception):
    """Sanitized failure. Never expose upstream bodies, cookies or provider errors."""


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", value):
        raise ChatUnavailable("Invalid upstream identifier")
    return value


async def events(response):
    """Bound text frames; discard unneeded payloads (including full-session done data)."""
    pending = b""
    name, data, size, seen, skipping = "", [], 0, False, False
    async for chunk in response.aiter_bytes():
        pending += chunk
        if skipping:
            if b"\n" not in pending:
                pending = b""
                continue
            _, pending = pending.split(b"\n", 1)
            skipping = False
        while b"\n" in pending:
            raw, pending = pending.split(b"\n", 1)
            raw = raw.rstrip(b"\r")
            if raw.startswith(b"data:") and name != "token":
                seen = True  # These event payloads are not forwarded or parsed.
                continue
            size += len(raw) + 1
            if size > 65536:
                raise ChatUnavailable("Upstream event too large")
            line = raw.decode("utf-8")
            if not line:
                if seen:
                    yield name, json.loads("\n".join(data)) if name == "token" else {}
                name, data, size, seen = "", [], 0, False
            elif line.startswith("event:"):
                name = line[6:].strip()
            elif line.startswith("data:"):
                seen = True
                data.append(line[5:].lstrip(" "))
        if len(pending) + size > 65536:
            if name != "token" and pending.startswith(b"data:"):
                pending, skipping, seen = b"", True, True
            else:
                raise ChatUnavailable("Upstream event too large")
    if pending or seen or skipping:
        raise ChatUnavailable("Incomplete upstream event")


async def follow_events(http, stream_id, session_id, authorized):
    """Follow once; after a network break wait for the same run's durable replay.

    Never repeat chat/start. A lost observer does not cancel the agent's work.
    """
    async def stream():
        async with http.stream("GET", "/api/chat/stream", params={"stream_id": stream_id}) as response:
            if response.status_code != 200:
                raise ChatUnavailable("Hermes stream unavailable")
            async for name, data in events(response):
                if not authorized():
                    raise ChatUnavailable("Credential revoked")
                if name == "stream_end":
                    return
                yield name, data

    try:
        async for event in stream():
            yield event
    except httpx.TransportError:
        pass
    # Network requests remain bounded; the execution itself has no wall-clock cap.
    while True:
        if not authorized():
            raise ChatUnavailable("Credential revoked")
        try:
            async with http.stream("GET", "/api/chat/stream/status", params={"stream_id": stream_id}) as response:
                raw = bytearray()
                async for chunk in response.aiter_bytes():
                    raw.extend(chunk)
                    if len(raw) > 65536:
                        raise ChatUnavailable("Runtime status too large")
            if response.status_code in (401, 403):
                raise ChatUnavailable("Hermes authorization unavailable")
            if response.status_code >= 500:
                await asyncio.sleep(2)
                continue
            if response.status_code != 200:
                raise ChatUnavailable("Hermes status unavailable")
            state = json.loads(raw)
            if state.get("stream_id") != stream_id or type(state.get("active")) is not bool:
                raise ChatUnavailable("Invalid runtime status")
            journal = state.get("journal") or {}
            if journal and (journal.get("session_id") != session_id or journal.get("run_id") != stream_id):
                raise ChatUnavailable("Runtime identity mismatch")
            if state["active"]:
                await asyncio.sleep(2)
                continue
            if state.get("replay_available") is not True or journal.get("terminal") is not True:
                raise ChatUnavailable("Runtime ended without a durable result")
            yield "replay", {}
            async for event in stream():
                yield event
            raise ChatUnavailable("Replay has no terminal event")
        except httpx.TransportError:
            await asyncio.sleep(2)


class HermesChat:
    def __init__(self, origin, password, *, transport=None):
        parsed = urlsplit(origin)
        # The fixed Compose service may use its private Docker network; public services require TLS.
        internal = parsed.scheme == "http" and parsed.hostname in ("hermes-webui", "127.0.0.1", "localhost")
        if (
            not parsed.hostname
            or (parsed.scheme != "https" and not internal)
            or parsed.username
            or parsed.password
            or parsed.path not in ("", "/")
            or parsed.query
            or parsed.fragment
            or "?" in origin
            or "#" in origin
        ):
            raise ValueError("Invalid Hermes service origin")
        _ = parsed.port  # Validate malformed ports now, before a request.
        self.origin, self._password, self.transport = origin.rstrip("/"), password, transport

    async def turn(self, profile, session_id, message, *, observation=None, authorized=lambda: True):
        profile = identifier(profile)
        stream_id = None
        finished = False
        cancel_requested = False
        total = 0
        async with httpx.AsyncClient(
            base_url=self.origin, transport=self.transport, trust_env=False, follow_redirects=False, timeout=30
        ) as http:

            async def post(path, body=None, **options):
                if not options:
                    options = {"json": body}
                async with http.stream("POST", path, **options) as response:
                    if response.status_code != 200:
                        raise ChatUnavailable("Hermes request failed")
                    raw = bytearray()
                    async for chunk in response.aiter_bytes(chunk_size=4096):
                        raw.extend(chunk)
                        if len(raw) > 65536:
                            raise ChatUnavailable("Hermes response too large")
                    result = json.loads(raw)
                    if not isinstance(result, dict):
                        raise ChatUnavailable("Invalid Hermes response")
                    return result

            try:
                await post("/api/auth/login", {"password": self._password})
                if session_id is None:
                    created = await post("/api/session/new", {"profile": profile})
                    session_id = identifier(created["session"]["session_id"])
                    yield "session", session_id
                session_id = identifier(session_id)
                payload = {"session_id": session_id, "profile": profile, "message": message}
                if not authorized():
                    raise ChatUnavailable("Credential revoked")
                if observation is not None:
                    # Session and path originate on the server, never from the device.
                    from uuid import uuid4

                    attachment = await post(
                        "/api/upload",
                        data={"session_id": session_id},
                        files={"file": (f"aether-{uuid4()}.jpg", observation, "image/jpeg")},
                    )
                    if (
                        attachment.get("mime") != "image/jpeg"
                        or attachment.get("is_image") is not True
                        or attachment.get("size") != len(observation)
                        or not all(
                            isinstance(attachment.get(k), str) and 0 < len(attachment[k]) <= 4096
                            for k in ("filename", "path")
                        )
                    ):
                        raise ChatUnavailable("Invalid upload result")
                    payload["attachments"] = [
                        {k: attachment[k] for k in ("filename", "path", "mime", "size", "is_image")}
                    ]
                if not authorized():
                    raise ChatUnavailable("Credential revoked")
                started = await post("/api/chat/start", payload)
                if started.get("session_id") != session_id:
                    raise ChatUnavailable("Session mismatch")
                stream_id = identifier(started["stream_id"])
                delivered = ""
                replay_position = None
                async for name, data in follow_events(http, stream_id, session_id, authorized):
                    if name == "replay":
                        replay_position = 0
                        continue
                    if name == "token":
                        text = data.get("text")
                        if not isinstance(text, str):
                            raise ChatUnavailable("Invalid text")
                        if replay_position is not None:
                            overlap = min(len(text), max(0, len(delivered) - replay_position))
                            if text[:overlap] != delivered[replay_position:replay_position + overlap]:
                                raise ChatUnavailable("Replay differs from delivered text")
                            replay_position += len(text)
                            text = text[overlap:]
                        total += len(text.encode("utf-8"))
                        if total > 65536:
                            raise ChatUnavailable("Answer too large")
                        delivered += text
                        for offset in range(0, len(text), 2000):
                            yield "text", text[offset:offset + 2000]
                    elif name in ("approval", "clarify"):
                        cancel_requested = True
                        yield "needs_action", ""
                        return
                    elif name == "done":
                        if replay_position is not None and replay_position < len(delivered):
                            raise ChatUnavailable("Incomplete replay")
                        finished = True
                        yield "completed", ""
                        return
                    elif name in ("apperror", "error", "cancel"):
                        raise ChatUnavailable("Hermes turn incomplete")
                raise ChatUnavailable("Hermes stream interrupted")
            except asyncio.CancelledError:
                cancel_requested = True
                raise
            except Exception:
                raise ChatUnavailable("Hermes conversation unavailable") from None
            finally:
                if stream_id and not finished and cancel_requested:
                    try:
                        async with asyncio.timeout(5):
                            await post("/api/chat/cancel", {"stream_id": stream_id})
                    except Exception:
                        pass  # Cancellation is best effort, never proof that tool effects were undone.

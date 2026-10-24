"""Bounded WebSocket adapter for the AETHER relay. No Android operations are imported."""
import asyncio
import secrets
import time
from datetime import datetime, timedelta

from aether_protocol import ClaimPoll, ClaimStart, Envelope, Hello, Result, Status, envelope
from database import (
    aether_device_profile,
    check_and_record_pairing_attempt,
    count_active_aether_claims,
    finish_aether_claim,
    start_aether_claim,
    verify_device_token,
)
from fastapi import APIRouter, HTTPException, Request, WebSocket, WebSocketDisconnect
from pydantic import ValidationError

CLAIM_TTL_SECONDS = 300
# No 0/O or 1/I/L: the code is read on one screen and typed or pasted into a chat.
CLAIM_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'


def new_claim_code():
    code = ''.join(secrets.choice(CLAIM_ALPHABET) for _ in range(8))
    return code[:4] + '-' + code[4:]


def agent_prompt(device_name, code):
    return (f"Connecte mon avatar AETHER « {device_name} » à ton profil : appelle l'outil "
            f"aether_claim_avatar du relais mobile avec le code {code}. Ce code expire dans 5 minutes.")


def router_for(relay, client_ip, enforce_not_blocked, note_auth_failure):
    router = APIRouter()

    @router.post('/api/aether/pair/start')
    def start_pairing(request: Request, req: ClaimStart):
        # Unauthenticated by design: the avatar has no credential yet. Bounded per source and globally.
        source = client_ip(request)
        enforce_not_blocked(source, 'aether')
        if not check_and_record_pairing_attempt(f'aether_start_{source}', max_attempts=5, window_seconds=CLAIM_TTL_SECONDS):
            raise HTTPException(status_code=429, detail='Too many AETHER pairing requests. Try again later.')
        if count_active_aether_claims() >= 10:
            raise HTTPException(status_code=429, detail='Too many pending AETHER pairing requests.')
        code, poll_token = new_claim_code(), secrets.token_urlsafe(32)
        if not start_aether_claim(req.device_id, req.device_name, code, poll_token, CLAIM_TTL_SECONDS):
            raise HTTPException(status_code=409, detail='This avatar id is already paired; use a new one.')
        return {'claim_code': code, 'poll_token': poll_token, 'expires_in_seconds': CLAIM_TTL_SECONDS,
                'agent_prompt': agent_prompt(req.device_name, code)}

    @router.post('/api/aether/pair/poll')
    def poll_pairing(req: ClaimPoll):
        # The poll token is 256 random bits, so polling every few seconds needs no attempt limit.
        device_token = 'tok_' + secrets.token_urlsafe(24)
        state, details = finish_aether_claim(req.poll_token, device_token)
        if state == 'pending':
            return {'status': 'pending'}
        if state == 'conflict':
            raise HTTPException(status_code=409, detail='This avatar id is already paired; use a new one.')
        if state == 'unknown':
            raise HTTPException(status_code=404, detail='Unknown or expired AETHER pairing request.')
        return {'status': 'paired', 'device_token': device_token, **details}

    async def receive(ws, timeout):
        text = await asyncio.wait_for(ws.receive_text(), timeout)
        if len(text.encode('utf-8')) > 16384:
            raise ValueError('Message too large')
        message = Envelope.model_validate_json(text)
        sent = datetime.fromisoformat(message.sent_at.replace('Z', '+00:00'))
        if sent.utcoffset() != timedelta(0):
            raise ValueError('UTC timestamp required')
        return message

    @router.websocket('/ws/aether')
    async def connect(ws: WebSocket):
        connection = None
        if 'aether.v1' not in ws.scope.get('subprotocols', []):
            await ws.close(code=1002)
            return
        await ws.accept(subprotocol='aether.v1')
        try:
            source = client_ip(ws)
            enforce_not_blocked(source, 'aether')
            header = ws.headers.get('authorization', '')
            token = header[7:] if header.startswith('Bearer ') else ''
            message = await receive(ws, 5)
            if message.type != 'client.hello':
                raise ValueError('Hello required')
            hello = Hello.model_validate(message.payload)
            if not token or not verify_device_token(hello.device_id, token, device_kind='aether'):
                note_auth_failure(source, 'aether')
                await ws.close(code=1008)
                return
            connection = await relay.connect(hello.device_id, ws, 'avatar.move_to' in hello.capabilities,
                profile=aether_device_profile(hello.device_id),
                authorized=lambda: verify_device_token(hello.device_id, token, device_kind='aether'))
            await ws.send_json(envelope('server.ready', {'session_id': connection.session_id,
                'heartbeat_interval_seconds': 20, 'max_command_ttl_seconds': 30}))
            while True:
                message = await receive(ws, 40)
                # Rotation/revocation applies to existing connections on their next message.
                if not verify_device_token(hello.device_id, token, device_kind='aether'):
                    await ws.close(code=1008)
                    return
                if message.type == 'telemetry.status':
                    connection.status = Status.model_validate(message.payload).model_dump()
                    connection.seen = time.monotonic()
                elif message.type.startswith('command.'):
                    result = Result.model_validate(message.payload)
                    if message.type == 'command.completed' and (result.avatar is None or result.avatar.moving):
                        raise ValueError('Completed command requires a stationary avatar')
                    if message.type == 'command.error' and result.code is None:
                        raise ValueError('Error code required')
                    relay.result(connection, message.type, result.model_dump())
                elif message.type == 'heartbeat' and not message.payload:
                    await ws.send_json(envelope('heartbeat', {}))
                else:
                    raise ValueError('Unsupported message')
        except WebSocketDisconnect:
            pass
        except (ValueError, ValidationError, asyncio.TimeoutError, HTTPException):
            await ws.close(code=1008)
        finally:
            if connection:
                relay.disconnect(connection)
    return router

"""Bounded WebSocket adapter for the AETHER relay. No Android operations are imported."""
import asyncio
import time
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import ValidationError
from database import aether_device_profile, verify_device_token
from aether_protocol import Envelope, Hello, Result, Status, envelope


def router_for(relay, client_ip, enforce_not_blocked, note_auth_failure):
    router = APIRouter()

    async def receive(ws, timeout):
        text = await asyncio.wait_for(ws.receive_text(), timeout)
        if len(text.encode('utf-8')) > 16384: raise ValueError('Message too large')
        message = Envelope.model_validate_json(text)
        sent = datetime.fromisoformat(message.sent_at.replace('Z', '+00:00'))
        if sent.utcoffset() != timedelta(0): raise ValueError('UTC timestamp required')
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
            if message.type != 'client.hello': raise ValueError('Hello required')
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
            if connection: relay.disconnect(connection)
    return router

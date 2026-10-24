"""Device-scoped AETHER command lifetimes, independent of HTTP and Android sessions."""
import asyncio
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable
from uuid import uuid4

from aether_protocol import Move, envelope


@dataclass(eq=False)
class Connection:
    device_id: str
    socket: object
    profile: str | None = None
    session_id: str = field(default_factory=lambda: uuid4().hex)
    status: dict | None = None
    seen: float = field(default_factory=time.monotonic)
    pending: tuple | None = None
    can_move: bool = True
    authorized: Callable[[], bool] = lambda: True

class AetherRelay:
    def __init__(self): self.connections = {}

    async def connect(self, device_id, socket, can_move=True, authorized=lambda: True, profile=None):
        old = self.connections.get(device_id)
        if old:
            self.disconnect(old)
        connection = Connection(device_id, socket, profile=profile, can_move=can_move, authorized=authorized)
        self.connections[device_id] = connection
        if old:
            try:
                await asyncio.wait_for(old.socket.close(code=1000), 1)
            except Exception:
                pass  # Replaced sessions cannot retain command authority if close fails.
        return connection

    def disconnect(self, connection):
        if self.connections.get(connection.device_id) is connection:
            self.connections.pop(connection.device_id)
        if connection.pending:
            cid, future, args = connection.pending
            if not future.done():
                future.set_result(self.error(cid, 'session_lost'))

    def owned(self, profile, device_id):
        """A profile reaches only the avatars it owns; an ownerless avatar is reachable by nobody."""
        connection = self.connections.get(device_id)
        if connection and connection.profile is not None and connection.profile == profile:
            return connection
        return None

    def status(self, profile):
        return {'devices': [{'device_id': c.device_id, 'connected': True,
                             'status': c.status if time.monotonic() - c.seen < 40 else None}
                            for c in self.connections.values() if self.owned(profile, c.device_id)]}

    @staticmethod
    def error(cid, code):
        return {'command_id': cid, 'status': 'failed', 'error': {'code': code, 'retryable': False}}

    async def move(self, profile, arguments):
        args = Move.model_validate(arguments)
        cid = uuid4().hex
        # Someone else's avatar answers exactly like a missing one, so its existence is not revealed.
        connection = self.owned(profile, args.device_id)
        if not connection:
            return self.error(cid, 'avatar_unavailable')
        if not connection.authorized():
            self.disconnect(connection)
            return self.error(cid, 'unauthorized')
        if not connection.can_move:
            return self.error(cid, 'unsupported_capability')
        if connection.pending:
            return self.error(cid, 'command_rejected')
        future = asyncio.get_running_loop().create_future()
        connection.pending = (cid, future, args)
        expires = datetime.fromtimestamp(time.time() + args.ttl_seconds, timezone.utc).isoformat().replace('+00:00', 'Z')
        try:
            # One total deadline includes both socket send and device execution.
            async def exchange():
                await connection.socket.send_json(envelope('command.avatar_move_to', {
                    'command_id': cid, 'expires_at': expires,
                    'screen_x': args.screen_x, 'screen_y': args.screen_y, 'posture': args.posture}))
                return await future
            return await asyncio.wait_for(exchange(), args.ttl_seconds)
        except asyncio.TimeoutError:
            return self.error(cid, 'command_expired')
        except Exception:
            self.disconnect(connection)
            return self.error(cid, 'session_lost')
        finally:
            connection.pending = None

    def result(self, connection, kind, payload):
        if self.connections.get(connection.device_id) is not connection or not connection.pending:
            return
        cid, future, args = connection.pending
        if future.done() or payload['command_id'] != cid or kind == 'command.accepted':
            return
        if kind == 'command.completed':
            avatar = payload.get('avatar')
            if (not avatar or avatar.get('moving') is not False or avatar.get('posture') != args.posture
                    or abs(avatar['screen_x'] - args.screen_x) > .01
                    or abs(avatar['screen_y'] - args.screen_y) > .01):
                future.set_result(self.error(cid, 'execution_failed'))
                return
            future.set_result({'command_id': cid, 'status': 'completed', 'avatar': payload.get('avatar')})
        elif kind == 'command.error':
            future.set_result(self.error(cid, payload.get('code') or 'execution_failed'))

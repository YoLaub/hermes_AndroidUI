"""Paired-avatar conversation state. Durable request IDs prevent automatic replay."""
import asyncio
import os
from contextlib import closing, aclosing
from uuid import UUID

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field, field_validator
import database
from aether_chat_upstream import HermesChat

ACTIVE = ('pending', 'running', 'uncertain')


def initialize():
    with closing(database.get_db()) as db, db:
        db.execute('CREATE TABLE IF NOT EXISTS aether_chat_sessions (device TEXT PRIMARY KEY, profile TEXT NOT NULL, session TEXT)')
        db.execute('CREATE TABLE IF NOT EXISTS aether_chat_turns (device TEXT NOT NULL, id TEXT NOT NULL, state TEXT NOT NULL, text TEXT NOT NULL DEFAULT \'\', PRIMARY KEY(device,id))')
        db.execute("UPDATE aether_chat_turns SET state='uncertain' WHERE state IN ('pending','running')")


def snapshot(device, request_id):
    with closing(database.get_db()) as db:
        row = db.execute('SELECT id AS request_id,state,text FROM aether_chat_turns WHERE device=? AND id=?', (device, request_id)).fetchone()
        if row is None: raise HTTPException(404, 'Unknown conversation request')
        return dict(row)


def update(device, request_id, state=None, text=None):
    with closing(database.get_db()) as db, db:
        if state: db.execute('UPDATE aether_chat_turns SET state=? WHERE device=? AND id=?', (state, device, request_id))
        if text is not None: db.execute('UPDATE aether_chat_turns SET text=? WHERE device=? AND id=?', (text, device, request_id))


class ChatService:
    def __init__(self, upstream):
        self.upstream = upstream
        self.tasks = {}

    async def close(self):
        keys = list(self.tasks)
        tasks = list(self.tasks.values())
        for task in tasks: task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        for device, request_id in keys:
            if snapshot(device, request_id)['state'] in ('pending', 'running'):
                update(device, request_id, 'uncertain')

    def start(self, device, profile, request_id, message, authorized):
        with closing(database.get_db()) as db, db:
            db.execute('BEGIN IMMEDIATE')
            previous = db.execute('SELECT 1 FROM aether_chat_turns WHERE device=? AND id=?', (device, request_id)).fetchone()
            if previous: return snapshot(device, request_id)
            if db.execute("SELECT 1 FROM aether_chat_turns WHERE device=? AND state IN ('pending','running','uncertain')", (device,)).fetchone():
                raise HTTPException(409, 'Conversation active or outcome uncertain')
            # Retain request IDs, bound retained answer text. No message contents are logged.
            if db.execute('SELECT COUNT(*) FROM aether_chat_turns WHERE device=?', (device,)).fetchone()[0] >= 10000:
                raise HTTPException(429, 'Conversation request limit reached')
            db.execute("UPDATE aether_chat_turns SET text='' WHERE device=?", (device,))
            row = db.execute('SELECT profile,session FROM aether_chat_sessions WHERE device=?', (device,)).fetchone()
            if row and row['profile'] != profile: raise HTTPException(409, 'Conversation profile mismatch')
            session = row['session'] if row else None
            db.execute('INSERT OR IGNORE INTO aether_chat_sessions(device,profile) VALUES (?,?)', (device, profile))
            db.execute("INSERT INTO aether_chat_turns(device,id,state) VALUES (?,?,'pending')", (device, request_id))
        task = asyncio.create_task(self.run(device, profile, request_id, session, message, authorized))
        self.tasks[(device, request_id)] = task
        task.add_done_callback(lambda _: self.tasks.pop((device, request_id), None))
        return snapshot(device, request_id)

    async def run(self, device, profile, request_id, session, message, authorized):
        text = ''
        update(device, request_id, 'running')
        try:
            if not authorized(): raise ValueError('revoked')
            async with aclosing(self.upstream.turn(profile, session, message)) as stream:
                async for kind, value in stream:
                    if not authorized(): raise ValueError('revoked')
                    if kind == 'session':
                        with closing(database.get_db()) as db, db:
                            db.execute('UPDATE aether_chat_sessions SET session=? WHERE device=? AND profile=?', (value, device, profile))
                    elif kind == 'text':
                        text += value
                        if len(text.encode('utf-8')) > 65536: raise ValueError('oversize')
                        update(device, request_id, text=text)
                    elif kind in ('completed', 'needs_action'):
                        update(device, request_id, kind)
                        return
            update(device, request_id, 'uncertain')
        except asyncio.CancelledError:
            update(device, request_id, 'uncertain')
            raise
        except Exception:
            update(device, request_id, 'uncertain')


class Send(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    request_id: str = Field(min_length=36, max_length=36)
    message: str = Field(min_length=1, max_length=4000)

    @field_validator('request_id')
    @classmethod
    def valid_id(cls, value):
        if str(UUID(value)) != value: raise ValueError('Canonical UUID required')
        return value

    @field_validator('message')
    @classmethod
    def nonempty(cls, value):
        if not value.strip(): raise ValueError('Message required')
        return value


def configured_service():
    origin = os.environ.get('AETHER_HERMES_ORIGIN')
    password = os.environ.get('AETHER_HERMES_PASSWORD')
    if not origin or not password: return None
    return ChatService(HermesChat(origin, password))


def router_for(client_ip, enforce_not_blocked, note_auth_failure):
    router = APIRouter(prefix='/api/aether/chat')

    def authorize(request):
        source = client_ip(request)
        enforce_not_blocked(source, 'aether')
        device = request.headers.get('x-aether-device', '')
        header = request.headers.get('authorization', '')
        token = header[7:] if header.startswith('Bearer ') else ''
        authorized = lambda: database.verify_device_token(device, token, device_kind='aether')
        if not device or len(device) > 128 or not token or not authorized():
            note_auth_failure(source, 'aether')
            raise HTTPException(401, 'Invalid avatar credential')
        return device, database.aether_device_profile(device), authorized

    @router.get('/status')
    async def status(request: Request):
        authorize(request)
        return {'available': getattr(request.app.state, 'aether_chat', None) is not None}

    @router.post('/send')
    async def send(body: Send, request: Request):
        device, profile, authorized = authorize(request)
        service = getattr(request.app.state, 'aether_chat', None)
        if service is None: raise HTTPException(503, 'Conversation not configured on relay')
        if not profile: raise HTTPException(403, 'Avatar profile unavailable')
        return service.start(device, profile, body.request_id, body.message, authorized)

    @router.get('/turn/{request_id}')
    async def turn(request_id: UUID, request: Request):
        device, _, _ = authorize(request)
        return snapshot(device, str(request_id))

    @router.post('/new')
    async def new_conversation(request: Request):
        device, _, _ = authorize(request)
        with closing(database.get_db()) as db, db:
            db.execute('BEGIN IMMEDIATE')
            if db.execute("SELECT 1 FROM aether_chat_turns WHERE device=? AND state IN ('pending','running')", (device,)).fetchone():
                raise HTTPException(409, 'Stop the active turn first')
            db.execute("UPDATE aether_chat_turns SET state='abandoned' WHERE device=? AND state='uncertain'", (device,))
            db.execute('DELETE FROM aether_chat_sessions WHERE device=?', (device,))
        return {'reset': True}

    @router.post('/cancel/{request_id}')
    async def cancel(request_id: UUID, request: Request):
        device, _, _ = authorize(request)
        result = snapshot(device, str(request_id))
        service = getattr(request.app.state, 'aether_chat', None)
        task = service.tasks.get((device, str(request_id))) if service else None
        if task:
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            result = snapshot(device, str(request_id))
            if result['state'] in ('pending', 'running'):
                update(device, str(request_id), 'uncertain')
                result = snapshot(device, str(request_id))
        return result

    return router

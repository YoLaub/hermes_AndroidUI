import asyncio
from uuid import uuid4
import pytest
import database
from aether_chat import ChatService, initialize, snapshot
from fastapi import HTTPException
from test_aether_routes import client, pair

@pytest.mark.asyncio
async def test_durable_identity_profile_and_single_turn(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'db'))
    database.init_db(); initialize()
    calls = []
    gate = asyncio.Event()
    class Upstream:
        async def turn(self, profile, session, message):
            calls.append((profile, session, message))
            yield 'session', 'isolated-session'
            await gate.wait()
            yield 'text', 'Bonjour'
            yield 'completed', ''
    service = ChatService(Upstream()); request_id = str(uuid4())
    service.start('avatar', 'john', request_id, 'Salut', lambda: True)
    await asyncio.sleep(0)
    assert service.start('avatar', 'john', request_id, 'Different text', lambda: True)['state'] == 'running'
    with pytest.raises(HTTPException) as busy:
        service.start('avatar', 'john', str(uuid4()), 'Other', lambda: True)
    assert busy.value.status_code == 409
    with pytest.raises(HTTPException): snapshot('another-avatar', request_id)
    gate.set(); await asyncio.gather(*list(service.tasks.values()))
    assert snapshot('avatar', request_id)['text'] == 'Bonjour'
    assert snapshot('avatar', request_id)['state'] == 'completed'
    assert calls == [('john', None, 'Salut')]
    initialize()  # Restart does not resend a completed ID.
    assert service.start('avatar', 'john', request_id, 'Salut', lambda: True)['state'] == 'completed'
    second = str(uuid4()); service.start('avatar', 'john', second, 'Encore', lambda: True)
    await asyncio.gather(*list(service.tasks.values()))
    assert calls[-1] == ('john', 'isolated-session', 'Encore')

@pytest.mark.asyncio
async def test_cancel_is_uncertain_and_closes_upstream(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'db'))
    database.init_db(); initialize()
    closed = []
    class Upstream:
        async def turn(self, *args):
            try:
                yield 'text', 'partial'
                await asyncio.Event().wait()
            finally: closed.append(True)
    service = ChatService(Upstream()); request_id = str(uuid4())
    service.start('avatar', 'john', request_id, 'Salut', lambda: True)
    await asyncio.sleep(0); await service.close()
    assert snapshot('avatar', request_id)['state'] == 'uncertain'
    assert closed == [True]
    with pytest.raises(HTTPException): service.start('avatar', 'john', str(uuid4()), 'Again', lambda: True)

def test_chat_authentication_and_disabled_service(client):
    assert client.get('/api/aether/chat/status').status_code == 401
    android = pair(client, 'android')
    assert client.get('/api/aether/chat/status', headers={'Authorization': 'Bearer ' + android, 'X-Aether-Device': 'android'}).status_code == 401
    token = pair(client, 'aether')
    headers = {'Authorization': 'Bearer ' + token, 'X-Aether-Device': 'aether'}
    assert client.get('/api/aether/chat/status', headers=headers).json() == {'available': False}
    assert client.post('/api/aether/chat/send', headers=headers, json={'request_id': str(uuid4()), 'message': 'Hi'}).status_code == 503
    assert client.post('/api/aether/chat/send', headers=headers, json={'request_id': str(uuid4()), 'message': 'Hi', 'profile': 'other'}).status_code == 422

def test_routes_bind_profile_and_reset_without_replaying(client):
    import server
    calls = []
    class Upstream:
        async def turn(self, profile, session, message):
            calls.append((profile, session, message))
            yield 'session', 'test-session'
            yield 'text', 'Salut'
            yield 'completed', ''
    server.app.state.aether_chat = ChatService(Upstream())
    token = pair(client, 'aether')
    headers = {'Authorization': 'Bearer ' + token, 'X-Aether-Device': 'aether'}
    request_id = str(uuid4())
    payload = {'request_id': request_id, 'message': 'Bonjour'}
    assert client.post('/api/aether/chat/send', headers=headers, json=payload).status_code == 200
    for _ in range(30):
        result = client.get('/api/aether/chat/turn/' + request_id, headers=headers)
        if result.json()['state'] == 'completed': break
    assert result.json() == {'request_id': request_id, 'state': 'completed', 'text': 'Salut'}
    assert client.post('/api/aether/chat/send', headers=headers, json=payload).json() == result.json()
    assert calls == [('john', None, 'Bonjour')]
    assert client.post('/api/aether/chat/new', headers=headers).json() == {'reset': True}
    assert client.post('/api/aether/chat/send', headers=headers, json=payload).json() == result.json()
    assert len(calls) == 1

@pytest.mark.asyncio
async def test_shutdown_before_task_first_runs_never_leaves_pending(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'db'))
    database.init_db(); initialize()
    class Upstream:
        async def turn(self, *args):
            raise AssertionError('must not send after shutdown')
            yield
    service = ChatService(Upstream()); request_id = str(uuid4())
    service.start('avatar', 'john', request_id, 'Salut', lambda: True)
    await service.close()
    assert snapshot('avatar', request_id)['state'] == 'uncertain'

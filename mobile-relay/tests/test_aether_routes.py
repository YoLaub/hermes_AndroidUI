import json
from concurrent.futures import ThreadPoolExecutor
import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
import database
import server
from aether_protocol import envelope

@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'relay.db'))
    monkeypatch.setenv('MOBILE_RELAY_ADMIN_TOKEN', 'admin-test')
    monkeypatch.setenv('HERMES_PROFILE_TOKENS', json.dumps({'john': 'john-test', 'mario': 'mario-test'}))
    with TestClient(server.app) as client:
        yield client


def pair(client, kind):
    response = client.post('/api/pair/generate', headers={'Authorization': 'Bearer admin-test'}, json={'device_kind': kind})
    assert response.status_code == 200
    response = client.post('/api/pair/verify', json={'code': response.json()['code'], 'device_id': kind, 'device_kind': kind})
    assert response.status_code == 200
    return response.json()['device_token']


def hello(kind='aether'):
    return envelope('client.hello', {'device_id': kind, 'client_version': 'test', 'capabilities': ['avatar.move_to', 'telemetry.status']})


def mcp(client, name, args=None, token='john-test'):
    response = client.post('/mcp', headers={'Authorization': 'Bearer ' + token}, json={'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call', 'params': {'name': name, 'arguments': args or {}}})
    return response.json()['result']


def test_android_token_cannot_connect_to_aether(client):
    token = pair(client, 'android')
    with client.websocket_connect('/ws/aether', headers={'Authorization': 'Bearer ' + token}, subprotocols=['aether.v1']) as ws:
        ws.send_json(hello('android'))
        with pytest.raises(WebSocketDisconnect): ws.receive_json()


def test_aether_mcp_round_trip_and_profile_boundary(client):
    token = pair(client, 'aether')
    with client.websocket_connect('/ws/aether', headers={'Authorization': 'Bearer ' + token}, subprotocols=['aether.v1']) as ws:
        ws.send_json(hello())
        assert ws.receive_json()['type'] == 'server.ready'
        assert mcp(client, 'aether_get_status', token='mario-test')['isError']
        status = json.loads(mcp(client, 'aether_get_status')['content'][0]['text'])
        assert status['devices'][0]['device_id'] == 'aether'
        with ThreadPoolExecutor() as executor:
            pending = executor.submit(mcp, client, 'aether_avatar_move_to', {'device_id': 'aether', 'screen_x': .2, 'screen_y': .6})
            command = ws.receive_json()
            cid = command['payload']['command_id']
            ws.send_json(envelope('command.accepted', {'command_id': cid}))
            ws.send_json(envelope('command.completed', {'command_id': cid, 'avatar': {'screen_x': .2, 'screen_y': .6, 'posture': 'walk', 'moving': False}}))
            result = json.loads(pending.result(timeout=3)['content'][0]['text'])
            assert result['status'] == 'completed'


def test_pairing_link_contains_only_relay_and_one_time_code(client, monkeypatch):
    from urllib.parse import urlsplit, parse_qs
    monkeypatch.setenv('AETHER_PUBLIC_URL', 'https://relay.example.test')
    response = client.post('/api/pair/generate', headers={'Authorization': 'Bearer admin-test'}, json={'device_kind': 'aether'})
    data = response.json()
    link = urlsplit(data['pairing_uri'])
    assert link.scheme == 'aether' and link.netloc == 'pair'
    assert parse_qs(link.query) == {'relay': ['https://relay.example.test'], 'code': [data['code']]}
    assert data['expires_in_seconds'] == 300


def test_aether_token_cannot_authenticate_android_socket(client):
    token = pair(client, 'aether')
    with client.websocket_connect('/ws/device') as ws:
        ws.send_json({'protocol': 'mobile-control/1', 'type': 'auth', 'device_id': 'aether', 'device_token': token})
        assert ws.receive_json()['type'] == 'auth_error'


def test_private_data_is_rejected_on_aether_telemetry(client):
    token = pair(client, 'aether')
    with client.websocket_connect('/ws/aether', headers={'Authorization': 'Bearer ' + token}, subprotocols=['aether.v1']) as ws:
        ws.send_json(hello()); ws.receive_json()
        ws.send_json(envelope('telemetry.status', {'availability': 'ready', 'avatar': {'screen_x': .2, 'screen_y': .6, 'posture': 'walk', 'moving': False}, 'screenshot': 'not-allowed'}))
        with pytest.raises(WebSocketDisconnect): ws.receive_json()

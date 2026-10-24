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


def pair(client, kind, profile_token='john-test'):
    if kind == 'aether':
        started = client.post('/api/aether/pair/start', json={'device_id': kind, 'device_name': 'Avatar'}).json()
        assert not mcp(client, 'aether_claim_avatar', {'code': started['claim_code']}, token=profile_token).get('isError')
        return client.post('/api/aether/pair/poll', json={'poll_token': started['poll_token']}).json()['device_token']
    response = client.post('/api/pair/generate', headers={'Authorization': 'Bearer admin-test'}, json={})
    assert response.status_code == 200
    response = client.post('/api/pair/verify', json={'code': response.json()['code'], 'device_id': kind})
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
        assert json.loads(mcp(client, 'aether_get_status', token='mario-test')['content'][0]['text']) == {'devices': []}
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


def test_admin_pairing_cannot_create_an_ownerless_aether_avatar(client):
    response = client.post('/api/pair/generate', headers={'Authorization': 'Bearer admin-test'}, json={'device_kind': 'aether'})
    assert response.status_code == 422
    response = client.post('/api/pair/verify', json={'code': 'ABC123', 'device_id': 'avatar', 'device_kind': 'aether'})
    assert response.status_code == 422


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


def test_aether_tools_are_listed_for_every_profile(client):
    response = client.post('/mcp', headers={'Authorization': 'Bearer mario-test'}, json={'jsonrpc': '2.0', 'id': 1, 'method': 'tools/list'})
    names = {tool['name'] for tool in response.json()['result']['tools']}
    assert {'aether_get_status', 'aether_avatar_move_to'} <= names


def test_other_profile_cannot_move_owned_avatar(client):
    token = pair(client, 'aether')
    with client.websocket_connect('/ws/aether', headers={'Authorization': 'Bearer ' + token}, subprotocols=['aether.v1']) as ws:
        ws.send_json(hello()); ws.receive_json()
        result = json.loads(mcp(client, 'aether_avatar_move_to', {'device_id': 'aether', 'screen_x': .2, 'screen_y': .6}, token='mario-test')['content'][0]['text'])
        assert result['error']['code'] == 'avatar_unavailable'

"""Agent-driven AETHER pairing: the avatar asks for a code, a profile's agent claims it."""
import json

import database
import pytest
import server
from aether_protocol import envelope
from fastapi.testclient import TestClient


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'relay.db'))
    monkeypatch.setenv('MOBILE_RELAY_ADMIN_TOKEN', 'admin-test')
    monkeypatch.setenv('HERMES_PROFILE_TOKENS', json.dumps({'john': 'john-test', 'mario': 'mario-test'}))
    with TestClient(server.app) as client:
        yield client


def start(client, device_id='avatar-1', name='Bureau'):
    return client.post('/api/aether/pair/start', json={'device_id': device_id, 'device_name': name})


def poll(client, poll_token):
    return client.post('/api/aether/pair/poll', json={'poll_token': poll_token})


def mcp(client, name, args, token):
    response = client.post('/mcp', headers={'Authorization': 'Bearer ' + token}, json={'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call', 'params': {'name': name, 'arguments': args}})
    return response.json()['result']


def claim(client, code, token='mario-test'):
    return mcp(client, 'aether_claim_avatar', {'code': code}, token)


def test_claimed_avatar_belongs_to_the_claiming_profile(client):
    started = start(client).json()
    assert poll(client, started['poll_token']).json() == {'status': 'pending'}
    claimed = claim(client, started['claim_code'])
    assert not claimed.get('isError')
    assert json.loads(claimed['content'][0]['text']) == {'status': 'claimed', 'device_id': 'avatar-1', 'device_name': 'Bureau'}
    paired = poll(client, started['poll_token']).json()
    assert paired['status'] == 'paired' and paired['profile'] == 'mario'
    # The enduring token is delivered once, to the avatar only.
    assert poll(client, started['poll_token']).status_code == 404
    assert paired['device_token'] not in claimed['content'][0]['text']
    with client.websocket_connect('/ws/aether', headers={'Authorization': 'Bearer ' + paired['device_token']}, subprotocols=['aether.v1']) as ws:
        ws.send_json(envelope('client.hello', {'device_id': 'avatar-1', 'client_version': 'test', 'capabilities': ['avatar.move_to']}))
        assert ws.receive_json()['type'] == 'server.ready'
        mario = json.loads(mcp(client, 'aether_get_status', {}, 'mario-test')['content'][0]['text'])
        john = json.loads(mcp(client, 'aether_get_status', {}, 'john-test')['content'][0]['text'])
        assert [d['device_id'] for d in mario['devices']] == ['avatar-1']
        assert john['devices'] == []


def test_claim_code_is_single_use_and_tolerant_of_formatting(client):
    started = start(client).json()
    assert not claim(client, started['claim_code'].lower().replace('-', ' ')).get('isError')
    assert claim(client, started['claim_code'], token='john-test')['isError']
    assert poll(client, started['poll_token']).json()['profile'] == 'mario'


def test_unknown_claim_code_is_refused(client):
    assert claim(client, 'AAAA-AAAA')['isError']


def test_expired_claim_cannot_be_claimed_or_polled(client):
    database.start_aether_claim('avatar-1', 'Bureau', 'ABCD2345', 'poll-secret', expires_in_seconds=-1)
    assert database.claim_aether_avatar('ABCD2345', 'mario') is None
    assert poll(client, 'poll-secret').status_code == 404


def test_already_paired_avatar_id_cannot_restart_pairing(client):
    assert database.atomic_register_or_update_device('avatar-1', 'secret', 'Bureau', device_kind='aether', profile='john')[0]
    assert start(client).status_code == 409


def test_start_rejects_unexpected_fields(client):
    response = client.post('/api/aether/pair/start', json={'device_id': 'avatar-1', 'device_name': 'Bureau', 'profile': 'john'})
    assert response.status_code == 422


def test_start_is_rate_limited_per_source(client):
    codes = [start(client, f'avatar-{i}').status_code for i in range(6)]
    assert codes[:5] == [200] * 5 and codes[5] == 429


def test_prompt_names_the_tool_and_code_but_never_the_poll_token(client):
    started = start(client).json()
    assert started['expires_in_seconds'] == 300
    assert 'aether_claim_avatar' in started['agent_prompt']
    assert started['claim_code'] in started['agent_prompt']
    assert started['poll_token'] not in started['agent_prompt']

import asyncio
import pytest
from aether_relay import AetherRelay

class Socket:
    def __init__(self): self.messages = asyncio.Queue()
    async def send_json(self, value): await self.messages.put(value)
    async def close(self, **kwargs): pass

@pytest.mark.asyncio
async def test_move_is_correlated_and_waits_for_completion():
    relay = AetherRelay(); ws = Socket()
    connection = await relay.connect('mac', ws)
    task = asyncio.create_task(relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7, 'posture': 'walk'}))
    command = await ws.messages.get(); cid = command['payload']['command_id']
    relay.result(connection, 'command.accepted', {'command_id': cid})
    assert not task.done()
    relay.result(connection, 'command.completed', {'command_id': 'wrong'})
    assert not task.done()
    relay.result(connection, 'command.completed', {'command_id': cid, 'avatar': {'screen_x': .4, 'screen_y': .7, 'posture': 'walk', 'moving': False}})
    assert (await task)['status'] == 'completed'

@pytest.mark.asyncio
async def test_disconnect_fails_pending_and_never_replays():
    relay = AetherRelay(); ws = Socket()
    connection = await relay.connect('mac', ws)
    task = asyncio.create_task(relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7, 'posture': 'walk'}))
    command = await ws.messages.get()
    replacement = Socket(); fresh = await relay.connect('mac', replacement)
    assert (await task)['error']['code'] == 'session_lost'
    relay.disconnect(connection)
    assert relay.connections['mac'] is fresh
    assert replacement.messages.empty()

@pytest.mark.asyncio
async def test_profile_bounds_and_single_command_limit():
    relay = AetherRelay(); ws = Socket(); await relay.connect('mac', ws)
    args = {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7, 'posture': 'walk'}
    with pytest.raises(ValueError): await relay.move('mario', args)
    with pytest.raises(ValueError): await relay.move('john', {**args, 'screen_x': True})
    with pytest.raises(ValueError): await relay.move('john', {**args, 'screen_x': 2})
    assert ws.messages.empty()
    task = asyncio.create_task(relay.move('john', args)); await ws.messages.get()
    assert (await relay.move('john', args))['error']['code'] == 'command_rejected'
    relay.disconnect(relay.connections['mac']); await task

@pytest.mark.asyncio
async def test_revoked_connection_cannot_receive_new_command():
    relay = AetherRelay(); ws = Socket()
    await relay.connect('mac', ws, authorized=lambda: False)
    result = await relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7})
    assert result['error']['code'] == 'unauthorized'
    assert ws.messages.empty()

@pytest.mark.asyncio
async def test_timeout_is_terminal_and_releases_slot():
    relay = AetherRelay(); ws = Socket(); connection = await relay.connect('mac', ws)
    result = await relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7, 'ttl_seconds': 1})
    assert result['error']['code'] == 'command_expired'
    assert connection.pending is None
    relay.result(connection, 'command.completed', {'command_id': result['command_id']})
    assert result['status'] == 'failed'

@pytest.mark.asyncio
async def test_other_device_cannot_complete_command():
    relay = AetherRelay(); ws = Socket(); owner = await relay.connect('mac', ws)
    other = await relay.connect('other', Socket())
    task = asyncio.create_task(relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7}))
    command = await ws.messages.get()
    relay.result(other, 'command.completed', {'command_id': command['payload']['command_id']})
    assert not task.done()
    relay.disconnect(owner); await task

@pytest.mark.asyncio
async def test_wrong_final_position_is_not_reported_as_completed():
    relay = AetherRelay(); ws = Socket(); owner = await relay.connect('mac', ws)
    task = asyncio.create_task(relay.move('john', {'device_id': 'mac', 'screen_x': .4, 'screen_y': .7}))
    command = await ws.messages.get()
    relay.result(owner, 'command.completed', {'command_id': command['payload']['command_id'], 'avatar': {'screen_x': .9, 'screen_y': .1, 'posture': 'walk', 'moving': False}})
    assert (await task)['error']['code'] == 'execution_failed'

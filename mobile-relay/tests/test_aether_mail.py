"""Owner-bound Apple Mail forwarding, no real Mac or email access."""
import asyncio

import pytest
from aether_mail import MailCommand
from aether_relay import AetherRelay


@pytest.mark.asyncio
async def test_mail_owner_capability_and_completed_result():
    sent=[]
    class Socket:
        async def send_json(self,value): sent.append(value)
    relay=AetherRelay()
    connection=await relay.connect('mac',Socket(),profile='john')
    args={'device_id':'mac','action':'accounts'}
    assert (await relay.mail('other',args))['error']['code']=='avatar_unavailable'
    assert (await relay.mail('john',args))['error']['code']=='unsupported_capability'
    connection.can_mail=True
    task=asyncio.create_task(relay.mail('john',args))
    for _ in range(10):
        await asyncio.sleep(0)
        if sent: break
    assert sent[0]['type']=='command.mail'
    payload=sent[0]['payload']
    assert set(payload)=={'command_id','expires_at','action'}
    relay.result(connection,'command.completed',{'command_id':payload['command_id'],'mail_result':'{"accounts":[]}'})
    assert (await task)['mail']=={'accounts':[]}


@pytest.mark.parametrize('action',['send','delete','shell'])
def test_mail_never_exposes_send_delete_or_script(action):
    with pytest.raises(ValueError): MailCommand(device_id='mac',action=action)

import json
from concurrent.futures import ThreadPoolExecutor

from aether_protocol import envelope
from test_aether_routes import client, hello, mcp, pair  # noqa: F401


def test_mail_mcp_roundtrip(client):  # noqa: F811
    token = pair(client, 'aether')
    with client.websocket_connect('/ws/aether', headers={
        'Authorization': 'Bearer ' + token, 'X-Aether-Mail': 'apple-mail-v1'
    }, subprotocols=['aether.v1']) as ws:
        message = hello()
        message['payload']['client_version'] = '0.2.0'
        ws.send_json(message)
        assert 'mail.manage' in ws.receive_json()['payload']['optional_capabilities']
        ws.send_json(envelope('client.capabilities', {'capabilities': ['mail.manage']}))
        ws.send_json(envelope('heartbeat', {}))
        ws.receive_json()
        with ThreadPoolExecutor() as pool:
            task = pool.submit(mcp, client, 'aether_apple_mail', {'device_id': 'aether', 'action': 'accounts'})
            command = ws.receive_json()
            assert command['type'] == 'command.mail'
            ws.send_json(envelope('command.completed', {
                'command_id': command['payload']['command_id'], 'mail_result': '{"accounts":[]}'
            }))
            assert json.loads(task.result(timeout=3)['content'][0]['text'])['mail'] == {'accounts': []}

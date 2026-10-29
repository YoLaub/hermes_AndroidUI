import asyncio

import pytest
from aether_relay import AetherRelay


@pytest.mark.asyncio
async def test_desktop_command_owner_capability_and_truthful_result():
    relay = AetherRelay()
    messages = []

    class Socket:
        async def send_json(self, message):
            messages.append(message)

    connection = await relay.connect("mac", Socket(), profile="john")
    args = {"device_id": "mac", "action": "app", "target": "spotify"}
    assert (await relay.open_desktop("other", args))["error"]["code"] == "avatar_unavailable"
    assert (await relay.open_desktop("john", args))["error"]["code"] == "unsupported_capability"
    connection.can_open = True
    task = asyncio.create_task(relay.open_desktop("john", args))
    while not messages:
        await asyncio.sleep(0)
    command = messages[-1]
    assert command["type"] == "command.desktop_open"
    relay.result(connection, "command.completed", {"command_id": command["payload"]["command_id"], "outcome": "opened"})
    result = await task
    assert result["outcome"] == "opened" and result["status"] == "completed"
    assert "avatar" not in result


import json
from concurrent.futures import ThreadPoolExecutor

from aether_protocol import envelope
from test_aether_routes import client, hello, mcp, pair  # noqa: F401


def test_desktop_mcp_websocket_roundtrip(client):  # noqa: F811
    token = pair(client, "aether")
    with client.websocket_connect(
        "/ws/aether",
        headers={"Authorization": "Bearer " + token, "X-Aether-Features": "desktop-open-v1"},
        subprotocols=["aether.v1"],
    ) as ws:
        message = hello()
        message["payload"]["client_version"] = "0.2.0"
        ws.send_json(message)
        assert ws.receive_json()["payload"]["optional_capabilities"] == ["avatar.gesture", "desktop.open"]
        ws.send_json(envelope("client.capabilities", {"capabilities": ["avatar.gesture", "desktop.open"]}))
        ws.send_json(envelope("heartbeat", {}))
        ws.receive_json()
        with ThreadPoolExecutor() as executor:
            task = executor.submit(
                mcp,
                client,
                "aether_desktop_open",
                {"device_id": "aether", "action": "youtube_search", "target": "jazz piano"},
            )
            command = ws.receive_json()
            assert command["type"] == "command.desktop_open"
            cid = command["payload"]["command_id"]
            ws.send_json(envelope("command.completed", {"command_id": cid, "outcome": "opened"}))
            result = json.loads(task.result(timeout=3)["content"][0]["text"])
            assert result["outcome"] == "opened"

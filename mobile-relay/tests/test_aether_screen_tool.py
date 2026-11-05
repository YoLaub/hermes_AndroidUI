import asyncio
import base64
from concurrent.futures import ThreadPoolExecutor

import pytest
from aether_protocol import envelope
from aether_relay import AetherRelay
from test_aether_routes import client as client
from test_aether_routes import hello, mcp, pair

JPEG = bytes.fromhex("ffd8 ffc0 000b 08 0002 0002 01 011100 ffda 0008 010100003f00 00 ffd9")
IMAGE = {"mime_type": "image/jpeg", "data_base64": base64.b64encode(JPEG).decode()}


@pytest.mark.asyncio
async def test_screen_is_owner_bound_and_requires_negotiation():
    relay = AetherRelay()

    class Socket:
        async def send_json(self, message):
            pass

    connection = await relay.connect("mac", Socket(), profile="john")
    args = {"device_id": "mac"}
    assert (await relay.observe("other", args))["error"]["code"] == "avatar_unavailable"
    assert (await relay.observe("john", args))["error"]["code"] == "unsupported_capability"
    connection.can_observe = True
    task = asyncio.create_task(relay.observe("john", args))
    await asyncio.sleep(0)
    cid = connection.pending[0]
    relay.result(connection, "command.completed", {"command_id": cid, "image": IMAGE})
    assert (await task)["image"] == IMAGE


@pytest.mark.parametrize("padding", [0, 20000])
def test_mcp_returns_image_block_not_json_encoded_pixels(client, padding):
    jpeg = (
        JPEG if not padding else JPEG[:2] + b"\xff\xe1" + (padding + 2).to_bytes(2, "big") + b"x" * padding + JPEG[2:]
    )
    image = {"mime_type": "image/jpeg", "data_base64": base64.b64encode(jpeg).decode()}
    token = pair(client, "aether")
    with client.websocket_connect(
        "/ws/aether",
        headers={"Authorization": "Bearer " + token, "X-Aether-Observation": "screen-observe-v1"},
        subprotocols=["aether.v1"],
    ) as ws:
        message = hello()
        message["payload"]["client_version"] = "0.2.0"
        ws.send_json(message)
        assert "screen.observe" in ws.receive_json()["payload"]["optional_capabilities"]
        ws.send_json(envelope("client.capabilities", {"capabilities": ["screen.observe"]}))
        ws.send_json(envelope("heartbeat", {}))
        ws.receive_json()
        with ThreadPoolExecutor() as executor:
            task = executor.submit(mcp, client, "aether_observe_screen", {"device_id": "aether"})
            command = ws.receive_json()
            assert command["type"] == "command.observe_screen"
            ws.send_json(
                envelope("command.completed", {"command_id": command["payload"]["command_id"], "image": image})
            )
            result = task.result(timeout=3)
            assert result["content"][1] == {"type": "image", "mimeType": "image/jpeg", "data": image["data_base64"]}
            assert image["data_base64"] not in result["content"][0]["text"]


@pytest.mark.asyncio
@pytest.mark.parametrize("revoked,invalid", [(True, False), (False, True)])
async def test_no_pixels_on_revocation_or_invalid_capture(revoked, invalid):
    relay = AetherRelay()
    allowed = True

    class Socket:
        async def send_json(self, message):
            pass

    connection = await relay.connect("mac", Socket(), profile="john", authorized=lambda: allowed)
    connection.can_observe = True
    task = asyncio.create_task(relay.observe("john", {"device_id": "mac"}))
    await asyncio.sleep(0)
    cid = connection.pending[0]
    allowed = not revoked
    image = {**IMAGE, "data_base64": "invalid"} if invalid else IMAGE
    relay.result(connection, "command.completed", {"command_id": cid, "image": image})
    result = await task
    assert result["status"] == "failed" and "image" not in result

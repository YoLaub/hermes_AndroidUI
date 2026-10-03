"""
mobile_control_status vs mobile_observe: same session, same device, same profile,
observation mode allowed, and distinct errors for "absent on the relay" vs "absent on the phone".
"""
import json
import logging
import os
import signal
import threading

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

from server import app, manager
from database import init_db, register_device

client = None  # one TestClient (one event loop) shared by HTTP and WebSocket, like production


@pytest.fixture(scope="module", autouse=True)
def _shared_client():
    global client
    with TestClient(app) as c:
        client = c
        yield


DEVICE_ID = "dev_obs00001"
DEVICE_TOKEN = "tok_device_secret_OBS1"
AUTH = {"protocol": "mobile-control/1", "type": "auth", "device_id": DEVICE_ID, "device_token": DEVICE_TOKEN}
JOHN = {"Authorization": "Bearer token_for_john"}


def start_msg(mode="interaction", session_id="ses_obs1"):
    return {"protocol": "mobile-control/1", "type": "session_start", "session_id": session_id,
            "target_package": "com.linkedin.android", "allowed_profile": "john",
            "mode": mode, "duration_seconds": 900}


@pytest.fixture(autouse=True)
def fresh_state():
    def _boom(*_):
        raise TimeoutError("test stuck for 15s")
    old = signal.signal(signal.SIGALRM, _boom)
    signal.alarm(15)
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    register_device(DEVICE_ID, DEVICE_TOKEN, "Test phone")
    manager.active_connections.clear()
    manager.active_sessions.clear()
    manager.pending_commands.clear()
    manager.device_in_flight.clear()
    yield
    signal.alarm(0)
    signal.signal(signal.SIGALRM, old)


def mcp_call(tool, arguments=None):
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 7, "method": "tools/call",
                                                   "params": {"name": tool, "arguments": arguments or {}}})
    assert res.status_code == 200, res.text
    result = res.json()["result"]
    return result.get("isError", False), result["content"][0]["text"]


def connect_and_start(ws, mode="interaction"):
    ws.send_text(json.dumps(AUTH))
    assert ws.receive_json()["type"] == "auth_ok"
    ws.receive_json()
    ws.send_text(json.dumps(start_msg(mode)))
    assert ws.receive_json()["type"] == "session_started_ack"


def observe_via_phone(ws, phone_reply):
    """Call mobile_observe (blocks on the phone) from a thread; answer as the phone would."""
    box = {}
    t = threading.Thread(target=lambda: box.update(out=mcp_call("mobile_observe")))
    t.start()
    cmd = ws.receive_json()
    assert cmd["type"] == "command"
    ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "result", "command_id": cmd["command_id"], **phone_reply}))
    t.join(10)
    return cmd, box["out"]


OBSERVED = {"status": "success", "message": "ok", "data": {
    "screen_revision": "rev_1", "package_name": "com.linkedin.android",
    "elements": [{"element_ref": "el_1", "class_name": "Button", "text": "Commenter", "clickable": True}]}}


def test_status_and_observe_use_the_same_session_device_and_profile():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        _, status = mcp_call("mobile_control_status")
        assert "ses_obs1" in status and DEVICE_ID in status and "john" in status

        cmd, (is_error, text) = observe_via_phone(ws, OBSERVED)
        assert not is_error and "Commenter" in text
        # The command carries exactly the session and device status reported.
        assert cmd["session_id"] == "ses_obs1"
        assert cmd["device_id"] == DEVICE_ID
        assert cmd["target_package"] == "com.linkedin.android"
        assert cmd["operation"] == "observe"


def test_observation_mode_allows_observe_but_still_denies_interaction():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, mode="observation")
        _, status = mcp_call("mobile_control_status")
        assert "observation" in status
        cmd, (is_error, text) = observe_via_phone(ws, OBSERVED)
        assert not is_error and cmd["operation"] == "observe"
        is_error, text = mcp_call("mobile_click_element", {"element_ref": "el_1"})
        assert is_error and text.startswith("MODE_DENIED")


def test_absent_on_the_relay_and_absent_on_the_phone_are_different_errors():
    # 1) The relay has no session for john.
    is_error, relay_absent = mcp_call("mobile_observe")
    assert is_error and relay_absent.startswith("SESSION_REQUIRED")
    assert "relais" in relay_absent.lower()

    # 2) The relay has one, the phone says it has none.
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        _, (is_error, phone_absent) = observe_via_phone(ws, {
            "status": "rejected", "error_code": "SESSION_NOT_ON_PHONE", "message": "Aucune session sur le téléphone."})
        assert is_error and phone_absent.startswith("SESSION_NOT_ON_PHONE")
        assert "téléphone" in phone_absent.lower()
        assert relay_absent.split(":")[0] != phone_absent.split(":")[0]
        # The relay no longer trusts its own view: it closes its stale session.
        assert manager.active_sessions == {}
        _, status = mcp_call("mobile_control_status")
        assert "Aucune session" in status


def test_phone_session_id_mismatch_is_its_own_error_and_also_resyncs_the_relay():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        _, (is_error, text) = observe_via_phone(ws, {
            "status": "rejected", "error_code": "SESSION_ID_MISMATCH", "message": "Autre session sur le téléphone."})
        assert is_error and text.startswith("SESSION_ID_MISMATCH")
        assert manager.active_sessions == {}


def test_dead_socket_gives_device_offline_not_a_server_error():
    class DeadSocket:
        async def send_text(self, _):
            raise RuntimeError("socket is dead")
    from server import ActiveSession
    import time
    manager.active_connections[DEVICE_ID] = DeadSocket()
    manager.set_active_session(ActiveSession("ses_dead", DEVICE_ID, "com.linkedin.android", "john", "interaction", time.time() + 600))
    is_error, text = mcp_call("mobile_observe")
    assert is_error and text.startswith("DEVICE_OFFLINE")
    assert DEVICE_ID not in manager.active_connections
    assert manager.active_sessions == {}


def test_reconnect_gap_after_a_drop_is_reported_as_relay_side_absence():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
    is_error, text = mcp_call("mobile_observe")
    assert is_error and text.startswith("SESSION_REQUIRED")


def test_commands_follow_the_phone_to_its_new_socket_after_a_reconnect():
    with client.websocket_connect("/ws/device") as ws1:
        connect_and_start(ws1)
        with client.websocket_connect("/ws/device") as ws2:
            ws2.send_text(json.dumps(AUTH))
            ws2.receive_json()
            assert ws2.receive_json()["session_id"] == "ses_obs1"
            cmd, (is_error, _) = observe_via_phone(ws2, OBSERVED)
            assert not is_error and cmd["session_id"] == "ses_obs1"


def test_tool_calls_are_logged_with_ids_and_no_secrets(caplog):
    caplog.set_level(logging.INFO, logger="mobile-relay")
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        observe_via_phone(ws, {"status": "rejected", "error_code": "SESSION_NOT_ON_PHONE", "message": "x"})
    mcp_call("mobile_observe")
    joined = "\n".join(r.getMessage() for r in caplog.records if r.name == "mobile-relay")
    for expected in (
        f"event=mcp_tool_call tool=mobile_observe profile=john session_id=ses_obs1 device_id={DEVICE_ID} mode=interaction",
        "event=mcp_tool_result tool=mobile_observe",
        "error_code=SESSION_NOT_ON_PHONE",
        "event=session_desync",
        "event=mcp_tool_refused tool=mobile_observe profile=john reason=SESSION_REQUIRED",
    ):
        assert expected in joined, f"missing log: {expected}\n{joined}"
    for secret in (DEVICE_TOKEN, "token_for_john", "test_admin_token_12345"):
        assert secret not in joined

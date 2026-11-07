"""A phone dedicated to the agent: paired by the admin for the standing `phone-agent` profile, it gets a session
opened by the relay itself (every app, interaction, every consent), and only the phone-agent token can drive it."""
import json
import os
import signal
import threading

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"
os.environ["MOBILE_CONTROL_TOKEN_PHONE_AGENT"] = "token_for_phone_agent"

import database
from database import atomic_register_or_update_device, init_db, register_device
from server import app, manager

client = None  # one TestClient (one event loop) shared by HTTP and WebSocket, like production


@pytest.fixture(scope="module", autouse=True)
def _shared_client():
    global client
    with TestClient(app) as c:
        client = c
        yield

ADMIN = {"X-Admin-Token": "test_admin_token_12345"}
AGENT = {"Authorization": "Bearer token_for_phone_agent"}
JOHN = {"Authorization": "Bearer token_for_john"}
AGENT_DEVICE, AGENT_TOKEN = "dev_agent_01", "tok_agent_secret_AAAA"
PLAIN_DEVICE, PLAIN_TOKEN = "dev_plain_01", "tok_plain_secret_BBBB"


def auth(dev, tok):
    return {"protocol": "mobile-control/1", "type": "auth", "device_id": dev, "device_token": tok}


@pytest.fixture(autouse=True)
def fail_fast_instead_of_hanging():
    def _boom(*_):
        raise TimeoutError("relay stayed silent for 15s (expected a message)")
    old = signal.signal(signal.SIGALRM, _boom)
    signal.alarm(15)
    yield
    signal.alarm(0)
    signal.signal(signal.SIGALRM, old)


@pytest.fixture(autouse=True)
def fresh_state():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    assert atomic_register_or_update_device(AGENT_DEVICE, AGENT_TOKEN, "Agent phone",
                                            device_kind="agent_phone", profile="phone-agent")[0]
    register_device(PLAIN_DEVICE, PLAIN_TOKEN, "Ordinary phone")
    manager.active_connections.clear()
    manager.active_sessions.clear()
    manager.pending_commands.clear()
    manager.device_in_flight.clear()


def mcp(headers, tool, arguments=None):
    res = client.post("/mcp", headers=headers, json={
        "jsonrpc": "2.0", "id": 1, "method": "tools/call",
        "params": {"name": tool, "arguments": arguments or {}}})
    assert res.status_code == 200, res.text
    return res.json()["result"]


def test_admin_pairs_an_agent_phone_bound_to_the_phone_agent_profile():
    code = client.post("/api/pair/generate", headers=ADMIN, json={"device_kind": "agent_phone"}).json()["code"]
    ok = client.post("/api/pair/verify", json={"device_kind": "agent_phone", "code": code,
                                               "device_id": "dev_new_agent", "device_name": "Agent"})
    assert ok.status_code == 200
    assert database.verify_device_token("dev_new_agent", ok.json()["device_token"], device_kind="agent_phone")
    assert database.device_profile("dev_new_agent") == "phone-agent"


def test_an_android_pairing_code_cannot_make_an_agent_phone():
    code = client.post("/api/pair/generate", headers=ADMIN, json={}).json()["code"]
    bad = client.post("/api/pair/verify", json={"device_kind": "agent_phone", "code": code,
                                                "device_id": "dev_x", "device_name": "x"})
    assert bad.status_code == 400


def test_connecting_agent_phone_gets_a_standing_session_opened_by_the_relay():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(auth(AGENT_DEVICE, AGENT_TOKEN)))
        assert ws.receive_json()["type"] == "auth_ok"
        state = ws.receive_json()
        assert state["type"] == "session_state" and state["active"] is True
        assert state["profile"] == "phone-agent" and state["mode"] == "interaction"
        assert state["target_package"] == "*"
        assert {"screenshots", "calendar", "sms_read", "call_log_read", "sms_send", "call_place",
                "calendar_write"} <= set(state["allow"])
        assert state["expires_in_seconds"] > 30 * 24 * 3600
    assert manager.active_sessions == {}  # disconnecting closes it; it is reopened on reconnection


def test_an_ordinary_phone_still_gets_no_session_on_connection():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(auth(PLAIN_DEVICE, PLAIN_TOKEN)))
        ws.receive_json()
        assert ws.receive_json()["active"] is False


def test_phone_agent_token_drives_the_agent_phone_without_any_consent_or_confirmation_setup():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(auth(AGENT_DEVICE, AGENT_TOKEN)))
        ws.receive_json(); ws.receive_json()
        box = {}
        t = threading.Thread(target=lambda: box.update(out=mcp(AGENT, "mobile_sms_send", {"to": "Marie", "text": "salut"})))
        t.start()
        cmd = ws.receive_json()
        assert cmd["type"] == "command" and cmd["operation"] == "sms_send"
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "result", "command_id": cmd["command_id"],
                                 "status": "success", "message": "envoyé"}))
        t.join(10)
        assert not box["out"].get("isError")


def test_john_token_cannot_drive_the_agent_phone_and_phone_agent_token_cannot_drive_john():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(auth(AGENT_DEVICE, AGENT_TOKEN)))
        ws.receive_json(); ws.receive_json()
        out = mcp(JOHN, "mobile_observe")
        assert out["isError"] and out["content"][0]["text"].startswith("SESSION_REQUIRED")
    manager.active_sessions.clear()
    out = mcp(AGENT, "mobile_observe")  # no agent phone connected
    assert out["isError"] and out["content"][0]["text"].startswith("SESSION_REQUIRED")


def test_the_agent_phone_cannot_start_a_john_session():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(auth(AGENT_DEVICE, AGENT_TOKEN)))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_j",
                                 "target_package": "com.x", "allowed_profile": "john",
                                 "mode": "interaction", "duration_seconds": 900}))
        err = ws.receive_json()
        assert err["type"] == "session_error" and err["error_code"] == "PROFILE_NOT_ALLOWED"
        assert manager.active_sessions[AGENT_DEVICE].allowed_profile == "phone-agent"


def test_an_agent_phone_token_does_not_authenticate_as_an_ordinary_phone_or_the_reverse():
    assert not database.verify_device_token(AGENT_DEVICE, AGENT_TOKEN)  # default kind: android
    assert not database.verify_device_token(PLAIN_DEVICE, PLAIN_TOKEN, device_kind="agent_phone")

"""
Session start confirmation, state sync on (re)connection, and secret-free logs.

The phone must learn from the relay whether a session was registered or refused,
the relay must say why a device was not authenticated, and every step must be
logged without tokens so Android and relay logs can be correlated.
"""
import json
import logging
import os
import signal

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

from server import app, manager
from database import init_db, register_device

client = TestClient(app)

DEVICE_ID = "dev_conf0001"
DEVICE_TOKEN = "tok_device_secret_AAAA1111"
SECRETS = [DEVICE_TOKEN, "token_for_john", "test_admin_token_12345", "tok_invented_BBBB2222"]

AUTH = {"protocol": "mobile-control/1", "type": "auth", "device_id": DEVICE_ID, "device_token": DEVICE_TOKEN}


def start_msg(profile="john", session_id="ses_conf1"):
    return {"protocol": "mobile-control/1", "type": "session_start", "session_id": session_id,
            "target_package": "com.linkedin.android", "allowed_profile": profile,
            "mode": "interaction", "duration_seconds": 900}


@pytest.fixture(autouse=True)
def fail_fast_instead_of_hanging():
    """receive_json() blocks forever when the relay stays silent: turn that into a failure."""
    def _boom(*_):
        raise TimeoutError("relay stayed silent for 5s (expected a message)")
    old = signal.signal(signal.SIGALRM, _boom)
    signal.alarm(5)
    yield
    signal.alarm(0)
    signal.signal(signal.SIGALRM, old)


@pytest.fixture(autouse=True)
def fresh_state():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    register_device(DEVICE_ID, DEVICE_TOKEN, "Test phone")
    manager.active_connections.clear()
    manager.active_sessions.clear()
    manager.pending_commands.clear()
    manager.device_in_flight.clear()


def status_text():
    res = client.post("/mcp", headers={"Authorization": "Bearer token_for_john"}, json={
        "jsonrpc": "2.0", "id": 1, "method": "tools/call",
        "params": {"name": "mobile_control_status", "arguments": {}}})
    return res.json()["result"]["content"][0]["text"]


def lines(caplog):
    return [r.getMessage() for r in caplog.records if r.name == "mobile-relay"]


def test_auth_ok_is_followed_by_the_servers_session_state():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        assert ws.receive_json()["type"] == "auth_ok"
        state = ws.receive_json()
        assert state["type"] == "session_state"
        assert state["active"] is False


def test_start_is_acknowledged_with_session_profile_and_device_then_visible_to_john():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps(start_msg()))
        ack = ws.receive_json()
        assert ack["type"] == "session_started_ack"
        assert ack["session_id"] == "ses_conf1"
        assert ack["profile"] == "john"
        assert ack["device_id"] == DEVICE_ID
        assert ack["expires_in_seconds"] > 0
        assert "Session active trouvée" in status_text()


def test_start_before_authentication_gets_an_explicit_error_not_silence():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(start_msg()))
        err = ws.receive_json()
        assert err["type"] == "session_error"
        assert err["error_code"] == "DEVICE_NOT_AUTHENTICATED"
    assert manager.active_sessions == {}


def test_failed_authentication_says_why_and_closes():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps({**AUTH, "device_token": "tok_invented_BBBB2222"}))
        err = ws.receive_json()
        assert err["type"] == "auth_error"
        assert err["error_code"] == "DEVICE_AUTH_FAILED"


def test_refused_profile_is_reported_and_nothing_is_registered():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps(start_msg(profile="mario")))
        err = ws.receive_json()
        assert err["type"] == "session_error"
        assert err["error_code"] == "PROFILE_NOT_ALLOWED"
    assert manager.active_sessions == {}


def test_reconnecting_device_is_told_the_servers_view_of_its_session():
    with client.websocket_connect("/ws/device") as ws1:
        ws1.send_text(json.dumps(AUTH))
        ws1.receive_json(); ws1.receive_json()
        ws1.send_text(json.dumps(start_msg()))
        ws1.receive_json()
        # A second socket for the same device supersedes the first one.
        with client.websocket_connect("/ws/device") as ws2:
            ws2.send_text(json.dumps(AUTH))
            assert ws2.receive_json()["type"] == "auth_ok"
            state = ws2.receive_json()
            assert state["type"] == "session_state"
            assert state["active"] is True
            assert state["session_id"] == "ses_conf1"
            assert state["profile"] == "john"


def test_session_lost_on_disconnect_is_reported_as_inactive_after_reconnect():
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps(start_msg()))
        ws.receive_json()
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json()
        assert ws.receive_json()["active"] is False
    assert "Aucune session" in status_text()


def test_every_step_is_logged_without_any_secret(caplog):
    caplog.set_level(logging.INFO, logger="mobile-relay")
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps(start_msg(profile="mario", session_id="ses_refused")))
        ws.receive_json()
        ws.send_text(json.dumps(start_msg()))
        ws.receive_json()
        status_text()
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps({**AUTH, "device_token": "tok_invented_BBBB2222"}))
        ws.receive_json()

    out = lines(caplog)
    joined = "\n".join(out)
    for expected in (
        f"event=device_authenticated device_id={DEVICE_ID}",
        f"event=session_start_received device_id={DEVICE_ID} session_id=ses_refused profile_requested=mario",
        "event=session_refused",
        "reason=PROFILE_NOT_ALLOWED",
        f"event=session_registered device_id={DEVICE_ID} session_id=ses_conf1 profile=john",
        f"event=session_ack_sent device_id={DEVICE_ID} session_id=ses_conf1",
        "event=device_auth_failed",
        "event=connection_closed",
        "reason=",
        "event=status_lookup profile=john",
        "sessions_known=1",
        "match=true",
    ):
        assert expected in joined, f"missing log: {expected}\n{joined}"
    for secret in SECRETS:
        assert secret not in joined, "a secret leaked into the logs"


def test_status_lookup_log_exposes_a_profile_mismatch(caplog):
    caplog.set_level(logging.INFO, logger="mobile-relay")
    from server import ActiveSession
    import time
    # A session exists for the device but under a different profile key: lookup must say no match.
    s = ActiveSession("ses_x", DEVICE_ID, "com.linkedin.android", "john", "interaction", time.time() + 600)
    s.allowed_profile = "other"
    manager.active_sessions[DEVICE_ID] = s
    assert "Aucune session" in status_text()
    joined = "\n".join(lines(caplog))
    assert "event=status_lookup profile=john" in joined
    assert "match=false" in joined
    assert "session_profiles=other" in joined


def test_health_and_status_expose_the_same_relay_instance_id():
    inst = client.get("/health").json()["instance_id"]
    assert inst and len(inst) >= 8
    assert inst in status_text()


# ── Pairing exactly as the Android app does it ───────────────────────────────

def admin_code():
    res = client.post("/api/pair/generate", headers={"Authorization": "Bearer test_admin_token_12345"}, json={})
    assert res.status_code == 200
    return res.json()["code"]


def app_pair(code, device_id="dev_app0001", current=None):
    body = {"code": f" {code.lower()} ", "device_id": device_id, "device_name": "Pixel"}
    if current:
        body["current_device_token"] = current
    return client.post("/api/pair/verify", json=body)


def test_token_issued_by_pairing_authenticates_and_the_session_becomes_visible_to_john():
    # The app trims the code; the relay uppercases it (app_pair sends it padded and lowercase).
    res = app_pair(admin_code())
    assert res.status_code == 200
    body = res.json()
    assert body["ok"] is True and body["device_token"].startswith("tok_")

    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth",
                                 "device_id": "dev_app0001", "device_token": body["device_token"]}))
        assert ws.receive_json()["type"] == "auth_ok"
        assert ws.receive_json()["active"] is False
        ws.send_text(json.dumps(start_msg()))
        assert ws.receive_json()["type"] == "session_started_ack"
        assert "Session active trouvée" in status_text()


def test_app_style_repair_needs_the_current_token_and_a_locally_invented_token_never_works():
    first = app_pair(admin_code()).json()["device_token"]
    # Same device, new code, no current token: refused (403), as the app explains.
    assert app_pair(admin_code()).status_code == 403
    # With the current token it is accepted and the old token stops working.
    second = app_pair(admin_code(), current=first)
    assert second.status_code == 200
    new_token = second.json()["device_token"]
    assert new_token != first

    for token, expected in ((first, "auth_error"), ("tok_invented_BBBB2222", "auth_error"), (new_token, "auth_ok")):
        with client.websocket_connect("/ws/device") as ws:
            ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth",
                                     "device_id": "dev_app0001", "device_token": token}))
            assert ws.receive_json()["type"] == expected

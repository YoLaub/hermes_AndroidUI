"""Per-profile control policy: `john` keeps its strict, phone-started sessions; `phone-agent` is a standing profile
that only a relay-paired agent phone may use (never claimed by an ordinary phone)."""
import json
import os
import time

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

from database import init_db, register_device
from profile_policy import policy_for
from server import ActiveSession, app, manager

client = TestClient(app)
DEVICE_ID = "dev_policy01"
DEVICE_TOKEN = "tok_device_secret_POLICY1"
AUTH = {"protocol": "mobile-control/1", "type": "auth", "device_id": DEVICE_ID, "device_token": DEVICE_TOKEN}


@pytest.fixture(autouse=True)
def fresh_state():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    register_device(DEVICE_ID, DEVICE_TOKEN, "Ordinary phone")
    manager.active_connections.clear()
    manager.active_sessions.clear()


def test_john_policy_is_strict_and_phone_started():
    p = policy_for("john")
    assert p is not None and p.standing is False


def test_phone_agent_policy_is_standing():
    p = policy_for("phone-agent")
    assert p is not None and p.standing is True


def test_policy_lookup_normalizes_case_and_spaces_and_rejects_unknown_profiles():
    assert policy_for("  JOHN ") is policy_for("john")
    assert policy_for("mario") is None
    assert policy_for("") is None


def test_active_session_accepts_phone_agent_and_still_rejects_other_profiles():
    ActiveSession("s1", "d1", "*", "phone-agent", "interaction", time.time() + 60)
    ActiveSession("s2", "d1", "com.x", "john", "interaction", time.time() + 60)
    with pytest.raises(ValueError):
        ActiveSession("s3", "d1", "com.x", "mario", "interaction", time.time() + 60)


def test_manager_registers_phone_agent_and_john_sessions():
    assert manager.set_active_session(ActiveSession("s1", "d1", "*", "phone-agent", "interaction", time.time() + 60))
    assert manager.set_active_session(ActiveSession("s2", "d2", "com.x", "john", "interaction", time.time() + 60))


def test_an_ordinary_phone_cannot_claim_the_phone_agent_profile():
    """The standing profile belongs to a paired agent phone, never to a phone that merely asks for it."""
    with client.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps(AUTH))
        ws.receive_json(); ws.receive_json()
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_x",
                                 "target_package": "*", "allowed_profile": "phone-agent",
                                 "mode": "interaction", "duration_seconds": 900}))
        err = ws.receive_json()
        assert err["type"] == "session_error" and err["error_code"] == "PROFILE_NOT_ALLOWED"
    assert manager.active_sessions == {}

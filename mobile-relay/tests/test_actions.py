"""
WP16d, relay side: sending an SMS and placing a call, each with its own consent, interaction mode only,
validated arguments, a long wait for the user's confirmation, and recipient and text never logged or stored.
"""
import json
import logging
import os
import signal
import threading
import time
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

from database import get_db, init_db, register_device
from server import app, manager

client = None  # one TestClient (one event loop) shared by HTTP and WebSocket, like production


@pytest.fixture(scope="module", autouse=True)
def _shared_client():
    global client
    with TestClient(app) as c:
        client = c
        yield


DEVICE_ID = "dev_cal00001"
DEVICE_TOKEN = "tok_device_secret_CAL1"
AUTH = {"protocol": "mobile-control/1", "type": "auth", "device_id": DEVICE_ID, "device_token": DEVICE_TOKEN}
JOHN = {"Authorization": "Bearer token_for_john"}

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


def mcp_raw(tool, arguments=None):
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 7, "method": "tools/call",
                                                   "params": {"name": tool, "arguments": arguments or {}}})
    assert res.status_code == 200, res.text
    return res.json()["result"]


def call_via_phone(ws, tool, arguments, phone_reply):
    box = {}
    t = threading.Thread(target=lambda: box.update(out=mcp_raw(tool, arguments)))
    t.start()
    cmd = ws.receive_json()
    assert cmd["type"] == "command"
    ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "result",
                             "command_id": cmd["command_id"], **phone_reply}))
    t.join(10)
    return cmd, box["out"]



SECRET_TEXT = "Dis à Marie que le virement est parti SECRET-SEND-88"
SECRET_WHO = "Marie Dupont"
NOW_MS = 1_700_000_000_000


def start_msg(allow=None, mode="interaction"):
    msg = {"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_act1",
           "target_package": "com.linkedin.android", "allowed_profile": "john", "mode": mode,
           "duration_seconds": 900}
    if allow is not None:
        msg["allow"] = allow
    return msg


def connect_and_start(ws, allow=None, mode="interaction"):
    ws.send_text(json.dumps(AUTH))
    assert ws.receive_json()["type"] == "auth_ok"
    ws.receive_json()
    ws.send_text(json.dumps(start_msg(allow, mode)))
    ack = ws.receive_json()
    assert ack["type"] == "session_started_ack"
    return ack


def ok(message="fait"):
    return {"status": "success", "message": message}


def test_each_action_has_its_own_consent_and_reads_do_not_open_them():
    reads = ["sms_read", "call_log_read", "calendar", "screenshots"]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=reads)
        with patch.object(type(manager), "send_command_to_device") as sent:
            sms = mcp_raw("mobile_sms_send", {"to": SECRET_WHO, "text": "salut"})
            call = mcp_raw("mobile_call_place", {"to": SECRET_WHO})
        assert not sent.called
    assert sms["isError"] and sms["content"][0]["text"].startswith("SMS_SEND_NOT_ALLOWED")
    assert call["isError"] and call["content"][0]["text"].startswith("CALL_NOT_ALLOWED")
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            call = mcp_raw("mobile_call_place", {"to": SECRET_WHO})
        assert not sent.called
    assert call["content"][0]["text"].startswith("CALL_NOT_ALLOWED")


def test_actions_are_refused_in_observation_mode_even_with_consent():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send", "call_place"], mode="observation")
        with patch.object(type(manager), "send_command_to_device") as sent:
            sms = mcp_raw("mobile_sms_send", {"to": SECRET_WHO, "text": "salut"})
            call = mcp_raw("mobile_call_place", {"to": SECRET_WHO})
        assert not sent.called
    assert sms["content"][0]["text"].startswith("MODE_DENIED")
    assert call["content"][0]["text"].startswith("MODE_DENIED")


def test_arguments_are_validated_before_anything_reaches_the_phone():
    bad_sms = [{}, {"to": "x"}, {"text": "x"}, {"to": "", "text": "x"}, {"to": "x", "text": "  "},
               {"to": "x", "text": "T" * 301}, {"to": "x" * 101, "text": "x"}, {"to": 5, "text": "x"},
               {"to": "x", "text": ["x"]}]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send", "call_place"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            for args in bad_sms:
                r = mcp_raw("mobile_sms_send", args)
                assert r["isError"] and r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), args
            for args in ({}, {"to": ""}, {"to": 7}, {"to": "x" * 101}):
                r = mcp_raw("mobile_call_place", args)
                assert r["isError"] and r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), args
        assert not sent.called


def test_a_valid_send_reaches_the_phone_with_its_arguments_and_time_to_confirm():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send"])
        cmd, result = call_via_phone(ws, "mobile_sms_send", {"to": SECRET_WHO, "text": SECRET_TEXT}, ok())
    assert cmd["operation"] == "sms_send"
    assert cmd["arguments"]["to"] == SECRET_WHO and cmd["arguments"]["text"] == SECRET_TEXT
    # The user has 60 seconds on the phone: the command must outlive that.
    assert cmd["expires_at"] / 1000 - time.time() > 70
    assert not result.get("isError")


def test_a_valid_call_reaches_the_phone_without_any_text():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["call_place"])
        cmd, result = call_via_phone(ws, "mobile_call_place", {"to": SECRET_WHO, "text": "ignoré"}, ok())
    assert cmd["operation"] == "call_place" and cmd["arguments"]["to"] == SECRET_WHO
    assert not cmd["arguments"].get("text")
    assert not result.get("isError")


def test_the_success_message_is_the_relays_own_and_never_echoes_the_phone_text():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send"])
        _, result = call_via_phone(ws, "mobile_sms_send", {"to": SECRET_WHO, "text": SECRET_TEXT},
                                   ok(message=f"SMS envoyé à {SECRET_WHO}: {SECRET_TEXT}"))
    text = result["content"][0]["text"]
    assert SECRET_WHO not in text and "SECRET-SEND-88" not in text
    assert "confirm" in text.lower()


def test_the_users_refusal_the_timeout_and_the_contacts_rule_pass_through():
    for code in ("USER_REFUSED", "CONFIRMATION_TIMEOUT", "RECIPIENT_NOT_IN_CONTACTS", "RECIPIENT_AMBIGUOUS",
                 "CONFIRMATION_BUSY"):
        manager.active_sessions.clear()
        with client.websocket_connect("/ws/device") as ws:
            connect_and_start(ws, allow=["sms_send"])
            _, result = call_via_phone(ws, "mobile_sms_send", {"to": "x", "text": "y"},
                                       {"status": "rejected", "error_code": code, "message": "non"})
        assert result["isError"] is True and result["content"][0]["text"].startswith(code), code


def test_recipient_and_text_never_reach_the_logs_or_the_database(caplog):
    caplog.set_level(logging.DEBUG)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send", "call_place"])
        call_via_phone(ws, "mobile_sms_send", {"to": SECRET_WHO, "text": SECRET_TEXT}, ok())
        call_via_phone(ws, "mobile_call_place", {"to": SECRET_WHO}, ok())
    logs = "\n".join(r.getMessage() for r in caplog.records)
    assert "SECRET-SEND-88" not in logs and SECRET_WHO not in logs
    conn = get_db()
    dump = []
    for table in [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]:
        dump.extend(str(tuple(row)) for row in conn.execute(f"SELECT * FROM {table}"))
    audit = [dict(r) for r in conn.execute("SELECT * FROM audit_logs WHERE operation IN ('SMS_SEND','CALL_PLACE')")]
    conn.close()
    joined = "\n".join(dump)
    assert "SECRET-SEND-88" not in joined and SECRET_WHO not in joined
    assert len(audit) == 2


def test_the_status_reports_both_action_consents():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_send"])
        text = mcp_raw("mobile_control_status")["content"][0]["text"]
    assert "Envoi de SMS : autorisé" in text and "Appels : non autorisés" in text

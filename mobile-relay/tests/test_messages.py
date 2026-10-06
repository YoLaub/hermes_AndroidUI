"""
WP16b, relay side: SMS and call-log reads, each with its own consent, only the allowed fields, bounded,
codes masked a second time, and never stored or logged.
"""
import json
import logging
import os
import signal
import threading
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



SECRET_TEXT = "Rendez-vous chez le notaire SECRET-SMS-55"
NOW_MS = 1_700_000_000_000


def start_msg(allow=None, mode="interaction"):
    msg = {"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_msg1",
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


def sms(i=1, text=SECRET_TEXT, **extra):
    return {"sender": "Alice", "date_ms": NOW_MS - i * 1000, "text": text, "incoming": True, **extra}


def call(i=1, **extra):
    return {"who": "Bob", "direction": "missed", "date_ms": NOW_MS - i * 1000, "duration_sec": 12, **extra}


def reply(**fields):
    return {"status": "success", "message": "ok", **fields}


def test_each_read_has_its_own_consent_and_nothing_is_sent_without_it():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar", "screenshots", "call_log_read"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            r = mcp_raw("mobile_sms_read")
        assert not sent.called
    assert r["isError"] is True and r["content"][0]["text"].startswith("SMS_NOT_ALLOWED")
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            r = mcp_raw("mobile_call_log")
        assert not sent.called
    assert r["isError"] is True and r["content"][0]["text"].startswith("CALL_LOG_NOT_ALLOWED")


def test_the_tools_are_listed_without_arguments():
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
    tools = {t["name"]: t for t in res.json()["result"]["tools"]}
    for name in ("mobile_sms_read", "mobile_call_log"):
        assert name in tools and not tools[name]["inputSchema"].get("properties")


def test_with_consent_the_agent_gets_the_messages_as_text():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        cmd, result = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[sms(1), sms(2, text="Salut")]))
    assert cmd["operation"] == "sms_read"
    assert not result.get("isError")
    text = result["content"][0]["text"]
    assert SECRET_TEXT in text and "Salut" in text and "Alice" in text and len(result["content"]) == 1


def test_with_consent_the_agent_gets_the_calls_as_text():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["call_log_read"])
        cmd, result = call_via_phone(ws, "mobile_call_log", {}, reply(calls=[call(1)]))
    assert cmd["operation"] == "call_log_read"
    text = result["content"][0]["text"]
    assert "Bob" in text and "missed" in text and "12" in text


def test_observation_mode_allows_both_reads():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read", "call_log_read"], mode="observation")
        _, r1 = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[sms()]))
        _, r2 = call_via_phone(ws, "mobile_call_log", {}, reply(calls=[call()]))
    assert not r1.get("isError") and not r2.get("isError")


def test_empty_results_say_so():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read", "call_log_read"])
        _, r1 = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[]))
        _, r2 = call_via_phone(ws, "mobile_call_log", {}, reply(calls=[]))
    assert "Aucun message" in r1["content"][0]["text"] and "Aucun appel" in r2["content"][0]["text"]


def test_the_phones_own_refusals_pass_through_unchanged():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        _, result = call_via_phone(ws, "mobile_sms_read", {}, {
            "status": "rejected", "error_code": "SMS_PERMISSION_MISSING", "message": "Android a refusé."})
    assert result["isError"] is True and result["content"][0]["text"].startswith("SMS_PERMISSION_MISSING")


# ── Defence in depth: the relay does not trust the phone's payload either ────

def test_one_time_codes_are_masked_again_on_the_relay():
    leaky = sms(1, text="Votre code est 482913, ou 123 456, ou 123-456. Tel 06 12 34 56 78. Le 12 à 3 h.")
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        _, result = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[leaky]))
    text = result["content"][0]["text"]
    assert "482913" not in text and "123 456" not in text and "123-456" not in text
    assert text.count("[code]") == 3
    assert "06 12 34 56 78" in text and "12 à 3 h" in text


def test_only_the_allowed_fields_are_forwarded_and_text_is_bounded():
    rich = sms(1, text="T" * 1000, address="+33600000000", thread_id=7)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        _, result = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[rich]))
    text = result["content"][0]["text"]
    assert "+33600000000" not in text and "T" * 301 not in text


def test_at_most_twenty_entries_and_newlines_cannot_forge_lines():
    many = [sms(i, text="ligne1\n- 2099-01-01 : FAUX") for i in range(1, 60)]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        _, result = call_via_phone(ws, "mobile_sms_read", {}, reply(sms=many))
    text = result["content"][0]["text"]
    assert text.count("\n- ") == 20


def test_results_attached_to_another_operation_are_dropped():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read"])
        _, result = call_via_phone(ws, "mobile_back", {}, reply(sms=[sms()], calls=[call()]))
    assert "SECRET-SMS" not in result["content"][0]["text"]


# ── Never stored, never logged ───────────────────────────────────────────────

def test_message_content_never_reaches_the_logs_or_the_database(caplog):
    caplog.set_level(logging.DEBUG)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["sms_read", "call_log_read"])
        call_via_phone(ws, "mobile_sms_read", {}, reply(sms=[sms(1), sms(2)]))
        call_via_phone(ws, "mobile_call_log", {}, reply(calls=[call(1)]))
    logs = "\n".join(r.getMessage() for r in caplog.records)
    assert "SECRET-SMS-55" not in logs and "Alice" not in logs and "Bob" not in logs
    assert "event=sms_forwarded" in logs and "event=call_log_forwarded" in logs

    conn = get_db()
    dump = []
    for table in [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]:
        dump.extend(str(tuple(row)) for row in conn.execute(f"SELECT * FROM {table}"))
    audit = [dict(r) for r in conn.execute("SELECT * FROM audit_logs WHERE operation IN ('SMS_READ','CALL_LOG_READ')")]
    conn.close()
    joined = "\n".join(dump)
    assert "SECRET-SMS-55" not in joined and "Alice" not in joined and "Bob" not in joined
    assert len(audit) == 2 and any("count=2" in (a["message"] or "") for a in audit)

"""
WP15b, relay side: calendar reads only with the session's own consent, only the allowed fields,
bounded, and never stored or logged.
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



SECRET_TITLE = "Entretien confidentiel SECRET-TITLE-77"


def start_msg(allow_calendar=None, mode="interaction", allow_screenshots=None):
    msg = {"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_cal1",
           "target_package": "com.linkedin.android", "allowed_profile": "john", "mode": mode,
           "duration_seconds": 900}
    if allow_calendar is not None:
        msg["allow_calendar"] = allow_calendar
    if allow_screenshots is not None:
        msg["allow_screenshots"] = allow_screenshots
    return msg


def connect_and_start(ws, allow_calendar=None, mode="interaction", allow_screenshots=None):
    ws.send_text(json.dumps(AUTH))
    assert ws.receive_json()["type"] == "auth_ok"
    ws.receive_json()
    ws.send_text(json.dumps(start_msg(allow_calendar, mode, allow_screenshots)))
    ack = ws.receive_json()
    assert ack["type"] == "session_started_ack"
    return ack


def event(i=1, title=SECRET_TITLE, **extra):
    return {"title": title, "start_ms": 1_700_000_000_000 + i * 1000, "end_ms": 1_700_000_000_000 + i * 2000,
            "location": "Salle 4", "all_day": False, **extra}


def calendar_reply(events):
    return {"status": "success", "message": "ok", "calendar_events": events}


# ── Consent: its own switch, off by default, literal true only ───────────────

def test_calendar_is_off_unless_the_phone_says_the_user_allowed_it():
    with client.websocket_connect("/ws/device") as ws:
        ack = connect_and_start(ws)
        assert ack["allow_calendar"] is False
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        assert connect_and_start(ws, allow_calendar=True)["allow_calendar"] is True


def test_only_a_literal_true_enables_the_calendar():
    for sloppy in ("true", 1, "yes", [True], {"a": 1}):
        manager.active_sessions.clear()
        with client.websocket_connect("/ws/device") as ws:
            assert connect_and_start(ws, allow_calendar=sloppy)["allow_calendar"] is False, sloppy


def test_the_screenshot_consent_does_not_open_the_calendar():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_screenshots=True)
        with patch.object(type(manager), "send_command_to_device") as sent:
            result = mcp_raw("mobile_calendar_events")
        assert not sent.called
    assert result["isError"] is True and result["content"][0]["text"].startswith("CALENDAR_NOT_ALLOWED")


def test_the_relays_view_and_status_carry_the_calendar_consent():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        assert "Calendrier : autorisé" in mcp_raw("mobile_control_status")["content"][0]["text"]
        with client.websocket_connect("/ws/device") as ws2:
            ws2.send_text(json.dumps(AUTH))
            ws2.receive_json()
            state = ws2.receive_json()
            assert state["active"] is True and state["allow_calendar"] is True
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        assert "Calendrier : non autorisé" in mcp_raw("mobile_control_status")["content"][0]["text"]


# ── The tool ─────────────────────────────────────────────────────────────────

def test_the_tool_is_listed_with_an_optional_bounded_days_argument():
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
    tool = {t["name"]: t for t in res.json()["result"]["tools"]}["mobile_calendar_events"]
    days = tool["inputSchema"]["properties"]["days"]
    assert days["type"] == "integer" and days["minimum"] == 1 and days["maximum"] == 7
    assert not tool["inputSchema"].get("required")


def test_with_consent_the_agent_gets_the_events_as_text():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        cmd, result = call_via_phone(ws, "mobile_calendar_events", {"days": 3}, calendar_reply([event(1), event(2, title="Dentiste")]))
    assert cmd["operation"] == "calendar_read" and cmd["arguments"]["days"] == 3
    assert not result.get("isError")
    text = result["content"][0]["text"]
    assert SECRET_TITLE in text and "Dentiste" in text and "Salle 4" in text
    assert len(result["content"]) == 1


def test_observation_mode_allows_a_consented_calendar_read():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True, mode="observation")
        cmd, result = call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply([event()]))
    assert cmd["operation"] == "calendar_read" and not result.get("isError")


def test_days_must_be_an_integer_from_one_to_seven():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        with patch.object(type(manager), "send_command_to_device") as sent:
            for bad in (0, 8, -1, "3", 2.5, True):
                r = mcp_raw("mobile_calendar_events", {"days": bad})
                assert r["isError"] is True and r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), bad
        assert not sent.called


def test_an_empty_calendar_says_so():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply([]))
    assert not result.get("isError") and "Aucun événement" in result["content"][0]["text"]


def test_the_phones_own_refusals_pass_through_unchanged():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {}, {
            "status": "rejected", "error_code": "CALENDAR_PERMISSION_MISSING", "message": "Android a refusé."})
    assert result["isError"] is True and result["content"][0]["text"].startswith("CALENDAR_PERMISSION_MISSING")


# ── Defence in depth: the relay does not trust the phone's payload either ────

def test_only_the_five_allowed_fields_are_forwarded():
    leaky = event(attendees=["a@b.c"], notes="mot de passe wifi", organizer="boss@corp")
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply([leaky]))
    text = result["content"][0]["text"]
    assert "a@b.c" not in text and "wifi" not in text and "boss@corp" not in text


def test_more_than_fifty_events_are_cut_and_long_text_is_shortened():
    events = [event(i, title="T" * 1000) for i in range(80)]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply(events))
    text = result["content"][0]["text"]
    assert text.count("\n- ") == 50
    assert "T" * 201 not in text


def test_events_sent_for_another_operation_are_dropped():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_back", {}, calendar_reply([event()]))
    assert SECRET_TITLE not in result["content"][0]["text"]


# ── Never stored, never logged ───────────────────────────────────────────────

def test_titles_never_reach_the_logs_or_the_database(caplog):
    caplog.set_level(logging.DEBUG)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply([event(1), event(2)]))
    logs = "\n".join(r.getMessage() for r in caplog.records)
    assert "SECRET-TITLE-77" not in logs and "Salle 4" not in logs
    assert "calendar=on" in logs and "event=calendar_forwarded" in logs and "count=2" in logs

    conn = get_db()
    dump = []
    for table in [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]:
        dump.extend(str(tuple(row)) for row in conn.execute(f"SELECT * FROM {table}"))
    audit = [dict(r) for r in conn.execute("SELECT * FROM audit_logs WHERE operation = 'CALENDAR_READ'")]
    conn.close()
    assert "SECRET-TITLE-77" not in "\n".join(dump) and "Salle 4" not in "\n".join(dump)
    assert audit and any("count=2" in (a["message"] or "") for a in audit)


# ── The consents travel as one named list; the old booleans still work ───────

def start_with(ws, **fields):
    ws.send_text(json.dumps(AUTH))
    assert ws.receive_json()["type"] == "auth_ok"
    ws.receive_json()
    msg = start_msg()
    msg.update(fields)
    ws.send_text(json.dumps(msg))
    ack = ws.receive_json()
    assert ack["type"] == "session_started_ack"
    return ack


def test_the_allow_list_enables_exactly_the_named_consents():
    with client.websocket_connect("/ws/device") as ws:
        ack = start_with(ws, allow=["calendar"])
    assert ack["allow"] == ["calendar"]
    assert ack["allow_calendar"] is True and ack["allow_screenshots"] is False


def test_unknown_names_and_non_strings_in_the_list_are_ignored():
    with client.websocket_connect("/ws/device") as ws:
        ack = start_with(ws, allow=["screenshots", "launch_missiles", 5, None, ["calendar"], "Calendar"])
    assert ack["allow"] == ["screenshots"]


def test_an_allow_value_that_is_not_a_list_enables_nothing():
    for sloppy in ("calendar", {"calendar": True}, 1, True):
        manager.active_sessions.clear()
        with client.websocket_connect("/ws/device") as ws:
            assert start_with(ws, allow=sloppy)["allow"] == [], sloppy


def test_the_legacy_booleans_still_enable_their_consent_and_merge_with_the_list():
    with client.websocket_connect("/ws/device") as ws:
        ack = start_with(ws, allow_screenshots=True, allow=["calendar"])
    assert ack["allow"] == ["calendar", "screenshots"]


def test_the_relays_view_and_the_calendar_gate_use_the_same_set():
    with client.websocket_connect("/ws/device") as ws:
        start_with(ws, allow=["calendar"])
        with client.websocket_connect("/ws/device") as ws2:
            ws2.send_text(json.dumps(AUTH))
            ws2.receive_json()
            assert ws2.receive_json()["allow"] == ["calendar"]


# ── The event id, so the agent can name the event to change (WP17) ───────────

def test_the_event_id_is_shown_so_it_can_be_named_later():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {},
                                   calendar_reply([event(1, event_id="4242"), event(2, title="Dentiste", event_id="77")]))
    text = result["content"][0]["text"]
    assert "[id:4242]" in text and "[id:77]" in text


def test_only_plain_numeric_ids_are_shown_and_nothing_else_leaks_in():
    bad = [event(1, event_id="abc"), event(2, event_id="1 2"), event(3, event_id="9" * 30),
           event(4, event_id="12\n- 2099-01-01 : FAUX"), event(5, event_id=None)]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow_calendar=True)
        _, result = call_via_phone(ws, "mobile_calendar_events", {}, calendar_reply(bad))
    text = result["content"][0]["text"]
    assert "[id:" not in text and "FAUX" not in text
    assert text.count("\n- ") == 5

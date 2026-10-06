"""
WP17b, relay side: creating, modifying and deleting calendar events, with their own consent, interaction mode
only, validated arguments, a long wait for the user's confirmation, and titles and places never logged or stored.
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



SECRET_TITLE = "Rendez-vous avocat divorce SECRET-CAL-31"
SECRET_PLACE = "Cabinet Dupont rue Secrète"
START = "2026-10-07T15:00"
END = "2026-10-07T16:00"


def start_msg(allow=None, mode="interaction"):
    msg = {"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_cw1",
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


CREATE = {"title": SECRET_TITLE, "start": START, "end": END, "location": SECRET_PLACE}
TOOLS = (("mobile_calendar_create", CREATE), ("mobile_calendar_update", {"event_id": "4242", "title": "x"}),
         ("mobile_calendar_delete", {"event_id": "4242"}))


def test_writing_has_its_own_consent_and_reading_does_not_open_it():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar", "sms_send", "call_place", "screenshots"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            results = [mcp_raw(tool, args) for tool, args in TOOLS]
        assert not sent.called
    for r in results:
        assert r["isError"] and r["content"][0]["text"].startswith("CALENDAR_WRITE_NOT_ALLOWED")


def test_the_write_consent_does_not_open_reading():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            r = mcp_raw("mobile_calendar_events")
        assert not sent.called
    assert r["content"][0]["text"].startswith("CALENDAR_NOT_ALLOWED")


def test_writes_are_refused_in_observation_mode_even_with_consent():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"], mode="observation")
        with patch.object(type(manager), "send_command_to_device") as sent:
            results = [mcp_raw(tool, args) for tool, args in TOOLS]
        assert not sent.called
    for r in results:
        assert r["content"][0]["text"].startswith("MODE_DENIED")


def test_the_tools_are_listed_with_their_required_arguments():
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
    tools = {t["name"]: t["inputSchema"] for t in res.json()["result"]["tools"]}
    assert set(tools["mobile_calendar_create"]["required"]) == {"title", "start", "end"}
    assert tools["mobile_calendar_update"]["required"] == ["event_id"]
    assert tools["mobile_calendar_delete"]["required"] == ["event_id"]


def test_arguments_are_validated_before_anything_reaches_the_phone():
    bad_create = [{}, {"title": "x"}, {**CREATE, "title": ""}, {**CREATE, "title": "  "}, {**CREATE, "title": "T" * 201},
                  {**CREATE, "location": "L" * 201}, {**CREATE, "start": "demain 15h"}, {**CREATE, "end": "2026-10-07 16:00"},
                  {**CREATE, "start": "2026-10-07T15:00zzz"}, {**CREATE, "title": 5}, {**CREATE, "location": ["x"]}]
    bad_update = [{}, {"event_id": "4242"}, {"title": "x"}, {"event_id": "abc", "title": "x"},
                  {"event_id": "1 2", "title": "x"}, {"event_id": "9" * 21, "title": "x"},
                  {"event_id": "4242", "title": ""}, {"event_id": "4242", "start": "nope"},
                  {"event_id": "4242", "location": "L" * 201}]
    bad_delete = [{}, {"event_id": ""}, {"event_id": "abc"}, {"event_id": 42}, {"event_id": "12\n34"}]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            for tool, cases in (("mobile_calendar_create", bad_create), ("mobile_calendar_update", bad_update),
                                ("mobile_calendar_delete", bad_delete)):
                for args in cases:
                    r = mcp_raw(tool, args)
                    assert r["isError"] and r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), (tool, args)
        assert not sent.called


def test_a_valid_create_reaches_the_phone_with_its_fields_and_time_to_confirm():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        cmd, result = call_via_phone(ws, "mobile_calendar_create", CREATE, ok())
    assert cmd["operation"] == "calendar_create"
    a = cmd["arguments"]
    assert (a["title"], a["start"], a["end"], a["location"]) == (SECRET_TITLE, START, END, SECRET_PLACE)
    assert cmd["expires_at"] / 1000 - time.time() > 70
    assert not result.get("isError")


def test_an_update_sends_only_what_the_agent_asked_to_change():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        cmd, _ = call_via_phone(ws, "mobile_calendar_update", {"event_id": "4242", "title": "Nouveau"}, ok())
    a = cmd["arguments"]
    assert cmd["operation"] == "calendar_update" and a["event_id"] == "4242" and a["title"] == "Nouveau"
    assert not a.get("start") and not a.get("end") and a.get("location") is None


def test_an_update_can_clear_the_location_with_an_empty_string():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        cmd, _ = call_via_phone(ws, "mobile_calendar_update", {"event_id": "4242", "location": ""}, ok())
    assert cmd["arguments"]["location"] == ""


def test_a_delete_sends_only_the_id():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        cmd, _ = call_via_phone(ws, "mobile_calendar_delete", {"event_id": "4242", "title": "ignoré"}, ok())
    a = cmd["arguments"]
    assert cmd["operation"] == "calendar_delete" and a["event_id"] == "4242" and not a.get("title")


def test_the_success_answer_is_the_relays_own_and_never_echoes_the_phone():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        _, result = call_via_phone(ws, "mobile_calendar_create", CREATE, ok(f"créé : {SECRET_TITLE} @ {SECRET_PLACE}"))
    text = result["content"][0]["text"]
    assert "SECRET-CAL-31" not in text and "Secrète" not in text and "confirm" in text.lower()


def test_the_phones_refusals_pass_through_unchanged():
    for code in ("USER_REFUSED", "CONFIRMATION_TIMEOUT", "CONFIRMATION_BUSY", "EVENT_NOT_EDITABLE", "EVENT_NOT_FOUND",
                 "NO_WRITABLE_CALENDAR", "CALENDAR_PERMISSION_MISSING"):
        manager.active_sessions.clear()
        with client.websocket_connect("/ws/device") as ws:
            connect_and_start(ws, allow=["calendar_write"])
            _, result = call_via_phone(ws, "mobile_calendar_delete", {"event_id": "1"},
                                       {"status": "rejected", "error_code": code, "message": "non"})
        assert result["isError"] is True and result["content"][0]["text"].startswith(code), code


def test_titles_and_places_never_reach_the_logs_or_the_database(caplog):
    caplog.set_level(logging.DEBUG)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        call_via_phone(ws, "mobile_calendar_create", CREATE, ok())
        call_via_phone(ws, "mobile_calendar_update", {"event_id": "7", "title": SECRET_TITLE}, ok())
        call_via_phone(ws, "mobile_calendar_delete", {"event_id": "7"}, ok())
    logs = "\n".join(r.getMessage() for r in caplog.records)
    assert "SECRET-CAL-31" not in logs and "Secrète" not in logs
    conn = get_db()
    dump = []
    for table in [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]:
        dump.extend(str(tuple(row)) for row in conn.execute(f"SELECT * FROM {table}"))
    audit = [dict(r) for r in conn.execute(
        "SELECT * FROM audit_logs WHERE operation IN ('CALENDAR_CREATE','CALENDAR_UPDATE','CALENDAR_DELETE')")]
    conn.close()
    joined = "\n".join(dump)
    assert "SECRET-CAL-31" not in joined and "Secrète" not in joined
    assert len(audit) == 3


def test_the_status_reports_the_write_consent():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        on = mcp_raw("mobile_control_status")["content"][0]["text"]
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=[])
        off = mcp_raw("mobile_control_status")["content"][0]["text"]
    assert "Écriture du calendrier : autorisée" in on and "Écriture du calendrier : non autorisée" in off


def test_titles_and_places_with_line_breaks_or_control_characters_are_refused():
    bad = ["a\nb", "a\rb", "a\tb", "a\x00b", "a\x7fb", "a\u2028b", "a\u2029b"]
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        with patch.object(type(manager), "send_command_to_device") as sent:
            for text in bad:
                for tool, args in (("mobile_calendar_create", {**CREATE, "title": text}),
                                   ("mobile_calendar_create", {**CREATE, "location": text}),
                                   ("mobile_calendar_update", {"event_id": "4242", "title": text}),
                                   ("mobile_calendar_update", {"event_id": "4242", "location": text})):
                    r = mcp_raw(tool, args)
                    assert r["isError"] and r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), (tool, text)
        assert not sent.called


def test_accents_and_ordinary_punctuation_are_still_accepted():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=["calendar_write"])
        cmd, result = call_via_phone(ws, "mobile_calendar_create",
                                     {**CREATE, "title": "Rendez-vous chez Zoë — 15 h (bureau) #2",
                                      "location": "12, rue de l'Église"}, ok())
    assert cmd["operation"] == "calendar_create" and not result.get("isError")

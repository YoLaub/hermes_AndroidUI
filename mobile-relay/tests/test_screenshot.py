"""
WP14b, relay side: screenshots only with the session's consent, never stored or logged,
tap by coordinates only in interaction mode, and size/format limits on what the phone sends.
"""
import base64
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


DEVICE_ID = "dev_shot0001"
DEVICE_TOKEN = "tok_device_secret_SHOT1"
AUTH = {"protocol": "mobile-control/1", "type": "auth", "device_id": DEVICE_ID, "device_token": DEVICE_TOKEN}
JOHN = {"Authorization": "Bearer token_for_john"}

# A recognisable payload: valid base64 of a JPEG-looking header, easy to search for in logs and the DB.
MARKER_BYTES = b"\xff\xd8\xff\xe0JFIF-SECRET-SCREEN-MARKER-0123456789" * 8
MARKER_B64 = base64.b64encode(MARKER_BYTES).decode()


def start_msg(allow=None, mode="interaction"):
    msg = {"protocol": "mobile-control/1", "type": "session_start", "session_id": "ses_shot1",
           "target_package": "com.linkedin.android", "allowed_profile": "john", "mode": mode,
           "duration_seconds": 900}
    if allow is not None:
        msg["allow_screenshots"] = allow
    return msg


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


def connect_and_start(ws, allow=None, mode="interaction"):
    ws.send_text(json.dumps(AUTH))
    assert ws.receive_json()["type"] == "auth_ok"
    ws.receive_json()
    ws.send_text(json.dumps(start_msg(allow, mode)))
    ack = ws.receive_json()
    assert ack["type"] == "session_started_ack"
    return ack


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


def shot_reply(data_b64=MARKER_B64, mime="image/jpeg", revision="rev_9"):
    return {"status": "success", "message": "ok", "data": {
        "screen_revision": revision, "package_name": "com.linkedin.android",
        "elements": [{"element_ref": "el_1", "class_name": "Button", "text": "Commenter", "clickable": True}],
        "screenshot": {"mime_type": mime, "width": 720, "height": 1600, "data": data_b64}}}


# ── Consent is explicit, per session, and off by default ─────────────────────

def test_screenshots_are_off_unless_the_phone_says_the_user_allowed_them():
    with client.websocket_connect("/ws/device") as ws:
        assert connect_and_start(ws)["allow_screenshots"] is False
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        assert connect_and_start(ws, allow=True)["allow_screenshots"] is True


def test_only_a_literal_true_enables_screenshots():
    for sloppy in ("true", 1, "yes", [True], {"a": 1}):
        manager.active_sessions.clear()
        with client.websocket_connect("/ws/device") as ws:
            assert connect_and_start(ws, allow=sloppy)["allow_screenshots"] is False, sloppy


def test_the_relays_view_of_the_session_carries_the_consent():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        with client.websocket_connect("/ws/device") as ws2:
            ws2.send_text(json.dumps(AUTH))
            ws2.receive_json()
            state = ws2.receive_json()
            assert state["active"] is True and state["allow_screenshots"] is True


def test_status_tells_the_agent_whether_captures_are_allowed():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        text = mcp_raw("mobile_control_status")["content"][0]["text"]
        assert "Captures d'écran : autorisées" in text
    manager.active_sessions.clear()
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)
        text = mcp_raw("mobile_control_status")["content"][0]["text"]
        assert "Captures d'écran : non autorisées" in text


# ── Tools ────────────────────────────────────────────────────────────────────

def test_the_two_tools_are_listed_with_their_schemas():
    res = client.post("/mcp", headers=JOHN, json={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
    tools = {t["name"]: t for t in res.json()["result"]["tools"]}
    assert "mobile_screenshot" in tools
    tap = tools["mobile_tap_xy"]["inputSchema"]
    assert set(tap["required"]) == {"x", "y", "screen_revision"}
    assert tap["properties"]["x"]["type"] == "integer"


def test_without_consent_nothing_is_even_sent_to_the_phone():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws)  # no consent
        with patch.object(type(manager), "send_command_to_device") as sent:
            result = mcp_raw("mobile_screenshot")
            tap = mcp_raw("mobile_tap_xy", {"x": 5, "y": 5, "screen_revision": "rev_1"})
        assert not sent.called
    for r in (result, tap):
        assert r["isError"] is True
        assert r["content"][0]["text"].startswith("SCREENSHOTS_NOT_ALLOWED")


def test_with_consent_the_agent_receives_text_and_the_image():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        cmd, result = call_via_phone(ws, "mobile_screenshot", {}, shot_reply())
    assert cmd["operation"] == "screenshot"
    assert not result.get("isError")
    kinds = [c["type"] for c in result["content"]]
    assert kinds == ["text", "image"]
    assert result["content"][1]["mimeType"] == "image/jpeg"
    assert result["content"][1]["data"] == MARKER_B64
    assert "rev_9" in result["content"][0]["text"] and "720x1600" in result["content"][0]["text"]
    assert MARKER_B64 not in result["content"][0]["text"]


def test_observation_mode_allows_a_consented_screenshot_but_not_a_tap():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True, mode="observation")
        _, shot = call_via_phone(ws, "mobile_screenshot", {}, shot_reply())
        assert not shot.get("isError")
        tap = mcp_raw("mobile_tap_xy", {"x": 10, "y": 20, "screen_revision": "rev_9"})
        assert tap["isError"] is True and tap["content"][0]["text"].startswith("MODE_DENIED")


def test_tap_by_coordinates_reaches_the_phone_with_its_arguments():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        cmd, result = call_via_phone(ws, "mobile_tap_xy", {"x": 120, "y": 340, "screen_revision": "rev_9"},
                                     {"status": "success", "message": "Geste envoyé"})
    assert cmd["operation"] == "tap_xy"
    assert cmd["arguments"]["x"] == 120 and cmd["arguments"]["y"] == 340
    assert cmd["screen_revision"] == "rev_9"
    assert not result.get("isError")


def test_tap_coordinates_must_be_integers():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        for bad in ({"x": "12", "y": 3}, {"x": 1.5, "y": 3}, {"x": True, "y": 3}, {"y": 3}, {"x": 1}):
            r = mcp_raw("mobile_tap_xy", {**bad, "screen_revision": "rev_9"})
            assert r["isError"] is True, bad
            assert r["content"][0]["text"].startswith("INVALID_ARGUMENTS"), bad


def test_the_phones_own_refusals_pass_through_unchanged():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        _, r = call_via_phone(ws, "mobile_screenshot", {}, {
            "status": "rejected", "error_code": "SCREENSHOT_BLOCKED_SECURE_WINDOW", "message": "Fenêtre protégée."})
    assert r["isError"] is True and r["content"][0]["text"].startswith("SCREENSHOT_BLOCKED_SECURE_WINDOW")


# ── Limits on what the phone sends ───────────────────────────────────────────

def test_an_oversized_image_is_refused_and_not_forwarded():
    huge = base64.b64encode(b"\x00" * 1_200_000).decode()  # > 1 MB of image bytes
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        _, r = call_via_phone(ws, "mobile_screenshot", {}, shot_reply(data_b64=huge))
    assert r["isError"] is True and r["content"][0]["text"].startswith("SCREENSHOT_TOO_LARGE")
    assert huge[:64] not in json.dumps(r)


def test_only_jpeg_with_valid_base64_is_accepted():
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        for kwargs in ({"mime": "image/png"}, {"mime": "text/html"}, {"data_b64": "not*base64*at*all"}, {"data_b64": ""}):
            _, r = call_via_phone(ws, "mobile_screenshot", {}, shot_reply(**kwargs))
            assert r["isError"] is True, kwargs
            assert r["content"][0]["text"].startswith("SCREENSHOT_INVALID"), kwargs


def test_a_phone_that_sends_an_image_nobody_asked_for_gets_it_dropped():
    # An observe answer carrying a screenshot must not forward the image to the agent.
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        _, r = call_via_phone(ws, "mobile_observe", {}, shot_reply())
    assert all(c["type"] == "text" for c in r["content"])
    assert MARKER_B64 not in json.dumps(r)


# ── Never stored, never logged ───────────────────────────────────────────────

def test_the_image_never_reaches_the_logs_or_the_database(caplog):
    caplog.set_level(logging.DEBUG)
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        call_via_phone(ws, "mobile_screenshot", {}, shot_reply())
        call_via_phone(ws, "mobile_screenshot", {}, shot_reply(mime="image/png"))   # rejected path too
    logs = "\n".join(r.getMessage() for r in caplog.records)
    assert MARKER_B64 not in logs and MARKER_B64[:40] not in logs

    conn = get_db()
    dump = []
    for table in [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]:
        dump.extend(str(tuple(row)) for row in conn.execute(f"SELECT * FROM {table}"))
    audit = [dict(r) for r in conn.execute("SELECT * FROM audit_logs WHERE operation = 'SCREENSHOT'")]
    conn.close()
    assert MARKER_B64[:40] not in "\n".join(dump)
    assert audit, "an audit row records that a screenshot was taken"
    assert any(f"bytes={len(MARKER_BYTES)}" in (a["message"] or "") for a in audit)


def test_consent_and_screenshot_events_are_logged_without_content(caplog):
    caplog.set_level(logging.INFO, logger="mobile-relay")
    with client.websocket_connect("/ws/device") as ws:
        connect_and_start(ws, allow=True)
        call_via_phone(ws, "mobile_screenshot", {}, shot_reply())
    logs = "\n".join(r.getMessage() for r in caplog.records if r.name == "mobile-relay")
    assert "screenshots=on" in logs
    assert "event=screenshot_forwarded" in logs and f"bytes={len(MARKER_BYTES)}" in logs

"""
Relay hardening: no wildcard CORS, throttled failed authentication, bounded audit log.
"""
import json
import os
import time

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

from database import (
    get_db,
    init_db,
    is_auth_blocked,
    log_audit,
    purge_old_records,
    record_auth_failure,
    register_device,
)
from server import app, manager

JOHN_OK = {"Authorization": "Bearer token_for_john"}
ADMIN_OK = {"Authorization": "Bearer test_admin_token_12345"}
MCP_BODY = {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}


def client_from(ip: str) -> TestClient:
    return TestClient(app, client=(ip, 50000))


@pytest.fixture(autouse=True)
def fresh_state(monkeypatch):
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    manager.active_connections.clear()
    manager.active_sessions.clear()
    for var in ("MOBILE_RELAY_AUTH_MAX_FAILURES", "MOBILE_RELAY_AUTH_WINDOW_SECONDS",
                "MOBILE_RELAY_AUDIT_RETENTION_DAYS", "MOBILE_RELAY_PURGE_INTERVAL_SECONDS"):
        monkeypatch.delenv(var, raising=False)


# ── CORS ─────────────────────────────────────────────────────────────────────

def test_a_cross_origin_browser_gets_no_cors_permission():
    c = client_from("203.0.113.1")
    pre = c.options("/mcp", headers={"Origin": "https://evil.example",
                                     "Access-Control-Request-Method": "POST"})
    assert "access-control-allow-origin" not in pre.headers
    real = c.post("/mcp", json=MCP_BODY, headers={**JOHN_OK, "Origin": "https://evil.example"})
    assert real.status_code == 200
    assert "access-control-allow-origin" not in real.headers
    assert "access-control-allow-credentials" not in real.headers


# ── Failure counting (database level, deterministic time) ────────────────────

def test_blocked_only_after_the_threshold_within_the_window():
    now = 1_000_000.0
    for i in range(9):
        record_auth_failure("1.1.1.1", now=now + i)
    assert not is_auth_blocked("1.1.1.1", max_failures=10, window_seconds=300, now=now + 10)
    record_auth_failure("1.1.1.1", now=now + 9)
    assert is_auth_blocked("1.1.1.1", max_failures=10, window_seconds=300, now=now + 10)


def test_failures_expire_with_the_window_and_sources_are_independent():
    now = 1_000_000.0
    for _ in range(10):
        record_auth_failure("1.1.1.1", now=now)
    assert is_auth_blocked("1.1.1.1", max_failures=10, window_seconds=300, now=now + 299)
    assert not is_auth_blocked("1.1.1.1", max_failures=10, window_seconds=300, now=now + 301)
    assert not is_auth_blocked("2.2.2.2", max_failures=10, window_seconds=300, now=now)


def test_a_limit_of_zero_disables_blocking():
    for _ in range(50):
        record_auth_failure("1.1.1.1")
    assert not is_auth_blocked("1.1.1.1", max_failures=0)


# ── MCP endpoint ─────────────────────────────────────────────────────────────

def test_mcp_throttles_a_source_that_keeps_sending_wrong_tokens(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "5")
    attacker = client_from("203.0.113.9")
    for _ in range(5):
        assert attacker.post("/mcp", json=MCP_BODY, headers={"Authorization": "Bearer wrong"}).status_code == 401
    blocked = attacker.post("/mcp", json=MCP_BODY, headers={"Authorization": "Bearer wrong"})
    assert blocked.status_code == 429
    assert int(blocked.headers["retry-after"]) > 0
    # While blocked even the right token is refused: a correct guess must not slip through.
    assert attacker.post("/mcp", json=MCP_BODY, headers=JOHN_OK).status_code == 429
    # Other sources are not affected.
    assert client_from("198.51.100.7").post("/mcp", json=MCP_BODY, headers=JOHN_OK).status_code == 200


def test_a_request_without_any_token_is_not_counted_as_a_guess(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "3")
    c = client_from("203.0.113.20")
    for _ in range(10):
        assert c.post("/mcp", json=MCP_BODY).status_code == 401
    assert c.post("/mcp", json=MCP_BODY, headers=JOHN_OK).status_code == 200


def test_successful_calls_never_count(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "3")
    c = client_from("203.0.113.30")
    for _ in range(20):
        assert c.post("/mcp", json=MCP_BODY, headers=JOHN_OK).status_code == 200


# ── Admin endpoint ───────────────────────────────────────────────────────────

def test_admin_endpoint_is_throttled_too(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "4")
    c = client_from("203.0.113.40")
    for _ in range(4):
        assert c.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer nope"}).status_code == 401
    assert c.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer nope"}).status_code == 429
    assert c.post("/api/pair/generate", json={}, headers=ADMIN_OK).status_code == 429


# ── Device WebSocket ─────────────────────────────────────────────────────────

def _bad_auth(ws):
    ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth",
                             "device_id": "dev_x", "device_token": "tok_wrong"}))
    return ws.receive_json()


def test_device_auth_is_throttled_and_says_so(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "3")
    c = client_from("203.0.113.50")
    for _ in range(3):
        with c.websocket_connect("/ws/device") as ws:
            assert _bad_auth(ws)["error_code"] == "DEVICE_AUTH_FAILED"
    with c.websocket_connect("/ws/device") as ws:
        assert _bad_auth(ws)["error_code"] == "AUTH_RATE_LIMITED"


def test_a_blocked_source_cannot_authenticate_even_with_a_valid_device_token(monkeypatch):
    monkeypatch.setenv("MOBILE_RELAY_AUTH_MAX_FAILURES", "2")
    register_device("dev_ok", "tok_ok", "phone")
    c = client_from("203.0.113.60")
    for _ in range(2):
        with c.websocket_connect("/ws/device") as ws:
            _bad_auth(ws)
    with c.websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth",
                                 "device_id": "dev_ok", "device_token": "tok_ok"}))
        assert ws.receive_json()["error_code"] == "AUTH_RATE_LIMITED"
    # Another source with the same valid token is fine.
    with client_from("198.51.100.8").websocket_connect("/ws/device") as ws:
        ws.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth",
                                 "device_id": "dev_ok", "device_token": "tok_ok"}))
        assert ws.receive_json()["type"] == "auth_ok"


def test_one_bad_connection_with_bad_headers_and_a_bad_message_counts_once():
    c = client_from("203.0.113.70")
    headers = {"X-Device-Id": "dev_x", "X-Device-Token": "tok_wrong"}
    with c.websocket_connect("/ws/device", headers=headers) as ws:
        assert _bad_auth(ws)["error_code"] == "DEVICE_AUTH_FAILED"
    conn = get_db()
    rows = conn.execute("SELECT COUNT(*) FROM auth_failures WHERE identifier = ?", ("203.0.113.70",)).fetchone()[0]
    conn.close()
    assert rows == 1, f"header + message of one connection counted {rows} times"


# ── Audit log retention ──────────────────────────────────────────────────────

def _count(table):
    conn = get_db()
    n = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    conn.close()
    return n


def test_purge_deletes_old_audit_rows_and_keeps_recent_ones():
    now = time.time()
    log_audit("recent", "dev", "john", "OBSERVE", "OK", "recent")
    conn = get_db()
    with conn:
        conn.execute("INSERT INTO audit_logs (id, timestamp, device_id, profile, operation, status, message) "
                     "VALUES ('old', ?, 'dev', 'john', 'OBSERVE', 'OK', 'old')", (now - 120 * 86400,))
    conn.close()
    result = purge_old_records(now=now, audit_retention_days=90)
    assert result["audit_logs"] == 1
    assert _count("audit_logs") == 1


def test_purge_also_drops_stale_failure_counters():
    now = time.time()
    record_auth_failure("1.1.1.1", now=now - 10 * 86400)
    record_auth_failure("1.1.1.1", now=now)
    result = purge_old_records(now=now, audit_retention_days=90)
    assert result["auth_failures"] == 1
    assert _count("auth_failures") == 1


def test_retention_can_be_configured_and_disabled(monkeypatch):
    now = time.time()
    conn = get_db()
    with conn:
        conn.execute("INSERT INTO audit_logs (id, timestamp, device_id, profile, operation, status, message) "
                     "VALUES ('old', ?, 'dev', 'john', 'X', 'OK', 'old')", (now - 400 * 86400,))
    conn.close()
    monkeypatch.setenv("MOBILE_RELAY_AUDIT_RETENTION_DAYS", "0")  # 0 = keep forever
    assert purge_old_records(now=now)["audit_logs"] == 0
    assert _count("audit_logs") == 1
    monkeypatch.setenv("MOBILE_RELAY_AUDIT_RETENTION_DAYS", "30")
    assert purge_old_records(now=now)["audit_logs"] == 1


def test_startup_purges_and_a_background_task_keeps_purging(monkeypatch):
    now = time.time()

    def insert_old(row_id):
        conn = get_db()
        with conn:
            conn.execute("INSERT INTO audit_logs (id, timestamp, device_id, profile, operation, status, message) "
                         "VALUES (?, ?, 'dev', 'john', 'X', 'OK', 'old')", (row_id, now - 200 * 86400))
        conn.close()

    insert_old("old_at_startup")
    monkeypatch.setenv("MOBILE_RELAY_PURGE_INTERVAL_SECONDS", "0.1")
    with TestClient(app):
        assert _count("audit_logs") == 0, "startup must purge"
        insert_old("old_later")
        deadline = time.time() + 3
        while time.time() < deadline and _count("audit_logs") > 0:
            time.sleep(0.05)
        assert _count("audit_logs") == 0, "the periodic task must purge too"


# ── Behind a reverse proxy ───────────────────────────────────────────────────

def test_startup_warns_when_the_proxy_is_not_trusted_because_everybody_would_share_one_address(monkeypatch, caplog):
    import logging
    monkeypatch.delenv("FORWARDED_ALLOW_IPS", raising=False)
    with caplog.at_level(logging.INFO, logger="mobile-relay"):
        with TestClient(app):
            pass
    text = "\n".join(r.getMessage() for r in caplog.records if r.name == "mobile-relay")
    assert "event=relay_started" in text
    assert "FORWARDED_ALLOW_IPS" in text and "WARNING" in "\n".join(
        r.levelname for r in caplog.records if "FORWARDED_ALLOW_IPS" in r.getMessage())


def test_startup_does_not_warn_when_only_the_proxy_network_is_trusted(monkeypatch, caplog):
    import logging
    monkeypatch.setenv("FORWARDED_ALLOW_IPS", "10.0.0.0/8,172.16.0.0/12,192.168.0.0/16")
    with caplog.at_level(logging.INFO, logger="mobile-relay"):
        with TestClient(app):
            pass
    assert not [r for r in caplog.records if r.name == "mobile-relay" and r.levelno >= logging.WARNING
                and "FORWARDED_ALLOW_IPS" in r.getMessage()]
    assert any("event=relay_started" in r.getMessage() and "proxy_headers=configured" in r.getMessage()
               for r in caplog.records if r.name == "mobile-relay")


def test_startup_warns_when_every_peer_is_trusted_because_the_source_address_can_be_forged(monkeypatch, caplog):
    import logging
    monkeypatch.setenv("FORWARDED_ALLOW_IPS", "*")
    with caplog.at_level(logging.INFO, logger="mobile-relay"):
        with TestClient(app):
            pass
    warnings = [r.getMessage() for r in caplog.records
                if r.name == "mobile-relay" and r.levelno >= logging.WARNING and "FORWARDED_ALLOW_IPS" in r.getMessage()]
    assert warnings and "forged" in warnings[0].lower()

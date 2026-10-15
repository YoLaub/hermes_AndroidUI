"""
The app-facing routes that relay the agent-memory owner validations.

The owner token is its own credential (X-Memory-Owner-Token), unrelated to the MCP tokens the agents hold, and
agent-memory is the only judge of it: the relay never stores or compares it, it only throttles repeated refusals.
"""
import json
import os

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"

import server  # noqa: E402
from database import init_db  # noqa: E402

OWNER = {"X-Memory-Owner-Token": "tok-owner"}
ITEM = {"event_id": "ev-1", "preview": "hello"}


def client_from(ip):
    return TestClient(server.app, client=(ip, 50000))


@pytest.fixture(autouse=True)
def fresh_state():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()


@pytest.fixture
def upstream(monkeypatch):
    calls = []
    reply = {"pending": (200, {"items": [ITEM]}), "decide": (204, {})}

    def pending(token, limit=50):
        calls.append(("pending", token, limit))
        return reply["pending"]

    def decide(token, event_id, decision):
        calls.append(("decide", token, event_id, decision))
        return reply["decide"]

    monkeypatch.setattr(server.memory_validations, "pending", pending)
    monkeypatch.setattr(server.memory_validations, "decide", decide)
    return type("Up", (), {"calls": calls, "reply": reply})


def test_pending_items_are_returned_with_the_token_passed_upstream(upstream):
    r = client_from("203.0.113.1").get("/api/memory/validations?limit=20", headers=OWNER)
    assert r.status_code == 200
    assert r.json() == {"items": [ITEM]}
    assert upstream.calls == [("pending", "tok-owner", "20")]


def test_a_decision_is_sent_and_answered_ok(upstream):
    r = client_from("203.0.113.2").post("/api/memory/validations/ev-1", json={"decision": "confirmed"}, headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"ok": True})
    assert upstream.calls == [("decide", "tok-owner", "ev-1", "confirmed")]


def test_a_request_without_the_owner_token_never_reaches_the_service(upstream):
    c = client_from("203.0.113.3")
    assert c.get("/api/memory/validations").status_code == 401
    assert c.post("/api/memory/validations/ev-1", json={"decision": "confirmed"}).status_code == 401
    assert upstream.calls == []


def test_an_mcp_token_is_not_an_owner_token(upstream):
    """The agents' credential gets no special treatment: it is just another token the service will judge."""
    r = client_from("203.0.113.4").get("/api/memory/validations", headers={"Authorization": "Bearer token_for_john"})
    assert r.status_code == 401
    assert upstream.calls == []


def test_a_service_refusal_keeps_its_status_and_never_echoes_the_token(upstream):
    upstream.reply["decide"] = (409, {"error": "refused by agent-memory"})
    r = client_from("203.0.113.5").post("/api/memory/validations/ev-1", json={"decision": "rejected"}, headers=OWNER)
    assert r.status_code == 409
    assert "tok-owner" not in json.dumps(r.json())


def test_a_decision_body_that_is_not_an_object_is_refused(upstream):
    r = client_from("203.0.113.6").post("/api/memory/validations/ev-1", json=["confirmed"], headers=OWNER)
    assert r.status_code == 422
    assert upstream.calls == []


def test_repeated_owner_token_refusals_lock_the_source_out(upstream):
    upstream.reply["pending"] = (401, {"error": "refused by agent-memory"})
    attacker = client_from("203.0.113.9")
    for _ in range(10):
        assert attacker.get("/api/memory/validations", headers=OWNER).status_code == 401
    blocked = attacker.get("/api/memory/validations", headers=OWNER)
    assert blocked.status_code == 429
    assert len(upstream.calls) == 10


def test_an_accepted_token_is_not_counted_as_a_failure(upstream):
    c = client_from("203.0.113.10")
    for _ in range(15):
        assert c.get("/api/memory/validations", headers=OWNER).status_code == 200


def test_no_cors_header_is_added(upstream):
    r = client_from("203.0.113.11").get("/api/memory/validations", headers={**OWNER, "Origin": "https://evil.example"})
    assert "access-control-allow-origin" not in r.headers

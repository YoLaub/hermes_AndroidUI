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


ACTION_ID = "1c9eb27b-5212-498a-ad1f-641749940202"


@pytest.fixture
def actions_upstream(monkeypatch):
    calls = []
    reply = {"pending": (200, {"items": [{"id": ACTION_ID}]}), "decide": (204, {})}

    def pending_actions(token):
        calls.append(("pending", token))
        return reply["pending"]

    def decide_action(token, action_id, decision):
        calls.append(("decide", token, action_id, decision))
        return reply["decide"]

    monkeypatch.setattr(server.memory_validations, "pending_actions", pending_actions)
    monkeypatch.setattr(server.memory_validations, "decide_action", decide_action)
    return type("Up", (), {"calls": calls, "reply": reply})


def test_pending_actions_are_returned_with_the_token_passed_upstream(actions_upstream):
    r = client_from("203.0.113.20").get("/api/memory/actions", headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"items": [{"id": ACTION_ID}]})
    assert actions_upstream.calls == [("pending", "tok-owner")]


def test_an_action_decision_is_sent_and_answered_ok(actions_upstream):
    r = client_from("203.0.113.21").post(f"/api/memory/actions/{ACTION_ID}", json={"decision": "approved"}, headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"ok": True})
    assert actions_upstream.calls == [("decide", "tok-owner", ACTION_ID, "approved")]


def test_action_routes_without_the_owner_token_never_reach_the_service(actions_upstream):
    c = client_from("203.0.113.22")
    assert c.get("/api/memory/actions").status_code == 401
    assert c.post(f"/api/memory/actions/{ACTION_ID}", json={"decision": "approved"}).status_code == 401
    assert actions_upstream.calls == []


def test_an_action_refusal_keeps_its_status_and_never_echoes_the_token(actions_upstream):
    actions_upstream.reply["decide"] = (404, {"error": "refused by agent-memory"})
    r = client_from("203.0.113.23").post(f"/api/memory/actions/{ACTION_ID}", json={"decision": "approved"}, headers=OWNER)
    assert r.status_code == 404
    assert "tok-owner" not in json.dumps(r.json())


def test_an_action_body_that_is_not_an_object_is_refused(actions_upstream):
    r = client_from("203.0.113.24").post(f"/api/memory/actions/{ACTION_ID}", json=["approved"], headers=OWNER)
    assert r.status_code == 422
    assert actions_upstream.calls == []


def test_repeated_refusals_on_the_action_routes_lock_the_source_out(actions_upstream):
    actions_upstream.reply["pending"] = (401, {"error": "refused by agent-memory"})
    attacker = client_from("203.0.113.25")
    for _ in range(10):
        assert attacker.get("/api/memory/actions", headers=OWNER).status_code == 401
    assert attacker.get("/api/memory/actions", headers=OWNER).status_code == 429
    assert len(actions_upstream.calls) == 10


@pytest.fixture
def profiles_upstream(monkeypatch):
    calls = []
    reply = {"any": (200, {"items": []})}

    def make(name):
        def fn(token, *args):
            calls.append((name, token, *args))
            return reply["any"]
        return fn

    for name in ("profiles", "create_profile", "set_grants", "revoke_profile"):
        monkeypatch.setattr(server.memory_validations, name, make(name))
    return type("Up", (), {"calls": calls, "reply": reply})


def test_profiles_are_listed_with_the_token_passed_upstream(profiles_upstream):
    r = client_from("203.0.113.30").get("/api/memory/profiles", headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"items": []})
    assert profiles_upstream.calls == [("profiles", "tok-owner")]


def test_a_profile_is_created_and_answered_201_with_its_one_time_token(profiles_upstream):
    profiles_upstream.reply["any"] = (201, {"name": "gaston", "token": "one-time"})
    r = client_from("203.0.113.31").post(
        "/api/memory/profiles", json={"name": "gaston", "read": ["other"], "write": []}, headers=OWNER)
    assert (r.status_code, r.json()) == (201, {"name": "gaston", "token": "one-time"})
    assert profiles_upstream.calls == [("create_profile", "tok-owner", "gaston", ["other"], [])]


def test_grants_and_revocation_are_answered_ok(profiles_upstream):
    profiles_upstream.reply["any"] = (204, {})
    c = client_from("203.0.113.32")
    r = c.put("/api/memory/profiles/gaston/grants", json={"read": ["git"], "write": []}, headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"ok": True})
    r = c.post("/api/memory/profiles/gaston/revoke", headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"ok": True})
    assert profiles_upstream.calls == [
        ("set_grants", "tok-owner", "gaston", ["git"], []), ("revoke_profile", "tok-owner", "gaston")]


def test_profile_routes_without_the_owner_token_never_reach_the_service(profiles_upstream):
    c = client_from("203.0.113.33")
    assert c.get("/api/memory/profiles").status_code == 401
    assert c.post("/api/memory/profiles", json={"name": "a"}).status_code == 401
    assert c.put("/api/memory/profiles/a/grants", json={}).status_code == 401
    assert c.post("/api/memory/profiles/a/revoke").status_code == 401
    assert profiles_upstream.calls == []


def test_a_body_that_is_not_an_object_is_refused_for_profiles(profiles_upstream):
    c = client_from("203.0.113.34")
    assert c.post("/api/memory/profiles", json=["x"], headers=OWNER).status_code == 422
    assert c.put("/api/memory/profiles/a/grants", json=["x"], headers=OWNER).status_code == 422
    assert profiles_upstream.calls == []


def test_the_service_wording_of_a_403_reaches_the_app_and_does_not_lock_the_owner_out(profiles_upstream):
    profiles_upstream.reply["any"] = (403, {"error": "refused by agent-memory", "detail": "editing grants is off"})
    c = client_from("203.0.113.35")
    for _ in range(15):
        r = c.post("/api/memory/profiles/a/revoke", headers=OWNER)
        assert (r.status_code, r.json()["detail"]) == (403, "editing grants is off")


def test_repeated_401_on_profiles_lock_the_source_out(profiles_upstream):
    profiles_upstream.reply["any"] = (401, {"error": "refused by agent-memory"})
    attacker = client_from("203.0.113.36")
    for _ in range(10):
        assert attacker.get("/api/memory/profiles", headers=OWNER).status_code == 401
    assert attacker.get("/api/memory/profiles", headers=OWNER).status_code == 429


@pytest.fixture
def mandates_upstream(monkeypatch):
    calls = []
    reply = {"any": (200, {"items": []})}

    def make(name):
        def fn(token, *args, **kwargs):
            calls.append((name, token, *args, *kwargs.values()))
            return reply["any"]
        return fn

    for name in ("mandates", "create_mandate", "revoke_mandate"):
        monkeypatch.setattr(server.memory_validations, name, make(name))
    return type("Up", (), {"calls": calls, "reply": reply})


def test_mandates_are_listed_with_the_token_passed_upstream(mandates_upstream):
    r = client_from("203.0.113.40").get("/api/memory/mandates", headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"items": []})
    assert mandates_upstream.calls == [("mandates", "tok-owner")]


def test_a_mandate_is_created_and_answered_201(mandates_upstream):
    mandates_upstream.reply["any"] = (201, {"id": "m-1"})
    body = {"profile": "gaston", "label": "Trip", "hosts": ["a.org"], "folders": [],
            "expires_at": "2026-10-20T10:00:00+00:00"}
    r = client_from("203.0.113.41").post("/api/memory/mandates", json=body, headers=OWNER)
    assert (r.status_code, r.json()) == (201, {"id": "m-1"})
    assert mandates_upstream.calls == [("create_mandate", "tok-owner", "gaston", "Trip", ["a.org"], [],
                                        "2026-10-20T10:00:00+00:00")]


def test_a_mandate_is_revoked_and_answered_ok(mandates_upstream):
    mandates_upstream.reply["any"] = (204, {})
    r = client_from("203.0.113.42").post("/api/memory/mandates/m-1/revoke", headers=OWNER)
    assert (r.status_code, r.json()) == (200, {"ok": True})
    assert mandates_upstream.calls == [("revoke_mandate", "tok-owner", "m-1")]


def test_mandate_routes_without_the_owner_token_never_reach_the_service(mandates_upstream):
    c = client_from("203.0.113.43")
    assert c.get("/api/memory/mandates").status_code == 401
    assert c.post("/api/memory/mandates", json={}).status_code == 401
    assert c.post("/api/memory/mandates/m-1/revoke").status_code == 401
    assert mandates_upstream.calls == []


def test_a_refused_mandate_keeps_the_service_wording_and_does_not_lock_the_owner_out(mandates_upstream):
    mandates_upstream.reply["any"] = (422, {"error": "refused by agent-memory", "detail": "too many active mandates"})
    c = client_from("203.0.113.44")
    for _ in range(15):
        r = c.post("/api/memory/mandates", json={"profile": "a"}, headers=OWNER)
        assert (r.status_code, r.json()["detail"]) == (422, "too many active mandates")

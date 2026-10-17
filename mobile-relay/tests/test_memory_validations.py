"""
The relay serves two owner routes of the agent-memory service for the Android app.

The owner token is typed in the app and only passes through here: it goes upstream as a Bearer header and is never
stored, logged or echoed back. Runs against a real local HTTP server standing in for agent-memory.
"""
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import memory_validations as mv
import pytest

ITEM = {
    "event_id": "ev-1", "occurred_at": "2026-10-14T23:03:54Z", "zone": "other",
    "origin": "twin", "status": "pending", "source": "hermes", "preview": "hello",
}


class Upstream:
    def __init__(self):
        self.seen = []
        self.reply = (200, {"items": [ITEM]})
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def _answer(self):
                length = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(length).decode() if length else ""
                outer.seen.append((self.command, self.path, self.headers.get("Authorization"), body))
                status, payload = outer.reply
                data = b"" if payload is None else json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            do_GET = do_POST = do_PUT = _answer

            def log_message(self, *args):
                pass

        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_port}"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()


@pytest.fixture
def upstream(monkeypatch):
    up = Upstream()
    monkeypatch.setenv("AGENT_MEMORY_URL", up.url)
    yield up
    up.close()


def test_pending_items_are_relayed_with_the_token_as_a_bearer(upstream):
    status, payload = mv.pending("tok-owner", limit=50)
    assert status == 200
    assert payload == {"items": [ITEM]}
    method, path, auth, _ = upstream.seen[0]
    assert (method, path, auth) == ("GET", "/owner/v1/validations/pending?limit=50", "Bearer tok-owner")


def test_only_the_known_fields_of_an_item_are_relayed(upstream):
    upstream.reply = (200, {"items": [dict(ITEM, secret_internal="x")]})
    _, payload = mv.pending("tok", limit=10)
    assert "secret_internal" not in payload["items"][0]


def test_a_long_preview_is_cut(upstream):
    upstream.reply = (200, {"items": [dict(ITEM, preview="a" * 5000)]})
    _, payload = mv.pending("tok", limit=10)
    assert len(payload["items"][0]["preview"]) <= mv.PREVIEW_MAX


def test_the_limit_is_bounded(upstream):
    mv.pending("tok", limit=100000)
    assert upstream.seen[0][1].endswith("limit=100")
    mv.pending("tok", limit="abc")
    assert upstream.seen[1][1].endswith("limit=50")


def test_a_decision_is_sent_upstream(upstream):
    upstream.reply = (204, None)
    status, payload = mv.decide("tok-owner", "ev-1", "confirmed")
    assert (status, payload) == (204, {})
    method, path, auth, body = upstream.seen[0]
    assert (method, path, auth) == ("POST", "/owner/v1/validations/ev-1", "Bearer tok-owner")
    assert json.loads(body) == {"decision": "confirmed"}


@pytest.mark.parametrize("decision", ["", "approve", "CONFIRMED", None])
def test_any_other_decision_is_refused_before_calling_the_service(upstream, decision):
    status, _ = mv.decide("tok", "ev-1", decision)
    assert status == 422
    assert upstream.seen == []


@pytest.mark.parametrize("event_id", ["", "../admin", "a/b", "ev 1", "x" * 200, "ev?x=1"])
def test_an_event_id_that_could_change_the_path_is_refused(upstream, event_id):
    status, _ = mv.decide("tok", event_id, "rejected")
    assert status == 422
    assert upstream.seen == []


@pytest.mark.parametrize("token", ["", None, "a\r\nX-Evil: 1", "a b\x00"])
def test_a_missing_or_malformed_token_is_refused_before_calling_the_service(upstream, token):
    assert mv.pending(token, limit=5)[0] == 401
    assert mv.decide(token, "ev-1", "confirmed")[0] == 401
    assert upstream.seen == []


@pytest.mark.parametrize("code", [401, 403, 404, 409, 422])
def test_the_service_refusals_keep_their_status(upstream, code):
    upstream.reply = (code, {"detail": "nope"})
    status, payload = mv.decide("tok", "ev-1", "confirmed")
    assert status == code
    assert "tok" not in json.dumps(payload)


def test_a_service_error_is_a_bad_gateway_without_details(upstream):
    upstream.reply = (500, {"trace": "internal stack tok-owner"})
    status, payload = mv.pending("tok-owner", limit=5)
    assert status == 502
    assert "tok-owner" not in json.dumps(payload) and "stack" not in json.dumps(payload)


def test_an_unreachable_service_is_a_bad_gateway(monkeypatch):
    monkeypatch.setenv("AGENT_MEMORY_URL", "http://127.0.0.1:1")
    assert mv.pending("tok", limit=5)[0] == 502


def test_without_a_configured_service_nothing_is_called(monkeypatch):
    monkeypatch.delenv("AGENT_MEMORY_URL", raising=False)
    assert mv.pending("tok", limit=5)[0] == 503


ACTION = {
    "id": "1c9eb27b-5212-498a-ad1f-641749940202", "kind": "run", "profile": "gaston", "session_id": "423772f5e8bb",
    "label": "set -o pipefail", "reason": "this session has read untrusted content: it cannot write or run anything alone",
    "requested_at": "2026-10-16T21:00:00Z",
}


def test_pending_actions_are_relayed_with_the_token_as_a_bearer(upstream):
    upstream.reply = (200, {"items": [ACTION]})
    status, payload = mv.pending_actions("tok-owner")
    assert (status, payload) == (200, {"items": [ACTION]})
    method, path, auth, _ = upstream.seen[0]
    assert (method, path, auth) == ("GET", "/owner/v1/actions/pending", "Bearer tok-owner")


def test_only_the_known_fields_of_an_action_are_relayed_and_a_long_label_is_cut(upstream):
    upstream.reply = (200, {"items": [dict(ACTION, label="a" * 9000, fingerprint="internal")]})
    _, payload = mv.pending_actions("tok")
    item = payload["items"][0]
    assert "fingerprint" not in item
    assert len(item["label"]) <= mv.LABEL_MAX


def test_an_action_decision_is_sent_upstream(upstream):
    upstream.reply = (204, None)
    status, payload = mv.decide_action("tok-owner", ACTION["id"], "approved")
    assert (status, payload) == (204, {})
    method, path, auth, body = upstream.seen[0]
    assert (method, path, auth) == ("POST", f"/owner/v1/actions/{ACTION['id']}/decision", "Bearer tok-owner")
    assert json.loads(body) == {"decision": "approved"}


@pytest.mark.parametrize("decision", ["", "confirmed", "APPROVED", None])
def test_an_action_needs_approved_or_rejected(upstream, decision):
    assert mv.decide_action("tok", ACTION["id"], decision)[0] == 422
    assert upstream.seen == []


@pytest.mark.parametrize("action_id", ["", "../admin", "a/b", "x" * 200, "id?x=1"])
def test_an_action_id_that_could_change_the_path_is_refused(upstream, action_id):
    assert mv.decide_action("tok", action_id, "rejected")[0] == 422
    assert upstream.seen == []


def test_a_malformed_token_never_reaches_the_service_for_actions(upstream):
    assert mv.pending_actions("a\r\nX-Evil: 1")[0] == 401
    assert mv.decide_action("", ACTION["id"], "approved")[0] == 401
    assert upstream.seen == []


PROFILE = {"name": "gaston", "state": "active", "read": ["other", "git"], "write": ["other"]}


def test_profiles_are_relayed_with_only_the_known_fields(upstream):
    upstream.reply = (200, {"items": [dict(PROFILE, token="never-relayed", token_sha256="x")]})
    status, payload = mv.profiles("tok-owner")
    assert (status, payload) == (200, {"items": [PROFILE]})
    method, path, auth, _ = upstream.seen[0]
    assert (method, path, auth) == ("GET", "/owner/v1/profiles", "Bearer tok-owner")


def test_a_profile_is_created_and_its_token_comes_back_once(upstream):
    upstream.reply = (201, {"name": "gaston", "token": "one-time-token", "extra": 1})
    status, payload = mv.create_profile("tok-owner", "gaston", ["other"], ["other"])
    assert (status, payload) == (201, {"name": "gaston", "token": "one-time-token"})
    method, path, _, body = upstream.seen[0]
    assert (method, path) == ("POST", "/owner/v1/profiles")
    assert json.loads(body) == {"name": "gaston", "read": ["other"], "write": ["other"]}


def test_a_profile_with_no_grant_at_all_is_valid(upstream):
    upstream.reply = (201, {"name": "empty", "token": "t"})
    assert mv.create_profile("tok", "empty", [], [])[0] == 201


@pytest.mark.parametrize("name", ["", "Gaston", "a b", "../x", "a/b", "x" * 101, "é"])
def test_a_profile_name_outside_the_rules_is_refused_before_the_service(upstream, name):
    assert mv.create_profile("tok", name, [], [])[0] == 422
    assert mv.set_grants("tok", name, [], [])[0] == 422
    assert mv.revoke_profile("tok", name)[0] == 422
    assert upstream.seen == []


@pytest.mark.parametrize("read,write", [(["nowhere"], []), ([], ["x"]), ("other", []), ([], None), ([1], [])])
def test_unknown_zones_or_wrong_shapes_are_refused_before_the_service(upstream, read, write):
    assert mv.set_grants("tok", "gaston", read, write)[0] == 422
    assert upstream.seen == []


def test_grants_are_replaced_with_a_put(upstream):
    upstream.reply = (204, None)
    assert mv.set_grants("tok-owner", "gaston", ["other"], []) == (204, {})
    method, path, auth, body = upstream.seen[0]
    assert (method, path, auth) == ("PUT", "/owner/v1/profiles/gaston/grants", "Bearer tok-owner")
    assert json.loads(body) == {"read": ["other"], "write": []}


def test_a_profile_is_revoked(upstream):
    upstream.reply = (204, None)
    assert mv.revoke_profile("tok-owner", "gaston") == (204, {})
    method, path, _, body = upstream.seen[0]
    assert (method, path) == ("POST", "/owner/v1/profiles/gaston/revoke")


def test_the_service_detail_of_a_refusal_is_kept_for_profiles(upstream):
    detail = "the zone(s) secrets cannot be granted through the API: use the admin command (python -m agent_memory.admin set-grants)"
    upstream.reply = (403, {"detail": detail})
    status, payload = mv.set_grants("tok-owner", "gaston", ["secrets"], [])
    assert (status, payload["detail"]) == (403, detail)


def test_a_detail_that_is_not_text_or_that_holds_the_token_is_dropped(upstream):
    upstream.reply = (422, {"detail": [{"loc": ["body"], "input": "tok-owner"}]})
    assert "detail" not in mv.create_profile("tok-owner", "gaston", [], [])[1]
    upstream.reply = (403, {"detail": "bad token tok-owner"})
    status, payload = mv.revoke_profile("tok-owner", "gaston")
    assert status == 403 and "tok-owner" not in json.dumps(payload)


def test_a_long_detail_is_cut(upstream):
    upstream.reply = (403, {"detail": "d" * 5000})
    assert len(mv.revoke_profile("tok", "gaston")[1]["detail"]) <= mv.DETAIL_MAX


def test_the_older_calls_still_hide_the_detail(upstream):
    upstream.reply = (409, {"detail": "internal wording"})
    assert mv.decide("tok", "ev-1", "confirmed")[1] == {"error": "refused by agent-memory"}


def test_a_malformed_token_never_reaches_the_service_for_profiles(upstream):
    assert mv.profiles("a\r\nX: 1")[0] == 401
    assert mv.create_profile("", "gaston", [], [])[0] == 401
    assert upstream.seen == []

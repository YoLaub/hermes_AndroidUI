"""The agent-memory owner validation routes, relayed for the Android app.

agent-memory is not published; the relay sits in the same Compose network and reaches it through AGENT_MEMORY_URL. The
owner token is typed in the app and arrives with each request: it goes upstream as a Bearer header and is never
stored, logged or echoed back. Everything coming from the service is data for display, never instructions.
"""
import json
import os
import re
import urllib.error
import urllib.request

PREVIEW_MAX = 500
LABEL_MAX = 2000
DETAIL_MAX = 400
RESPONSE_MAX = 1024 * 1024
TIMEOUT = 10
DECISIONS = ("confirmed", "rejected")
ACTION_DECISIONS = ("approved", "rejected")
_EVENT_ID = re.compile(r"[A-Za-z0-9_.:-]{1,128}")
_TOKEN = re.compile(r"[\x21-\x7e]{1,512}")
_FIELDS = ("event_id", "occurred_at", "zone", "origin", "status", "source", "preview")
_ACTION_FIELDS = ("id", "kind", "profile", "session_id", "label", "reason", "requested_at")
_PROFILE_NAME = re.compile(r"[a-z0-9_-]{1,100}")
ZONES = ("secrets", "finance", "health", "legal", "personal", "professional", "git", "other")
_PROFILE_FIELDS = ("name", "state", "read", "write")
_PASSED_THROUGH = (401, 403, 404, 409, 422)


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect())


def _base_url():
    return (os.environ.get("AGENT_MEMORY_URL") or "").strip().rstrip("/")


def _refusal(error, token, keep_detail):
    """The refusal sent to the app. The service's own wording is relayed only when asked for, as short text without the token."""
    refused = {"error": "refused by agent-memory"}
    if not keep_detail:
        return refused
    try:
        detail = json.loads(error.read(RESPONSE_MAX + 1)).get("detail")
    except (ValueError, AttributeError, OSError):
        return refused
    if isinstance(detail, str) and token not in detail:
        refused["detail"] = detail[:DETAIL_MAX]
    return refused


def _call(method, path, token, body=None, keep_detail=False):
    """Return (status, parsed JSON or {}); statuses the service uses to refuse are kept, the rest is a 502."""
    if not isinstance(token, str) or not _TOKEN.fullmatch(token):
        return 401, {"error": "owner token missing or malformed"}
    base = _base_url()
    if not base:
        return 503, {"error": "agent-memory is not configured"}
    data = None if body is None else json.dumps(body).encode()
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with _opener.open(request, timeout=TIMEOUT) as response:
            raw = response.read(RESPONSE_MAX + 1)
            status = response.status
    except urllib.error.HTTPError as error:
        try:
            if error.code in _PASSED_THROUGH:
                return error.code, _refusal(error, token, keep_detail)
            return 502, {"error": "agent-memory error"}
        finally:
            error.close()
    except (urllib.error.URLError, TimeoutError, OSError):
        return 502, {"error": "agent-memory unreachable"}
    if len(raw) > RESPONSE_MAX:
        return 502, {"error": "agent-memory response too large"}
    try:
        return status, (json.loads(raw) if raw else {})
    except ValueError:
        return 502, {"error": "agent-memory sent invalid data"}


def _clean_item(item):
    if not isinstance(item, dict):
        return None
    cleaned = {k: item.get(k) for k in _FIELDS}
    preview = cleaned.get("preview")
    cleaned["preview"] = preview[:PREVIEW_MAX] if isinstance(preview, str) else ""
    return cleaned


def pending(token, limit=50):
    try:
        limit = int(limit)
    except (TypeError, ValueError):
        limit = 50
    limit = max(1, min(limit, 100))
    status, payload = _call("GET", f"/owner/v1/validations/pending?limit={limit}", token)
    if status != 200:
        return status, payload
    items = payload.get("items") if isinstance(payload, dict) else None
    cleaned = [c for c in (_clean_item(i) for i in (items if isinstance(items, list) else [])) if c]
    return 200, {"items": cleaned}


def decide(token, event_id, decision):
    if decision not in DECISIONS:
        return 422, {"error": "decision must be confirmed or rejected"}
    if not isinstance(event_id, str) or not _EVENT_ID.fullmatch(event_id):
        return 422, {"error": "invalid event id"}
    status, payload = _call("POST", f"/owner/v1/validations/{event_id}", token, {"decision": decision})
    if status in (200, 204):
        return 204, {}
    return status, payload


def _clean_action(item):
    if not isinstance(item, dict):
        return None
    cleaned = {k: item.get(k) for k in _ACTION_FIELDS}
    label = cleaned.get("label")
    cleaned["label"] = label[:LABEL_MAX] if isinstance(label, str) else ""
    return cleaned


def pending_actions(token):
    """The action gate: what the agents asked to do and the owner has not decided yet."""
    status, payload = _call("GET", "/owner/v1/actions/pending", token)
    if status != 200:
        return status, payload
    items = payload.get("items") if isinstance(payload, dict) else None
    cleaned = [c for c in (_clean_action(i) for i in (items if isinstance(items, list) else [])) if c]
    return 200, {"items": cleaned}


def decide_action(token, action_id, decision):
    if decision not in ACTION_DECISIONS:
        return 422, {"error": "decision must be approved or rejected"}
    if not isinstance(action_id, str) or not _EVENT_ID.fullmatch(action_id):
        return 422, {"error": "invalid action id"}
    status, payload = _call("POST", f"/owner/v1/actions/{action_id}/decision", token, {"decision": decision})
    if status in (200, 204):
        return 204, {}
    return status, payload


def _profile_name_ok(name):
    return isinstance(name, str) and _PROFILE_NAME.fullmatch(name) is not None


def _zones(value):
    """A list of known zones without repeats, or None when the shape is wrong."""
    if not isinstance(value, list) or not all(isinstance(z, str) and z in ZONES for z in value):
        return None
    return list(dict.fromkeys(value))


def _clean_profile(item):
    if not isinstance(item, dict):
        return None
    return {k: item.get(k) for k in _PROFILE_FIELDS}


def profiles(token):
    status, payload = _call("GET", "/owner/v1/profiles", token, keep_detail=True)
    if status != 200:
        return status, payload
    items = payload.get("items") if isinstance(payload, dict) else None
    cleaned = [c for c in (_clean_profile(i) for i in (items if isinstance(items, list) else [])) if c]
    return 200, {"items": cleaned}


def create_profile(token, name, read, write):
    """The service answers with the profile's token, shown once: it only passes through here."""
    read, write = _zones(read), _zones(write)
    if not _profile_name_ok(name) or read is None or write is None:
        return 422, {"error": "invalid profile name or zones"}
    status, payload = _call("POST", "/owner/v1/profiles", token, {"name": name, "read": read, "write": write}, keep_detail=True)
    if status != 201:
        return status, payload
    created = payload if isinstance(payload, dict) else {}
    if not isinstance(created.get("name"), str) or not isinstance(created.get("token"), str):
        return 502, {"error": "agent-memory sent invalid data"}
    return 201, {"name": created["name"], "token": created["token"]}


def set_grants(token, name, read, write):
    read, write = _zones(read), _zones(write)
    if not _profile_name_ok(name) or read is None or write is None:
        return 422, {"error": "invalid profile name or zones"}
    status, payload = _call("PUT", f"/owner/v1/profiles/{name}/grants", token, {"read": read, "write": write}, keep_detail=True)
    return (204, {}) if status in (200, 204) else (status, payload)


def revoke_profile(token, name):
    if not _profile_name_ok(name):
        return 422, {"error": "invalid profile name"}
    status, payload = _call("POST", f"/owner/v1/profiles/{name}/revoke", token, keep_detail=True)
    return (204, {}) if status in (200, 204) else (status, payload)

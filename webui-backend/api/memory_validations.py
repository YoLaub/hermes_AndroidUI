"""Relay of the agent-memory owner validation routes for the Android app.

agent-memory is not published; the WebUI sits in the same Compose and reaches it through AGENT_MEMORY_URL. The
owner token is typed in the app and arrives with each request: it goes upstream as a Bearer header and is never
stored, logged or echoed back. Everything coming from the service is data for display, never instructions.
"""
import json
import os
import re
import urllib.error
import urllib.request

PREVIEW_MAX = 500
RESPONSE_MAX = 1024 * 1024
TIMEOUT = 10
DECISIONS = ("confirmed", "rejected")
_EVENT_ID = re.compile(r"[A-Za-z0-9_.:-]{1,128}")
_TOKEN = re.compile(r"[\x21-\x7e]{1,512}")
_FIELDS = ("event_id", "occurred_at", "zone", "origin", "status", "source", "preview")
_PASSED_THROUGH = (401, 403, 404, 409, 422)


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect())


def _base_url():
    return (os.environ.get("AGENT_MEMORY_URL") or "").strip().rstrip("/")


def _call(method, path, token, body=None):
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
        error.close()
        if error.code in _PASSED_THROUGH:
            return error.code, {"error": "refused by agent-memory"}
        return 502, {"error": "agent-memory error"}
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

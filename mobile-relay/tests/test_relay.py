import os
import json
import pytest
import asyncio
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"
os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_MARIO"] = "token_for_mario"
os.environ["MOBILE_CONTROL_TOKEN_GASTON"] = "token_for_gaston"

from server import app, manager, ActiveSession
from database import init_db, verify_device_token, register_device, reset_pairing_attempts
from models import MobileCommand, MobileCommandResult, MobileScreenData, MobileElementInfo

client = TestClient(app)

@pytest.fixture(autouse=True)
def setup_test_db():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()
    manager.active_connections.clear()
    manager.active_sessions.clear()
    manager.pending_commands.clear()
    manager.device_in_flight.clear()

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"

# ── Fix 1: Authentification stricte & Pas de token dans l'URL ──────────────────

def test_mcp_unauthorized_without_token():
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/list",
        "params": {}
    }
    # No auth header
    res = client.post("/mcp", json=payload)
    assert res.status_code == 401

    # Invalid auth header
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer bad_token"})
    assert res.status_code == 401

def test_hermes_profile_tokens_strict_mapping_and_no_url_token(monkeypatch):
    monkeypatch.setenv("HERMES_PROFILE_TOKENS", json.dumps({"mario": "super_secret_token_mario"}))

    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "mobile_control_status",
            "arguments": {}
        }
    }

    # 1. Sending the profile name 'mario' as Bearer token MUST BE REJECTED
    res_key = client.post("/mcp", json=payload, headers={"Authorization": "Bearer mario"})
    assert res_key.status_code == 401

    # 2. Sending the secret token MUST BE ACCEPTED and identify profile mario
    res_val = client.post("/mcp", json=payload, headers={"Authorization": "Bearer super_secret_token_mario"})
    assert res_val.status_code == 200
    content = res_val.json()["result"]["content"][0]["text"]
    assert "profil 'mario'" in content

    # 3. Sending token as URL query param MUST BE REJECTED (no URL tokens allowed)
    res_url = client.post("/mcp?token=super_secret_token_mario", json=payload)
    assert res_url.status_code == 401

# ── Fix 2: Permissions (Refuser tout mode inconnu pour interactions) ──────────

def test_unknown_and_observation_modes_deny_interaction():
    # 1. Mode observation
    session_obs = ActiveSession(
        session_id="ses_obs",
        device_id="dev_obs",
        target_package="com.linkedin.android",
        allowed_profile="mario",
        mode="observation",
        expires_at=9999999999.0
    )
    manager.set_active_session(session_obs)

    payload_click = {
        "jsonrpc": "2.0",
        "id": 10,
        "method": "tools/call",
        "params": {
            "name": "mobile_click_element",
            "arguments": {"element_ref": "el_1"}
        }
    }
    res_obs = client.post("/mcp", json=payload_click, headers={"Authorization": "Bearer token_for_mario"})
    assert res_obs.status_code == 200
    assert res_obs.json()["result"]["isError"] is True
    assert "MODE_DENIED" in res_obs.json()["result"]["content"][0]["text"]

    # 2. Mode inconnu (ex: 'audit', 'custom_read', etc.)
    session_unknown = ActiveSession(
        session_id="ses_unk",
        device_id="dev_unk",
        target_package="com.linkedin.android",
        allowed_profile="mario",
        mode="audit",
        expires_at=9999999999.0
    )
    manager.set_active_session(session_unknown)

    res_unk = client.post("/mcp", json=payload_click, headers={"Authorization": "Bearer token_for_mario"})
    assert res_unk.status_code == 200
    assert res_unk.json()["result"]["isError"] is True
    assert "MODE_DENIED" in res_unk.json()["result"]["content"][0]["text"]

# ── Fix 3: Appairage (Transaction atomique, Rate Limit, Protection écrasement) ──

def test_pair_generate_requires_admin_token():
    # Unauthorized
    res = client.post("/api/pair/generate", json={"user_id": "test_user"})
    assert res.status_code == 401

    # Authorized with admin token
    res = client.post(
        "/api/pair/generate",
        json={"user_id": "test_user"},
        headers={"Authorization": "Bearer test_admin_token_12345"}
    )
    assert res.status_code == 200
    code = res.json()["code"]
    assert len(code) == 6

    # Verify device with pairing code
    res_v = client.post("/api/pair/verify", json={"code": code, "device_id": "dev_test_1"})
    assert res_v.status_code == 200
    token = res_v.json()["device_token"]
    assert token.startswith("tok_")
    assert verify_device_token("dev_test_1", token) is True

def test_pairing_code_atomic_consumption_single_use():
    # Generate code
    res = client.post("/api/pair/generate", json={"user_id": "user1"}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code = res.json()["code"]

    # First verify succeeds
    res1 = client.post("/api/pair/verify", json={"code": code, "device_id": "dev_atom_1"})
    assert res1.status_code == 200

    # Second verify with same code fails (code was consumed atomically)
    res2 = client.post("/api/pair/verify", json={"code": code, "device_id": "dev_atom_2"})
    assert res2.status_code == 400

def test_pairing_code_concurrent_race_condition():
    from concurrent.futures import ThreadPoolExecutor
    # Generate code
    res = client.post("/api/pair/generate", json={"user_id": "race_user"}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code = res.json()["code"]

    def attempt_verify(dev_num):
        # Create dedicated client per thread
        c = TestClient(app)
        return c.post("/api/pair/verify", json={"code": code, "device_id": f"dev_race_{dev_num}"})

    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = [executor.submit(attempt_verify, i) for i in range(5)]
        results = [f.result() for f in futures]

    successes = [r for r in results if r.status_code == 200]
    failures = [r for r in results if r.status_code == 400]

    # Exactly ONE thread can succeed, all other simultaneous threads MUST fail
    assert len(successes) == 1
    assert len(failures) == 4


def test_pairing_device_overwrite_protection():
    # 1. Pair dev_fixed first time
    res = client.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code1 = res.json()["code"]
    res_pair1 = client.post("/api/pair/verify", json={"code": code1, "device_id": "dev_fixed"})
    assert res_pair1.status_code == 200
    orig_token = res_pair1.json()["device_token"]

    # 2. Attempt to overwrite dev_fixed without any token (or bogus token)
    res = client.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code2 = res.json()["code"]
    res_pair2 = client.post("/api/pair/verify", json={"code": code2, "device_id": "dev_fixed", "current_device_token": "wrong_tok"})
    assert res_pair2.status_code == 403
    assert "L'écrasement nécessite le token actuel" in res_pair2.json()["detail"]

    # 3. Overwrite with valid current_device_token
    res = client.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code3 = res.json()["code"]
    res_pair3 = client.post("/api/pair/verify", json={"code": code3, "device_id": "dev_fixed", "current_device_token": orig_token})
    assert res_pair3.status_code == 200
    new_token = res_pair3.json()["device_token"]
    assert new_token != orig_token
    assert verify_device_token("dev_fixed", new_token) is True

    # 4. Overwrite with admin_token authorization
    res = client.post("/api/pair/generate", json={}, headers={"Authorization": "Bearer test_admin_token_12345"})
    code4 = res.json()["code"]
    res_pair4 = client.post("/api/pair/verify", json={"code": code4, "device_id": "dev_fixed", "admin_token": "test_admin_token_12345"})
    assert res_pair4.status_code == 200
    token4 = res_pair4.json()["device_token"]
    assert token4 != new_token
    assert verify_device_token("dev_fixed", token4) is True

def test_pairing_rate_limiting_cannot_be_bypassed_by_changing_device_id():
    # 5 failed attempts with different device_ids from the same client IP
    for i in range(5):
        client.post("/api/pair/verify", json={"code": "BADCOD", "device_id": f"dev_brute_{i}"})

    # 6th attempt with a completely new device_id is STILL rate limited (429)
    res_limit = client.post("/api/pair/verify", json={"code": "BADCOD", "device_id": "dev_brand_new"})
    assert res_limit.status_code == 429
    assert "Trop de tentatives" in res_limit.json()["detail"]

# ── Fix 4: Reconnexion (Vérification d'identité avant déconnexion) ─────────────

def test_reconnection_stale_disconnect_does_not_unregister_new_socket():
    ws_old = object()
    ws_new = object()

    # Old connection registers
    manager.register_connection("phone_1", ws_old)
    assert manager.active_connections["phone_1"] == ws_old

    # New reconnection comes in
    manager.register_connection("phone_1", ws_new)
    assert manager.active_connections["phone_1"] == ws_new

    # Old connection disconnect event fires late
    manager.unregister_connection("phone_1", websocket=ws_old)
    # The active connection MUST still be ws_new, not wiped out!
    assert manager.active_connections.get("phone_1") == ws_new

    # When new connection disconnects
    manager.unregister_connection("phone_1", websocket=ws_new)
    assert "phone_1" not in manager.active_connections

# ── Fix 5: MCP (Notifications HTTP 202 et négociation de version 2025-03-26) ──

def test_mcp_initialize_version_negotiation():
    # Default initialize negotiates 2025-03-26
    payload_def = {
        "jsonrpc": "2.0",
        "id": "init-def",
        "method": "initialize",
        "params": {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "hermes-agent", "version": "1.0.0"}
        }
    }
    res_def = client.post("/mcp", json=payload_def, headers={"Authorization": "Bearer token_for_mario"})
    assert res_def.status_code == 200
    assert res_def.json()["result"]["protocolVersion"] == "2025-03-26"
    assert res_def.headers.get("MCP-Protocol-Version") == "2025-03-26"

    # Backward compatibility with 2024-11-05
    payload_legacy = {
        "jsonrpc": "2.0",
        "id": "init-leg",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "hermes-agent", "version": "1.0.0"}
        }
    }
    res_leg = client.post("/mcp", json=payload_legacy, headers={"Authorization": "Bearer token_for_mario"})
    assert res_leg.status_code == 200
    assert res_leg.json()["result"]["protocolVersion"] == "2024-11-05"
    assert res_leg.headers.get("MCP-Protocol-Version") == "2024-11-05"

def test_mcp_notifications_return_202():
    # Named notification
    payload1 = {
        "jsonrpc": "2.0",
        "method": "notifications/initialized",
        "params": {}
    }
    res1 = client.post("/mcp", json=payload1, headers={"Authorization": "Bearer token_for_mario"})
    assert res1.status_code == 202
    assert "MCP-Protocol-Version" in res1.headers

    # Notification without id
    payload2 = {
        "jsonrpc": "2.0",
        "method": "custom_notification",
        "params": {}
    }
    res2 = client.post("/mcp", json=payload2, headers={"Authorization": "Bearer token_for_mario"})
    assert res2.status_code == 202

def test_mcp_ping():
    payload = {
        "jsonrpc": "2.0",
        "id": 99,
        "method": "ping",
        "params": {}
    }
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer token_for_mario"})
    assert res.status_code == 200
    assert res.json()["id"] == 99
    assert res.json()["result"] == {}

# ── Tests complémentaires (class_name & websocket security) ───────────────────

def test_class_name_formatting():
    session = ActiveSession(
        session_id="ses_1",
        device_id="dev_1",
        target_package="com.linkedin.android",
        allowed_profile="mario",
        mode="interaction",
        expires_at=9999999999.0
    )
    manager.set_active_session(session)

    screen_data = MobileScreenData(
        screen_revision="rev_1",
        package_name="com.linkedin.android",
        elements=[
            MobileElementInfo(
                element_ref="el_1",
                class_name="android.widget.Button",
                text="Publier",
                clickable=True
            ),
            MobileElementInfo(
                element_ref="el_2",
                class_name="android.widget.EditText",
                content_desc="Écrire un post",
                editable=True
            )
        ]
    )
    result = MobileCommandResult(
        command_id="cmd_test",
        status="success",
        data=screen_data
    )

    elements_summary = []
    for el in result.data.elements:
        attrs = []
        if el.clickable: attrs.append("clickable")
        if el.editable: attrs.append("editable")
        if el.scrollable: attrs.append("scrollable")
        attr_str = f" [{', '.join(attrs)}]" if attrs else ""
        c_name = el.class_name or "View"
        text_display = f"\"{el.text}\"" if el.text else (f"desc=\"{el.content_desc}\"" if el.content_desc else c_name)
        elements_summary.append(f"- [{el.element_ref}] {c_name}: {text_display}{attr_str}")

    assert "- [el_1] android.widget.Button: \"Publier\" [clickable]" in elements_summary
    assert "- [el_2] android.widget.EditText: desc=\"Écrire un post\" [editable]" in elements_summary

def test_websocket_result_security():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    fut = loop.create_future()

    manager.pending_commands["cmd_secure_1"] = (fut, "dev_real", 9999999999.0)

    fake_result = MobileCommandResult(
        command_id="cmd_secure_1",
        status="success",
        message="Hacked result"
    )
    manager.resolve_command_result("dev_fake", fake_result)
    assert not fut.done()

    real_result = MobileCommandResult(
        command_id="cmd_secure_1",
        status="success",
        message="Valid result"
    )
    manager.resolve_command_result("dev_real", real_result)
    assert fut.done()
    assert fut.result().message == "Valid result"

def test_concurrent_command_denied():
    manager.active_connections["dev_busy"] = object()
    manager.device_in_flight["dev_busy"] = "cmd_1"

    loop = asyncio.new_event_loop()
    cmd = MobileCommand(
        command_id="cmd_2",
        session_id="ses_busy",
        operation="observe",
        target_package="com.linkedin.android"
    )
    result = loop.run_until_complete(manager.send_command_to_device("dev_busy", cmd))
    assert result.status == "rejected"
    assert result.error_code == "CONCURRENT_COMMAND_DENIED"

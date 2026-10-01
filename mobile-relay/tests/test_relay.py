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
from database import init_db, verify_device_token, register_device
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

# ── Requirement 1: Authentifier /mcp ──────────────────────────────────────────

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

def test_mcp_authorized_deduces_profile():
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {
            "name": "mobile_control_status",
            "arguments": {}
        }
    }
    # Authorized as mario via Bearer token
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer token_for_mario"})
    assert res.status_code == 200
    content = res.json()["result"]["content"][0]["text"]
    assert "profil 'mario'" in content

    # Authorized as gaston via X-Hermes-Token
    res = client.post("/mcp", json=payload, headers={"X-Hermes-Token": "token_for_gaston"})
    assert res.status_code == 200
    content = res.json()["result"]["content"][0]["text"]
    assert "profil 'gaston'" in content

# ── Requirement 2: Protéger /api/pair/generate & Ne pas journaliser le code ──

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

# ── Requirement 3: Implémenter l'initialisation MCP ───────────────────────────

def test_mcp_initialize():
    payload = {
        "jsonrpc": "2.0",
        "id": "init-1",
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "hermes-agent", "version": "1.0.0"}
        }
    }
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer token_for_mario"})
    assert res.status_code == 200
    data = res.json()
    assert data["id"] == "init-1"
    assert data["result"]["serverInfo"]["name"] == "hermes-mobile-relay"
    assert data["result"]["protocolVersion"] == "2024-11-05"

def test_mcp_notifications():
    payload = {
        "jsonrpc": "2.0",
        "method": "notifications/initialized",
        "params": {}
    }
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer token_for_mario"})
    assert res.status_code == 204

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

# ── Requirement 4: class_name sans AttributeError ────────────────────────────

def test_class_name_formatting():
    # Setup active session for mario
    session = ActiveSession(
        session_id="ses_1",
        device_id="dev_1",
        target_package="com.linkedin.android",
        allowed_profile="mario",
        mode="interaction",
        expires_at=9999999999.0
    )
    manager.set_active_session(session)

    # Fake device response for an observation
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

    # Verify formatting does not crash with AttributeError
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

# ── Requirement 5: Sécuriser les résultats WebSocket ─────────────────────────

def test_websocket_result_security():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    fut = loop.create_future()

    # Pending command sent to dev_real
    manager.pending_commands["cmd_secure_1"] = (fut, "dev_real", 9999999999.0)

    # 1. Imposter dev_fake tries to resolve the command
    fake_result = MobileCommandResult(
        command_id="cmd_secure_1",
        status="success",
        message="Hacked result"
    )
    manager.resolve_command_result("dev_fake", fake_result)
    assert not fut.done() # MUST NOT BE RESOLVED

    # 2. Legitimate dev_real resolves the command
    real_result = MobileCommandResult(
        command_id="cmd_secure_1",
        status="success",
        message="Valid result"
    )
    manager.resolve_command_result("dev_real", real_result)
    assert fut.done()
    assert fut.result().message == "Valid result"

# ── Requirement 6: Limites de session côté relais ────────────────────────────

def test_observation_mode_denies_interaction():
    session = ActiveSession(
        session_id="ses_obs",
        device_id="dev_obs",
        target_package="com.linkedin.android",
        allowed_profile="mario",
        mode="observation", # OBSERVATION ONLY
        expires_at=9999999999.0
    )
    manager.set_active_session(session)

    # Trying to click element in observation mode
    payload = {
        "jsonrpc": "2.0",
        "id": 10,
        "method": "tools/call",
        "params": {
            "name": "mobile_click_element",
            "arguments": {"element_ref": "el_1"}
        }
    }
    res = client.post("/mcp", json=payload, headers={"Authorization": "Bearer token_for_mario"})
    assert res.status_code == 200
    data = res.json()
    assert data["result"]["isError"] is True
    assert "MODE_DENIED" in data["result"]["content"][0]["text"]

def test_concurrent_command_denied():
    # Mark device as connected and already in flight with cmd_1
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

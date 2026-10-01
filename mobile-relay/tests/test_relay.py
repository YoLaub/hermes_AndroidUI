import pytest
import asyncio
from fastapi.testclient import TestClient
import os

os.environ["MOBILE_RELAY_DB_PATH"] = "/tmp/test_mobile_relay.db"

from server import app
from database import init_db, save_pairing_code, consume_pairing_code, register_device, verify_device_token

client = TestClient(app)

@pytest.fixture(autouse=True)
def setup_test_db():
    if os.path.exists("/tmp/test_mobile_relay.db"):
        os.remove("/tmp/test_mobile_relay.db")
    init_db()

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"

def test_pairing_flow():
    # 1. Generate pairing code
    res = client.post("/api/pair/generate", json={"user_id": "test_user"})
    assert res.status_code == 200
    code = res.json()["code"]
    assert len(code) == 6

    # 2. Verify pairing code
    res = client.post("/api/pair/verify", json={"code": code, "device_id": "dev_test_1"})
    assert res.status_code == 200
    token = res.json()["device_token"]
    assert token.startswith("tok_")

    # 3. Verify in DB
    assert verify_device_token("dev_test_1", token) is True
    assert verify_device_token("dev_test_1", "bad_token") is False

def test_mcp_tools_list():
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/list",
        "params": {}
    }
    res = client.post("/mcp", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert "result" in data
    tools = data["result"]["tools"]
    tool_names = [t["name"] for t in tools]
    assert "mobile_control_status" in tool_names
    assert "mobile_observe" in tool_names
    assert "mobile_click_element" in tool_names
    assert "mobile_set_text" in tool_names
    assert "mobile_scroll" in tool_names
    assert "mobile_launch_allowed_app" in tool_names
    assert "mobile_back" in tool_names
    assert "mobile_end_session" in tool_names

def test_mcp_status_no_session():
    payload = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
            "name": "mobile_control_status",
            "arguments": {}
        }
    }
    res = client.post("/mcp", json=payload, headers={"X-Hermes-Profile": "mario"})
    assert res.status_code == 200
    content = res.json()["result"]["content"][0]["text"]
    assert "Aucune session" in content

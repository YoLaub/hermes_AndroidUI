"""A draft handoff ends John's control; it never reports an automatic publication."""

import os
import time
from unittest.mock import AsyncMock

import pytest
from fastapi.testclient import TestClient

os.environ["MOBILE_RELAY_ADMIN_TOKEN"] = "test_admin_token_12345"
os.environ["MOBILE_CONTROL_TOKEN_JOHN"] = "token_for_john"
from models import MobileCommandResult
from server import ActiveSession, app, manager


@pytest.fixture
def relay(monkeypatch, tmp_path):
    import database

    monkeypatch.setattr(database, "DB_PATH", str(tmp_path / "relay.db"))
    manager.active_sessions.clear()
    manager.set_active_session(
        ActiveSession("draft1", "phone", "com.linkedin.android", "john", "interaction", time.time() + 600)
    )
    sent = AsyncMock(return_value=MobileCommandResult(command_id="c", status="success"))
    monkeypatch.setattr(manager, "send_command_to_device", sent)
    with TestClient(app) as client:
        yield client, sent
    manager.active_sessions.clear()


def call(client, args):
    return client.post(
        "/mcp",
        headers={"Authorization": "Bearer token_for_john"},
        json={
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {"name": "mobile_linkedin_draft", "arguments": args},
        },
    ).json()["result"]


def test_draft_is_sent_as_a_path_not_as_image_bytes_and_closes_control(relay):
    client, sent = relay
    result = call(client, {"image_path": "/workspace/photo.png", "text": "Mon texte"})
    assert not result.get("isError")
    command = sent.call_args.args[1]
    assert command.operation == "linkedin_draft"
    assert command.arguments.image_path == "/workspace/photo.png"
    assert command.arguments.text == "Mon texte"
    assert manager.active_sessions == {}
    assert "brouillon" in result["content"][0]["text"].lower()


@pytest.mark.parametrize(
    "args",
    [
        {},
        {"image_path": "https://evil.test/image.png"},
        {"image_path": "../image.png"},
        {"image_path": "/workspace/image.png", "text": "x" * 3001},
        {"image_path": 23},
    ],
)
def test_invalid_input_never_reaches_phone(relay, args):
    client, sent = relay
    result = call(client, args)
    assert result["isError"] and "INVALID_ARGUMENTS" in result["content"][0]["text"]
    sent.assert_not_called()


def test_draft_requires_linkedin_as_the_target(relay):
    client, sent = relay
    manager.active_sessions["phone"].target_package = "com.android.settings"
    result = call(client, {"image_path": "/workspace/photo.png"})
    assert result["isError"] and "APP_NOT_ALLOWED" in result["content"][0]["text"]
    sent.assert_not_called()


def test_observation_mode_cannot_open_a_draft(relay):
    client, sent = relay
    manager.active_sessions["phone"].mode = "observation"
    result = call(client, {"image_path": "/workspace/photo.png"})
    assert result["isError"] and "MODE_DENIED" in result["content"][0]["text"]
    sent.assert_not_called()

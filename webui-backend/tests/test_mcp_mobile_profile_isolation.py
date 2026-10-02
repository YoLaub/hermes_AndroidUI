"""
Tests for MCP Mobile Relay Profile Scoping, Isolation, and Resilience.
Covers:
- Exposure of mobile relay tools to John.
- Strict absence of mobile tools for other profiles (Mario, Gaston, default).
- Non-contamination of secrets (e.g. MOBILE_CONTROL_TOKEN) and toolsets between profiles, including concurrency.
- Resilience when mobile relay is unavailable (server down, connection refused).
- Recovery and retry after an initial connection failure.
- Profile resolution via X-Hermes-Profile header and hermes_profile cookie.
"""

import logging
import os
import sys
import threading
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# Ensure webui-backend is in sys.path
_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from api.config import (
    _cfg_cache_by_path,
    _cfg_mtime_by_path,
    _resolve_cli_toolsets,
    get_config,
)
from api.helpers import get_profile_cookie
from api.profiles import clear_request_profile, get_active_profile_name, set_request_profile
from api.routes import _mcp_tools_from_registry
from api.streaming import _discover_profile_mcp_tools, _sanitize_mcp_error


class DummyHandler:
    def __init__(self, headers=None):
        self.headers = headers or {}


class TestProfileDetection:
    def test_x_hermes_profile_header(self):
        handler = DummyHandler({"X-Hermes-Profile": "john"})
        assert get_profile_cookie(handler) == "john"

    def test_cookie_profile_detection(self):
        handler = DummyHandler({"Cookie": "hermes_profile=mario; other=value"})
        assert get_profile_cookie(handler) == "mario"

    def test_header_takes_precedence_over_cookie(self):
        handler = DummyHandler({"X-Hermes-Profile": "john", "Cookie": "hermes_profile=mario"})
        assert get_profile_cookie(handler) == "john"

    def test_invalid_profile_name_rejected(self):
        handler = DummyHandler({"X-Hermes-Profile": "../traversal/bad"})
        assert get_profile_cookie(handler) is None


class TestToolsetResolutionAndIsolation:
    def test_john_gets_mobile_tools(self):
        john_cfg = {
            "mcp_servers": {
                "mcp-mobile": {
                    "url": "http://mobile-relay:8765/mcp",
                    "headers": {"Authorization": "Bearer ${MOBILE_CONTROL_TOKEN}"},
                }
            },
            "platform_toolsets": {
                "api_server": ["browser", "web", "mcp-mobile"]
            },
        }
        toolsets = _resolve_cli_toolsets(john_cfg, platform="webui")
        assert "mcp-mobile" in toolsets
        assert "browser" in toolsets
        assert "web" in toolsets

    def test_mario_gaston_default_do_not_get_mobile_tools(self):
        mario_cfg = {
            "platform_toolsets": {
                "cli": ["browser", "terminal", "file"]
            }
        }
        gaston_cfg = {
            "platform_toolsets": {
                "api_server": ["browser", "web"]
            }
        }
        default_cfg = {}

        assert "mcp-mobile" not in _resolve_cli_toolsets(mario_cfg, platform="webui")
        assert "mcp-mcp-mobile" not in _resolve_cli_toolsets(mario_cfg, platform="webui")

        assert "mcp-mobile" not in _resolve_cli_toolsets(gaston_cfg, platform="webui")
        assert "mcp-mcp-mobile" not in _resolve_cli_toolsets(gaston_cfg, platform="webui")

        assert "mcp-mobile" not in _resolve_cli_toolsets(default_cfg, platform="webui")
        assert "mcp-mcp-mobile" not in _resolve_cli_toolsets(default_cfg, platform="webui")

    def test_registry_tools_filtered_by_profile(self):
        # Even if mobile tools were registered in the shared tools.registry,
        # Mario must not see John's mobile tools in the MCP tools list.
        fake_registry = MagicMock()
        fake_registry.get_all_tool_names.return_value = [
            "mcp_mobile_mobile_control_status",
            "browser_open",
        ]

        def get_toolset(name):
            if name == "mcp_mobile_mobile_control_status":
                return "mcp-mcp-mobile"
            return "browser"

        fake_registry.get_toolset_for_tool.side_effect = get_toolset
        fake_registry.get_schema.return_value = {"type": "object"}

        # John's server summaries contain mcp-mobile
        john_summaries = {"mcp-mobile": {"name": "mcp-mobile", "enabled": True}}
        # Mario's server summaries contain no mobile server
        mario_summaries = {"other-tool": {"name": "other-tool", "enabled": True}}

        with patch.dict("sys.modules", {"tools.registry": MagicMock(registry=fake_registry)}):
            john_tools = _mcp_tools_from_registry(john_summaries)
            mario_tools = _mcp_tools_from_registry(mario_summaries)

            assert any(t["name"] == "mcp_mobile_mobile_control_status" for t in john_tools)
            assert not any(t["name"] == "mcp_mobile_mobile_control_status" for t in mario_tools)


class TestConfigAndSecretIsolation:
    def test_config_cache_concurrent_isolation(self, tmp_path):
        john_home = tmp_path / "profiles" / "john"
        mario_home = tmp_path / "profiles" / "mario"
        john_home.mkdir(parents=True)
        mario_home.mkdir(parents=True)

        (john_home / "config.yaml").write_text("agent:\n  name: JohnAgent\n", encoding="utf-8")
        (mario_home / "config.yaml").write_text("agent:\n  name: MarioAgent\n", encoding="utf-8")

        def mock_get_hermes_home(profile):
            if profile == "john":
                return john_home
            elif profile == "mario":
                return mario_home
            return tmp_path

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            cfg_john = get_config(profile="john")
            cfg_mario = get_config(profile="mario")

            assert cfg_john.get("agent", {}).get("name") == "JohnAgent"
            assert cfg_mario.get("agent", {}).get("name") == "MarioAgent"

            # Parallel access in threads
            results = {}

            def worker(p):
                c = get_config(profile=p)
                results[p] = c.get("agent", {}).get("name")

            t1 = threading.Thread(target=worker, args=("john",))
            t2 = threading.Thread(target=worker, args=("mario",))
            t1.start()
            t2.start()
            t1.join()
            t2.join()

            assert results["john"] == "JohnAgent"
            assert results["mario"] == "MarioAgent"

    def test_secrets_never_leak_globally_during_or_after_discovery(self, tmp_path):
        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n",
            encoding="utf-8"
        )

        john_env = {
            "MOBILE_CONTROL_TOKEN": "super_secret_john_token_12345",
            "OTHER_VAR": "public_value"
        }

        # Ensure token is not currently in os.environ
        assert "MOBILE_CONTROL_TOKEN" not in os.environ

        discovered = []

        def fake_discover():
            # During discovery under _ENV_LOCK, the env is available
            assert os.environ.get("MOBILE_CONTROL_TOKEN") == "super_secret_john_token_12345"
            discovered.append(True)

        fake_mcp = MagicMock(discover_mcp_tools=fake_discover, _servers={})

        def mock_get_hermes_home(profile):
            return john_home

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            with patch.dict("sys.modules", {"tools.mcp_tool": fake_mcp}):
                _discover_profile_mcp_tools("john", john_home, john_env)

        assert discovered == [True]
        # Crucial check: AFTER discovery finishes, MOBILE_CONTROL_TOKEN must NOT be in os.environ
        assert "MOBILE_CONTROL_TOKEN" not in os.environ


class TestResilienceAndRetry:
    def test_offline_mcp_server_does_not_crash_and_redacts_secrets(self, tmp_path, caplog):
        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n",
            encoding="utf-8"
        )

        secret_token = "secret_auth_token_9999"
        john_env = {"MOBILE_CONTROL_TOKEN": secret_token}

        def failing_discover():
            raise ConnectionRefusedError(f"Failed to connect to http://mobile-relay:8765/mcp with Bearer {secret_token}")

        fake_mcp = MagicMock(discover_mcp_tools=failing_discover, _servers={})

        def mock_get_hermes_home(profile):
            return john_home

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            with patch.dict("sys.modules", {"tools.mcp_tool": fake_mcp}):
                with caplog.at_level(logging.WARNING):
                    # Must not raise exception
                    _discover_profile_mcp_tools("john", john_home, john_env)

        # Check logs: profile mentioned, error mentioned, secret REDACTED
        assert "Profile 'john' MCP discovery failed" in caplog.text
        assert secret_token not in caplog.text
        assert "[REDACTED]" in caplog.text

    def test_retry_after_initial_failure_cleans_stale_server_and_succeeds(self, tmp_path):
        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n",
            encoding="utf-8"
        )

        attempts = 0
        stale_server = MagicMock(is_connected=lambda: False)
        servers_dict = {"mcp-mobile": stale_server}

        def discover():
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise ConnectionError("Temporary relay offline")
            # Attempt 2 succeeds and registers active server
            active_server = MagicMock(is_connected=lambda: True)
            servers_dict["mcp-mobile"] = active_server

        fake_mcp = MagicMock(discover_mcp_tools=discover, _servers=servers_dict)

        def mock_get_hermes_home(profile):
            return john_home

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            with patch.dict("sys.modules", {"tools.mcp_tool": fake_mcp}):
                # Attempt 1: fails gracefully
                _discover_profile_mcp_tools("john", john_home, {})
                assert attempts == 1

                # Stale server should have been purged from _servers
                assert servers_dict.get("mcp-mobile") is None

                # Attempt 2: succeeds on next turn/request
                _discover_profile_mcp_tools("john", john_home, {})
                assert attempts == 2
                assert servers_dict["mcp-mobile"].is_connected() is True

    def test_concurrent_sessions_profile_and_secret_isolation(self, tmp_path):
        john_home = tmp_path / "profiles" / "john"
        mario_home = tmp_path / "profiles" / "mario"
        john_home.mkdir(parents=True)
        mario_home.mkdir(parents=True)

        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n"
            "platform_toolsets:\n  api_server: [browser, web, mcp-mobile]\n",
            encoding="utf-8"
        )
        (mario_home / "config.yaml").write_text(
            "platform_toolsets:\n  cli: [browser, terminal, file]\n",
            encoding="utf-8"
        )

        john_env = {"MOBILE_CONTROL_TOKEN": "token_john_xyz"}
        mario_env = {"SOME_MARIO_VAR": "mario_public"}

        errors = []
        observed_toolsets = {}

        def mock_get_hermes_home(profile):
            if profile == "john":
                return john_home
            elif profile == "mario":
                return mario_home
            return tmp_path

        fake_mcp = MagicMock(discover_mcp_tools=lambda: None, _servers={})

        def run_profile_flow(p_name, p_home, p_env):
            try:
                # Set thread-local profile as worker thread does
                set_request_profile(p_name)
                assert get_active_profile_name() == p_name

                # Discover MCP tools
                _discover_profile_mcp_tools(p_name, p_home, p_env)

                # Resolve config and toolsets
                cfg = get_config(p_name)
                ts = _resolve_cli_toolsets(cfg, platform="webui")
                observed_toolsets[p_name] = ts

                # Ensure token does NOT leak into Mario's view
                if p_name == "mario":
                    if os.environ.get("MOBILE_CONTROL_TOKEN") is not None:
                        errors.append("Mario observed MOBILE_CONTROL_TOKEN in os.environ!")
            except Exception as e:
                errors.append(f"{p_name} error: {e}")
            finally:
                clear_request_profile()

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            with patch.dict("sys.modules", {"tools.mcp_tool": fake_mcp}):
                threads = [
                    threading.Thread(target=run_profile_flow, args=("john", john_home, john_env)),
                    threading.Thread(target=run_profile_flow, args=("mario", mario_home, mario_env)),
                ]
                for t in threads:
                    t.start()
                for t in threads:
                    t.join()

        assert not errors, f"Errors encountered: {errors}"
        assert "mcp-mobile" in observed_toolsets["john"]
        assert "mcp-mobile" not in observed_toolsets["mario"]
        assert "MOBILE_CONTROL_TOKEN" not in os.environ

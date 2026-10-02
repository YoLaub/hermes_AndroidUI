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
            # Crucial: Secret tokens are NEVER injected into os.environ of the shared WebUI process
            assert os.environ.get("MOBILE_CONTROL_TOKEN") is None
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


class TestConcurrentSameServerNameDifferentCredentials:
    def test_two_profiles_same_server_name_different_credentials(self, tmp_path):
        """Two concurrent profiles have a server of the SAME name but with DIFFERENT credentials.

        Neither profile's credentials or connection object should stomp on the other,
        and no secrets should enter os.environ.
        """
        john_home = tmp_path / "profiles" / "john"
        alice_home = tmp_path / "profiles" / "alice"
        john_home.mkdir(parents=True)
        alice_home.mkdir(parents=True)

        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  custom-server:\n    url: http://relay1:8000/mcp\n    headers:\n      Authorization: Bearer john_secret_token_111\n",
            encoding="utf-8"
        )
        (alice_home / "config.yaml").write_text(
            "mcp_servers:\n  custom-server:\n    url: http://relay2:9000/mcp\n    headers:\n      Authorization: Bearer alice_secret_token_222\n",
            encoding="utf-8"
        )

        john_env = {"SECRET_TOKEN": "john_secret_token_111"}
        alice_env = {"SECRET_TOKEN": "alice_secret_token_222"}

        from api.mcp_isolated import ProfileAwareMCPServers
        servers = ProfileAwareMCPServers()

        srv_john = MagicMock(name="ServerJohn", token="john_secret_token_111", url="http://relay1:8000/mcp")
        srv_alice = MagicMock(name="ServerAlice", token="alice_secret_token_222", url="http://relay2:9000/mcp")

        results = {}
        errors = []

        def worker(p_name, srv_obj, env_dict):
            try:
                set_request_profile(p_name)
                # Store server connection in profile-aware dictionary under SAME key
                servers["custom-server"] = srv_obj
                # Sleep briefly to ensure concurrent interleave
                import time; time.sleep(0.01)
                # Read back
                retrieved = servers.get("custom-server")
                results[p_name] = retrieved
                # Verify os.environ never contains the secret token
                for val in env_dict.values():
                    if val in str(os.environ):
                        errors.append(f"{p_name} secret leaked into os.environ!")
            except Exception as e:
                errors.append(f"{p_name} error: {e}")
            finally:
                clear_request_profile()

        t1 = threading.Thread(target=worker, args=("john", srv_john, john_env))
        t2 = threading.Thread(target=worker, args=("alice", srv_alice, alice_env))
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        assert not errors, f"Errors: {errors}"
        assert results["john"] is srv_john
        assert results["john"].token == "john_secret_token_111"
        assert results["alice"] is srv_alice
        assert results["alice"].token == "alice_secret_token_222"
        assert "john_secret_token_111" not in os.environ
        assert "alice_secret_token_222" not in os.environ


class TestGastonMarioMobileExclusion:
    def test_gaston_mario_default_strict_exclusion(self, tmp_path):
        """Ensure Gaston, Mario, and Default have NO mobile tools or servers."""
        from api.routes import _handle_mcp_servers_list, _handle_mcp_tools_list
        import json

        john_home = tmp_path / "profiles" / "john"
        gaston_home = tmp_path / "profiles" / "gaston"
        mario_home = tmp_path / "profiles" / "mario"
        default_home = tmp_path / "default"
        for p in (john_home, gaston_home, mario_home, default_home):
            p.mkdir(parents=True)

        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n"
            "platform_toolsets:\n  api_server: [browser, web, mcp-mobile]\n",
            encoding="utf-8"
        )
        (gaston_home / "config.yaml").write_text(
            "platform_toolsets:\n  api_server: [browser, web]\n",
            encoding="utf-8"
        )
        (mario_home / "config.yaml").write_text(
            "platform_toolsets:\n  cli: [browser, terminal, file]\n",
            encoding="utf-8"
        )
        (default_home / "config.yaml").write_text("agent:\n  name: DefaultAgent\n", encoding="utf-8")

        def mock_get_hermes_home(profile):
            if profile == "john":
                return john_home
            elif profile == "gaston":
                return gaston_home
            elif profile == "mario":
                return mario_home
            return default_home

        class RecordingHandler(DummyHandler):
            def __init__(self, headers=None):
                super().__init__(headers)
                self.sent_json = None

            def send_response(self, code):
                self.status_code = code

            def send_header(self, k, v):
                pass

            def end_headers(self):
                pass

            @property
            def wfile(self):
                mock_w = MagicMock()
                def write_bytes(data):
                    try:
                        self.sent_json = json.loads(data.decode("utf-8"))
                    except Exception:
                        pass
                mock_w.write.side_effect = write_bytes
                return mock_w

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            # Test Mario: 0 servers, 0 tools
            h_mario = RecordingHandler({"X-Hermes-Profile": "mario"})
            set_request_profile("mario")
            try:
                _handle_mcp_servers_list(h_mario)
                assert len(h_mario.sent_json["servers"]) == 0
                _handle_mcp_tools_list(h_mario)
                assert len(h_mario.sent_json["tools"]) == 0
            finally:
                clear_request_profile()

            # Test Gaston: 0 servers, 0 tools
            h_gaston = RecordingHandler({"X-Hermes-Profile": "gaston"})
            set_request_profile("gaston")
            try:
                _handle_mcp_servers_list(h_gaston)
                assert len(h_gaston.sent_json["servers"]) == 0
                _handle_mcp_tools_list(h_gaston)
                assert len(h_gaston.sent_json["tools"]) == 0
            finally:
                clear_request_profile()

            # Test John: 1 mobile server
            h_john = RecordingHandler({"X-Hermes-Profile": "john"})
            set_request_profile("john")
            try:
                _handle_mcp_servers_list(h_john)
                assert len(h_john.sent_json["servers"]) == 1
                assert h_john.sent_json["servers"][0]["name"] == "mcp-mobile"
            finally:
                clear_request_profile()


class TestMcpSaveParameterPreservationAndTrueDiscovery:
    def test_mcp_server_save_preserves_all_parameters(self, tmp_path):
        from api.routes import _handle_mcp_server_update
        import json

        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        initial_yaml = (
            "mcp_servers:\n"
            "  test-server:\n"
            "    url: http://example.internal/mcp\n"
            "    headers:\n"
            "      Authorization: Bearer secret_live_token_777\n"
            "    timeout: 90\n"
            "    connect_timeout: 30\n"
            "    enabled: true\n"
            "    description: Custom Mobile Relay\n"
        )
        (john_home / "config.yaml").write_text(initial_yaml, encoding="utf-8")

        def mock_get_hermes_home(profile):
            return john_home

        class RecordingHandler(DummyHandler):
            def __init__(self, headers=None):
                super().__init__(headers)
                self.sent_json = None

            def send_response(self, code):
                self.status_code = code

            def send_header(self, k, v):
                pass

            def end_headers(self):
                pass

            @property
            def wfile(self):
                mock_w = MagicMock()
                def write_bytes(data):
                    try:
                        self.sent_json = json.loads(data.decode("utf-8"))
                    except Exception:
                        pass
                mock_w.write.side_effect = write_bytes
                return mock_w

        handler = RecordingHandler({"X-Hermes-Profile": "john"})

        # Submit update with masked placeholder "••••••" and without timeout/connect_timeout
        update_body = {
            "name": "test-server",
            "url": "http://updated.internal/mcp",
            "headers": {
                "Authorization": "Bearer ••••••"
            }
        }

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            set_request_profile("john")
            try:
                _handle_mcp_server_update(handler, "test-server", update_body)
            finally:
                clear_request_profile()

        assert handler.sent_json["ok"] is True
        saved_server = handler.sent_json["server"]
        assert saved_server["name"] == "test-server"
        assert saved_server["url"] == "http://updated.internal/mcp"
        assert saved_server["timeout"] == 90
        assert saved_server["connect_timeout"] == 30
        assert saved_server["enabled"] is True

        import yaml
        saved_disk_cfg = yaml.safe_load((john_home / "config.yaml").read_text(encoding="utf-8"))
        srv_disk = saved_disk_cfg["mcp_servers"]["test-server"]
        assert srv_disk["url"] == "http://updated.internal/mcp"
        assert srv_disk["headers"]["Authorization"] == "Bearer secret_live_token_777"
        assert srv_disk["timeout"] == 90
        assert srv_disk["connect_timeout"] == 30
        assert srv_disk["enabled"] is True
        assert srv_disk["description"] == "Custom Mobile Relay"

    def test_real_discovery_results_reporting(self, tmp_path):
        from api.mcp_isolated import set_profile_discovery_inventory
        from api.routes import _handle_mcp_tools_list
        import json

        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        (john_home / "config.yaml").write_text(
            "mcp_servers:\n  mcp-mobile:\n    url: http://mobile-relay:8765/mcp\n",
            encoding="utf-8"
        )

        def mock_get_hermes_home(profile):
            return john_home

        real_tools = [
            {
                "name": "mcp_mobile_take_screenshot",
                "server": "mcp-mobile",
                "description": "Capture Android screen",
                "schema": {"properties": {"quality": {"type": "integer"}}},
            },
            {
                "name": "mcp_mobile_tap_element",
                "server": "mcp-mobile",
                "description": "Tap on UI coordinate",
                "schema": {"properties": {"x": {"type": "integer"}, "y": {"type": "integer"}}},
            }
        ]
        real_servers = {
            "mcp-mobile": {
                "name": "mcp-mobile",
                "transport": "http",
                "status": "active",
                "active": True,
                "enabled": True,
                "tools": 2
            }
        }
        set_profile_discovery_inventory("john", real_tools, real_servers)

        class RecordingHandler(DummyHandler):
            def __init__(self, headers=None):
                super().__init__(headers)
                self.sent_json = None

            def send_response(self, code):
                self.status_code = code

            def send_header(self, k, v):
                pass

            def end_headers(self):
                pass

            @property
            def wfile(self):
                mock_w = MagicMock()
                def write_bytes(data):
                    try:
                        self.sent_json = json.loads(data.decode("utf-8"))
                    except Exception:
                        pass
                mock_w.write.side_effect = write_bytes
                return mock_w

        handler = RecordingHandler({"X-Hermes-Profile": "john"})

        with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
            set_request_profile("john")
            try:
                _handle_mcp_tools_list(handler)
            finally:
                clear_request_profile()

        assert handler.sent_json["total"] == 2
        assert handler.sent_json["source"] == "profile_discovery"
        tool_names = [t["name"] for t in handler.sent_json["tools"]]
        assert "mcp_mobile_take_screenshot" in tool_names
        assert "mcp_mobile_tap_element" in tool_names


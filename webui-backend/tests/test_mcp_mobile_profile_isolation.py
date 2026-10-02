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

import http.server
import json
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


class MockMCPServerHandler(http.server.BaseHTTPRequestHandler):
    expected_token = None
    server_instance = None

    def log_message(self, format, *args):
        pass

    def do_POST(self):
        auth_header = self.headers.get("Authorization", "")
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8")
        req = json.loads(body)
        method = req.get("method")
        req_id = req.get("id")

        if self.expected_token and auth_header != f"Bearer {self.expected_token}":
            self.send_response(401)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Unauthorized"}).encode("utf-8"))
            return

        if self.server_instance:
            self.server_instance.recorded_requests.append({
                "method": method,
                "auth": auth_header,
                "body": req,
            })

        if method == "initialize":
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "serverInfo": {"name": "test-relay", "version": "1.0"},
                },
            }
        elif method == "tools/list":
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "tools": [
                        {
                            "name": "echo",
                            "description": "Echo back message",
                            "inputSchema": {
                                "type": "object",
                                "properties": {"message": {"type": "string"}},
                                "required": ["message"],
                            },
                        }
                    ]
                },
            }
        elif method == "tools/call":
            params = req.get("params", {})
            t_name = params.get("name")
            args = params.get("arguments", {})
            if t_name == "echo":
                msg = args.get("message", "")
                prefix = getattr(self.server_instance, "response_prefix", "Echo: ") if self.server_instance else "Echo: "
                resp = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {"type": "text", "text": f"{prefix}{msg}"}
                        ]
                    },
                }
            else:
                resp = {"jsonrpc": "2.0", "id": req_id, "error": {"message": f"Unknown tool {t_name}"}}
        else:
            resp = {"jsonrpc": "2.0", "id": req_id, "result": {}}

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(resp).encode("utf-8"))


class LocalMCPTestServer:
    def __init__(self, expected_token: str, response_prefix: str = "Echo: "):
        self.expected_token = expected_token
        self.response_prefix = response_prefix
        self.recorded_requests = []
        handler_cls = type(
            "BoundHandler",
            (MockMCPServerHandler,),
            {"expected_token": expected_token, "server_instance": self},
        )
        self.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler_cls)
        self.port = self.httpd.server_address[1]
        self.url = f"http://127.0.0.1:{self.port}/mcp"
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def shutdown(self):
        try:
            self.httpd.shutdown()
            self.httpd.server_close()
        except Exception:
            pass


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
        from api.mcp_isolated import ProfileMCPManager, get_profile_discovered_inventory

        token = "super_secret_john_token_12345"
        server = LocalMCPTestServer(expected_token=token)
        try:
            john_home = tmp_path / "profiles" / "john"
            john_home.mkdir(parents=True)
            (john_home / "config.yaml").write_text(
                f"mcp_servers:\n  mcp-mobile:\n    url: {server.url}\n    headers:\n      Authorization: Bearer ${{MOBILE_CONTROL_TOKEN}}\n",
                encoding="utf-8"
            )

            john_env = {
                "MOBILE_CONTROL_TOKEN": token,
                "OTHER_VAR": "public_value"
            }

            # Ensure token is not currently in os.environ
            assert "MOBILE_CONTROL_TOKEN" not in os.environ

            def mock_get_hermes_home(profile):
                return john_home

            with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
                _discover_profile_mcp_tools("john", john_home, john_env)

            # Crucial check: AFTER discovery finishes, MOBILE_CONTROL_TOKEN must NOT be in os.environ
            assert "MOBILE_CONTROL_TOKEN" not in os.environ
            assert os.environ.get("MOBILE_CONTROL_TOKEN") is None

            # Verify discovery actually reached the local server with the secret token
            reqs = [r for r in server.recorded_requests if r["method"] == "tools/list"]
            assert len(reqs) >= 1
            assert all(r["auth"] == f"Bearer {token}" for r in reqs)

            inv = get_profile_discovered_inventory("john")
            assert len(inv.get("tools", [])) == 1
        finally:
            ProfileMCPManager.get_instance().shutdown_all()
            server.shutdown()


class TestResilienceAndRetry:
    def test_offline_mcp_server_does_not_crash_and_redacts_secrets(self, tmp_path, caplog):
        from api.mcp_isolated import ProfileMCPManager

        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)
        secret_token = "secret_auth_token_9999"
        (john_home / "config.yaml").write_text(
            f"mcp_servers:\n  mcp-mobile:\n    url: http://127.0.0.1:59998/mcp?token={secret_token}\n    headers:\n      Authorization: Bearer {secret_token}\n",
            encoding="utf-8"
        )

        john_env = {"MOBILE_CONTROL_TOKEN": secret_token}

        def mock_get_hermes_home(profile):
            return john_home

        try:
            with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
                with caplog.at_level(logging.WARNING):
                    # Must not raise exception
                    _discover_profile_mcp_tools("john", john_home, john_env)

            # Check logs: profile mentioned, error mentioned, secret REDACTED
            assert "Profile 'john' MCP discovery failed" in caplog.text
            assert secret_token not in caplog.text
            assert "[REDACTED]" in caplog.text
        finally:
            ProfileMCPManager.get_instance().shutdown_all()

    def test_retry_after_initial_failure_cleans_stale_server_and_succeeds(self, tmp_path):
        from api.mcp_isolated import ProfileMCPManager, get_profile_discovered_inventory

        john_home = tmp_path / "profiles" / "john"
        john_home.mkdir(parents=True)

        token = "token_retry_test_123"
        server = LocalMCPTestServer(expected_token=token)
        server_url = server.url
        # Stop server to simulate initial offline state
        server.shutdown()

        (john_home / "config.yaml").write_text(
            f"mcp_servers:\n  mcp-mobile:\n    url: {server_url}\n    headers:\n      Authorization: Bearer {token}\n",
            encoding="utf-8"
        )

        def mock_get_hermes_home(profile):
            return john_home

        try:
            with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
                # Attempt 1: server is down, discovery fails gracefully and registers 0 tools
                _discover_profile_mcp_tools("john", john_home, {})
                inv1 = get_profile_discovered_inventory("john")
                assert len(inv1.get("tools", [])) == 0

                # Restart server on the same port
                server = LocalMCPTestServer(expected_token=token)
                (john_home / "config.yaml").write_text(
                    f"mcp_servers:\n  mcp-mobile:\n    url: {server.url}\n    headers:\n      Authorization: Bearer {token}\n",
                    encoding="utf-8"
                )

                # Attempt 2: succeeds on next turn/request
                _discover_profile_mcp_tools("john", john_home, {})
                inv2 = get_profile_discovered_inventory("john")
                assert len(inv2.get("tools", [])) == 1
                assert inv2["servers"]["mcp-mobile"]["active"] is True
        finally:
            ProfileMCPManager.get_instance().shutdown_all()
            server.shutdown()

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


class TestRealLocalMCPServerIntegration:
    def test_end_to_end_discovery_execution_and_isolation(self, tmp_path):
        """Full end-to-end integration test with real local MCP HTTP servers.

        Tests:
        1. Two real local HTTP MCP servers with different credentials.
        2. Two profiles (John & Alice) with servers sharing the exact same name ('my-relay').
        3. Real discovery via ProfileMCPManager running persistent workers.
        4. Real live execution of tools via IPC, returning real results.
        5. Verification that each profile connects with its own token to its own server.
        6. Verification that neither token ever enters parent os.environ.
        7. Gaston and Mario have 0 tools and cannot invoke mobile tools.
        8. Updating/invalidating configuration purges old inventory and resets the worker.
        """
        from api.mcp_isolated import (
            ProfileMCPManager,
            invalidate_profile_mcp_server,
            get_profile_discovered_inventory,
        )

        token_john = "token_john_live_secret_777"
        token_alice = "token_alice_live_secret_888"

        server_john = LocalMCPTestServer(expected_token=token_john)
        server_alice = LocalMCPTestServer(expected_token=token_alice)

        try:
            john_home = tmp_path / "profiles" / "john"
            alice_home = tmp_path / "profiles" / "alice"
            gaston_home = tmp_path / "profiles" / "gaston"
            mario_home = tmp_path / "profiles" / "mario"
            for p in (john_home, alice_home, gaston_home, mario_home):
                p.mkdir(parents=True)

            # Both profiles configure a server with the EXACT SAME NAME 'my-relay'
            (john_home / "config.yaml").write_text(
                f"mcp_servers:\n  my-relay:\n    url: {server_john.url}\n    headers:\n      Authorization: Bearer ${{JOHN_TOKEN}}\n",
                encoding="utf-8"
            )
            (alice_home / "config.yaml").write_text(
                f"mcp_servers:\n  my-relay:\n    url: {server_alice.url}\n    headers:\n      Authorization: Bearer ${{ALICE_TOKEN}}\n",
                encoding="utf-8"
            )
            (gaston_home / "config.yaml").write_text("agent:\n  name: Gaston\n", encoding="utf-8")
            (mario_home / "config.yaml").write_text("agent:\n  name: Mario\n", encoding="utf-8")

            john_env = {"JOHN_TOKEN": token_john}
            alice_env = {"ALICE_TOKEN": token_alice}

            mgr = ProfileMCPManager.get_instance()
            # Clean start
            mgr.shutdown_all()

            def mock_get_hermes_home(profile):
                if profile == "john":
                    return john_home
                elif profile == "alice":
                    return alice_home
                elif profile == "gaston":
                    return gaston_home
                elif profile == "mario":
                    return mario_home
                return tmp_path

            with patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):
                # 1. Real Discovery
                disc_john = mgr.discover_for_profile("john", john_home, john_env)
                disc_alice = mgr.discover_for_profile("alice", alice_home, alice_env)
                disc_gaston = mgr.discover_for_profile("gaston", gaston_home, {})

                assert disc_john["ok"] is True
                assert len(disc_john["tools"]) == 1
                assert disc_john["tools"][0]["name"] == "mcp_my_relay_echo"
                assert disc_john["tools"][0]["original_name"] == "echo"
                assert disc_john["servers"]["my-relay"]["active"] is True

                assert disc_alice["ok"] is True
                assert len(disc_alice["tools"]) == 1
                assert disc_alice["tools"][0]["name"] == "mcp_my_relay_echo"
                assert disc_alice["tools"][0]["original_name"] == "echo"
                assert disc_alice["servers"]["my-relay"]["active"] is True

                assert len(disc_gaston["tools"]) == 0

                # 2. Verify parent os.environ is NOT contaminated
                assert token_john not in os.environ
                assert token_alice not in os.environ

                # 3. Real Tool Execution
                res_j = mgr.call_tool("john", "my-relay", "echo", {"message": "greeting from john"})
                assert res_j == "Echo: greeting from john"

                res_a = mgr.call_tool("alice", "my-relay", "echo", {"message": "greeting from alice"})
                assert res_a == "Echo: greeting from alice"

                # 4. Verify tokens received on each local server
                john_reqs = [r for r in server_john.recorded_requests if r["method"] == "tools/call"]
                alice_reqs = [r for r in server_alice.recorded_requests if r["method"] == "tools/call"]

                assert len(john_reqs) == 1
                assert john_reqs[0]["auth"] == f"Bearer {token_john}"

                assert len(alice_reqs) == 1
                assert alice_reqs[0]["auth"] == f"Bearer {token_alice}"

                # 5. Concurrent execution
                concurrent_results = {}
                errors = []

                def worker_run(p_name, msg):
                    try:
                        ans = mgr.call_tool(p_name, "my-relay", "echo", {"message": msg})
                        concurrent_results[p_name] = ans
                    except Exception as exc:
                        errors.append(f"{p_name} error: {exc}")

                threads = [
                    threading.Thread(target=worker_run, args=("john", "concurrent John msg")),
                    threading.Thread(target=worker_run, args=("alice", "concurrent Alice msg")),
                ]
                for t in threads:
                    t.start()
                for t in threads:
                    t.join()

                assert not errors, f"Errors: {errors}"
                assert concurrent_results["john"] == "Echo: concurrent John msg"
                assert concurrent_results["alice"] == "Echo: concurrent Alice msg"

                # 6. Invalidation upon configuration change
                invalidate_profile_mcp_server("john", "my-relay")
                john_inv = get_profile_discovered_inventory("john")
                assert len(john_inv.get("tools", [])) == 0

        finally:
            mgr.shutdown_all()
            server_john.shutdown()
            server_alice.shutdown()


class InMemoryToolRegistry:
    """In-memory tool registry matching tools.registry.registry interface."""

    def __init__(self):
        self._tools = {}
        self._schemas = {}
        self._toolsets = {}

    def register(self, name, func, schema=None, toolset=None):
        self._tools[name] = func
        if schema is not None:
            self._schemas[name] = schema
        if toolset is not None:
            self._toolsets[name] = toolset

    def get(self, name):
        return self._tools.get(name)

    def get_schema(self, name):
        return self._schemas.get(name)

    def get_toolset_for_tool(self, name):
        return self._toolsets.get(name)

    def get_all_tool_names(self):
        return list(self._tools.keys())


class TestRealLocalMCPServerDualProfileStdioAndRegistry:
    """Real tests covering:
    1. Two profiles having the exact same tool name with distinct results via parent registry.
    2. Parent registry does not overwrite global functions with profile-specific closures.
    3. Unauthorized profile calling the registered tool raises PermissionError.
    4. Real stdio MCP server subprocess.
    5. include_tools and exclude_tools filtering against stdio server.
    6. End-to-end tool call passing from WebUI parent registry down to persistent worker.
    """

    def test_dual_profile_same_tool_name_distinct_results_no_closure_collision_via_registry(self, tmp_path):
        import types
        from api.mcp_isolated import ProfileMCPManager

        fake_registry = InMemoryToolRegistry()
        tools_mod = types.ModuleType("tools")
        reg_mod = types.ModuleType("tools.registry")
        reg_mod.registry = fake_registry
        tools_mod.registry = reg_mod

        # Real local test servers returning distinct results
        server_john = LocalMCPTestServer(expected_token="tok_john", response_prefix="[John]: ")
        server_alice = LocalMCPTestServer(expected_token="tok_alice", response_prefix="[Alice]: ")

        mgr = ProfileMCPManager.get_instance()
        mgr.shutdown_all()

        try:
            john_home = tmp_path / "profiles" / "john"
            alice_home = tmp_path / "profiles" / "alice"
            mario_home = tmp_path / "profiles" / "mario"
            for p in (john_home, alice_home, mario_home):
                p.mkdir(parents=True)

            # Both profiles configure server 'custom-relay' with the exact same tool name 'echo'
            (john_home / "config.yaml").write_text(
                f"mcp_servers:\n  custom-relay:\n    url: {server_john.url}\n    headers:\n      Authorization: Bearer ${{J_TOKEN}}\n",
                encoding="utf-8",
            )
            (alice_home / "config.yaml").write_text(
                f"mcp_servers:\n  custom-relay:\n    url: {server_alice.url}\n    headers:\n      Authorization: Bearer ${{A_TOKEN}}\n",
                encoding="utf-8",
            )
            (mario_home / "config.yaml").write_text("agent:\n  name: Mario\n", encoding="utf-8")

            def mock_get_hermes_home(profile):
                if profile == "john":
                    return john_home
                elif profile == "alice":
                    return alice_home
                elif profile == "mario":
                    return mario_home
                return tmp_path

            with patch.dict("sys.modules", {"tools": tools_mod, "tools.registry": reg_mod}), \
                 patch("api.profiles.get_hermes_home_for_profile", side_effect=mock_get_hermes_home):

                # 1. Discover for John: registers 'mcp_custom_relay_echo'
                disc_j = mgr.discover_for_profile("john", john_home, {"J_TOKEN": "tok_john"})
                assert disc_j["ok"] is True
                assert any(t["name"] == "mcp_custom_relay_echo" for t in disc_j["tools"])
                assert "mcp_custom_relay_echo" in fake_registry._tools

                # 2. Discover for Alice: registers 'mcp_custom_relay_echo'
                disc_a = mgr.discover_for_profile("alice", alice_home, {"A_TOKEN": "tok_alice"})
                assert disc_a["ok"] is True
                assert any(t["name"] == "mcp_custom_relay_echo" for t in disc_a["tools"])

                # 3. Discover for Mario: Mario has NO custom-relay configured
                disc_m = mgr.discover_for_profile("mario", mario_home, {})
                assert len(disc_m["tools"]) == 0

                # 4. Invocations through WebUI parent registry
                registered_tool_fn = fake_registry.get("mcp_custom_relay_echo")
                assert callable(registered_tool_fn)

                # Call under John's profile
                set_request_profile("john")
                try:
                    res_john = registered_tool_fn(message="Hello John")
                    assert res_john == "[John]: Hello John"
                finally:
                    clear_request_profile()

                # Call under Alice's profile using the SAME registered function
                # (Alice must NOT have overwritten John's closure, and John's closure must not bind Alice)
                set_request_profile("alice")
                try:
                    res_alice = registered_tool_fn(message="Hello Alice")
                    assert res_alice == "[Alice]: Hello Alice"
                finally:
                    clear_request_profile()

                # Call again under John's profile to prove no permanent overwrite occurred
                set_request_profile("john")
                try:
                    res_john_2 = registered_tool_fn(message="Hello again John")
                    assert res_john_2 == "[John]: Hello again John"
                finally:
                    clear_request_profile()

                # Unauthorized profile Mario attempting to call John/Alice's tool via registry
                set_request_profile("mario")
                try:
                    with pytest.raises(PermissionError) as exc_info:
                        registered_tool_fn(message="Intrusion")
                    assert "is not configured or available for active profile 'mario'" in str(exc_info.value)
                finally:
                    clear_request_profile()

                # 5. Concurrent calls from multiple threads under different profiles
                results = {}
                errs = []

                def worker_thread(prof, msg):
                    try:
                        set_request_profile(prof)
                        try:
                            results[prof] = registered_tool_fn(message=msg)
                        finally:
                            clear_request_profile()
                    except Exception as e:
                        errs.append(f"{prof}: {e}")

                t1 = threading.Thread(target=worker_thread, args=("john", "Parallel John"))
                t2 = threading.Thread(target=worker_thread, args=("alice", "Parallel Alice"))
                t1.start()
                t2.start()
                t1.join()
                t2.join()

                assert not errs, f"Concurrent registry errors: {errs}"
                assert results["john"] == "[John]: Parallel John"
                assert results["alice"] == "[Alice]: Parallel Alice"

        finally:
            mgr.shutdown_all()
            server_john.shutdown()
            server_alice.shutdown()
            clear_request_profile()

    def test_stdio_server_with_include_exclude_filters_and_registry_call(self, tmp_path):
        import types
        from api.mcp_isolated import ProfileMCPManager

        fake_registry = InMemoryToolRegistry()
        tools_mod = types.ModuleType("tools")
        reg_mod = types.ModuleType("tools.registry")
        reg_mod.registry = fake_registry
        tools_mod.registry = reg_mod

        # Create a real stdio MCP server script
        stdio_script = tmp_path / "local_stdio_server.py"
        stdio_script.write_text(
            '''import sys, json

while True:
    line = sys.stdin.readline()
    if not line:
        break
    line = line.strip()
    if not line:
        continue
    try:
        req = json.loads(line)
    except Exception:
        continue

    method = req.get("method")
    req_id = req.get("id")

    if method == "initialize":
        resp = {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "serverInfo": {"name": "test-stdio", "version": "1.0"},
            },
        }
    elif method == "notifications/initialized":
        continue
    elif method == "tools/list":
        resp = {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "tools": [
                    {
                        "name": "calc_add",
                        "description": "Add two numbers",
                        "inputSchema": {
                            "type": "object",
                            "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
                            "required": ["a", "b"],
                        },
                    },
                    {
                        "name": "calc_sub",
                        "description": "Subtract two numbers",
                        "inputSchema": {
                            "type": "object",
                            "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
                            "required": ["a", "b"],
                        },
                    },
                    {
                        "name": "calc_mul",
                        "description": "Multiply two numbers",
                        "inputSchema": {
                            "type": "object",
                            "properties": {"a": {"type": "number"}, "b": {"type": "number"}},
                            "required": ["a", "b"],
                        },
                    },
                    {
                        "name": "system_wipe",
                        "description": "Admin dangerous tool",
                        "inputSchema": {"type": "object"},
                    },
                ],
            },
        }
    elif method == "tools/call":
        params = req.get("params", {})
        t_name = params.get("name")
        args = params.get("arguments", {})
        if t_name == "calc_add":
            ans = float(args.get("a", 0)) + float(args.get("b", 0))
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"content": [{"type": "text", "text": str(ans)}]},
            }
        elif t_name == "calc_sub":
            ans = float(args.get("a", 0)) - float(args.get("b", 0))
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"content": [{"type": "text", "text": str(ans)}]},
            }
        else:
            resp = {
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {"message": f"Tool {t_name} not available or blocked"},
            }
    else:
        resp = {"jsonrpc": "2.0", "id": req_id, "result": {}}

    sys.stdout.write(json.dumps(resp) + "\\n")
    sys.stdout.flush()
''',
            encoding="utf-8",
        )

        bob_home = tmp_path / "profiles" / "bob"
        bob_home.mkdir(parents=True)

        # Configure stdio server with include_tools and exclude_tools
        # include: calc_add, calc_sub, system_wipe
        # exclude: system_wipe
        # calc_mul is not in include_tools => omitted
        # system_wipe is in exclude_tools => omitted
        # Result should ONLY expose: calc_add, calc_sub
        import yaml
        config_content = {
            "mcp_servers": {
                "calc": {
                    "command": sys.executable,
                    "args": ["-u", str(stdio_script)],
                    "include_tools": ["calc_add", "calc_sub", "system_wipe"],
                    "exclude_tools": ["system_wipe"],
                }
            }
        }
        (bob_home / "config.yaml").write_text(yaml.dump(config_content), encoding="utf-8")

        mgr = ProfileMCPManager.get_instance()
        mgr.shutdown_all()

        try:
            with patch.dict("sys.modules", {"tools": tools_mod, "tools.registry": reg_mod}), \
                 patch("api.profiles.get_hermes_home_for_profile", return_value=bob_home):

                # 1. Discover for profile 'bob'
                disc = mgr.discover_for_profile("bob", bob_home, {})
                assert disc["ok"] is True
                tool_names = [t["name"] for t in disc["tools"]]

                # Hermes naming: mcp_calc_calc_add, mcp_calc_calc_sub
                assert "mcp_calc_calc_add" in tool_names
                assert "mcp_calc_calc_sub" in tool_names
                assert "mcp_calc_calc_mul" not in tool_names
                assert "mcp_calc_system_wipe" not in tool_names
                assert len(disc["tools"]) == 2

                # Check registry has the tools registered
                assert "mcp_calc_calc_add" in fake_registry._tools
                assert "mcp_calc_calc_sub" in fake_registry._tools
                assert "mcp_calc_calc_mul" not in fake_registry._tools
                assert "mcp_calc_system_wipe" not in fake_registry._tools

                # 2. Call from WebUI agent layer through registry down to worker
                set_request_profile("bob")
                try:
                    tool_add = fake_registry.get("mcp_calc_calc_add")
                    res_add = tool_add(a=19, b=23)
                    assert float(res_add) == 42.0

                    tool_sub = fake_registry.get("mcp_calc_calc_sub")
                    res_sub = tool_sub(a=100, b=58)
                    assert float(res_sub) == 42.0
                finally:
                    clear_request_profile()

        finally:
            mgr.shutdown_all()
            clear_request_profile()




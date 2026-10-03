"""
Tests for the profile MCP worker running on the official MCP SDK.

Real servers only (no hand-rolled JSON-RPC mocks): a streamable-HTTP server with
sessions and a silent stdio server, both launched from tests/mcp_fixtures/.
Covers:
- HTTP session reuse across calls on one persistent connection.
- Silent stdio server: tool timeouts are honoured and the connection survives.
- Authorization on the exact (server, tool) pair of the active profile, with no
  fallback on the bare tool name.
- Two profiles sharing the same server and tool names get their own results.
"""

import json
import socket
import subprocess
import sys
import time
import types
from pathlib import Path
from unittest.mock import patch

import pytest

_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from api.mcp_worker import ProfileWorkerEngine
from api.profiles import clear_request_profile, set_request_profile

FIXTURES = Path(__file__).parent / "mcp_fixtures"


def _free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class HttpFixture:
    """Real streamable-HTTP MCP server in a subprocess."""

    def __init__(self, tmp_path: Path, token: str, prefix: str = ""):
        self.token = token
        self.port = _free_port()
        self.url = f"http://127.0.0.1:{self.port}/mcp"
        self.logfile = tmp_path / f"http-{self.port}.log"
        self.logfile.write_text("")
        env = {**__import__("os").environ, "FIXTURE_PREFIX": prefix}
        self.proc = subprocess.Popen(
            [sys.executable, str(FIXTURES / "http_server.py"), str(self.port), token, str(self.logfile)],
            env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        deadline = time.time() + 15
        while time.time() < deadline:
            try:
                socket.create_connection(("127.0.0.1", self.port), timeout=0.2).close()
                return
            except OSError:
                time.sleep(0.1)
        self.stop()
        raise RuntimeError("HTTP fixture did not start")

    def requests(self):
        return [json.loads(l) for l in self.logfile.read_text().splitlines() if l.strip()]

    def stop(self):
        self.proc.terminate()
        try:
            self.proc.wait(timeout=3)
        except Exception:
            self.proc.kill()


def _stdio_cfg(prefix: str = "", **extra) -> dict:
    return {
        "command": sys.executable,
        "args": [str(FIXTURES / "stdio_server.py")],
        "env": {"FIXTURE_PREFIX": prefix},
        **extra,
    }


def _engine(tmp_path: Path, servers: dict, name: str = "p") -> ProfileWorkerEngine:
    import yaml

    home = tmp_path / name
    home.mkdir(parents=True, exist_ok=True)
    (home / "config.yaml").write_text(yaml.safe_dump({"mcp_servers": servers}), encoding="utf-8")
    return ProfileWorkerEngine(name, home)


@pytest.fixture
def http_server(tmp_path):
    srv = HttpFixture(tmp_path, token="tok_http", prefix="[H] ")
    yield srv
    srv.stop()


class TestHttpWithSession:
    def test_one_session_is_reused_for_discovery_and_every_call(self, tmp_path, http_server):
        eng = _engine(tmp_path, {"relay": {
            "url": http_server.url, "headers": {"Authorization": "Bearer tok_http"},
        }})
        try:
            res = eng.reload()
            assert res["ok"] is True, res
            names = {t["name"] for t in res["tools"]}
            assert "mcp_relay_echo" in names

            assert eng.call_tool("relay", "echo", {"message": "one"}) == "[H] one"
            assert eng.call_tool("relay", "echo", {"message": "two"}) == "[H] two"

            reqs = http_server.requests()
            assert all(r["auth_ok"] for r in reqs)
            posts = [r for r in reqs if r["method"] == "POST"]
            sessionless = [r for r in posts if not r["session"]]
            assert len(sessionless) == 1, "exactly one initialize, then everything on the session"
            sessions = {r["session"] for r in posts if r["session"]}
            assert len(sessions) == 1, f"calls must share one MCP session, got {sessions}"
        finally:
            eng.shutdown()

    def test_wrong_token_is_reported_as_connection_failure(self, tmp_path, http_server):
        eng = _engine(tmp_path, {"relay": {
            "url": http_server.url, "headers": {"Authorization": "Bearer nope"},
        }})
        try:
            res = eng.reload()
            assert res["ok"] is False
            assert res["tools"] == []
        finally:
            eng.shutdown()


class TestSilentStdio:
    def test_silent_server_answers_and_stays_usable_after_a_timeout(self, tmp_path):
        eng = _engine(tmp_path, {"quiet": _stdio_cfg("[S] ", timeout=1)})
        try:
            res = eng.reload()
            assert res["ok"] is True, res
            assert eng.call_tool("quiet", "echo", {"message": "a"}) == "[S] a"

            t0 = time.time()
            with pytest.raises(Exception) as exc:
                eng.call_tool("quiet", "slow", {"seconds": 5})
            assert time.time() - t0 < 4, "timeout must be honoured on a silent server"
            assert "time" in str(exc.value).lower()

            # the persistent connection recovers and answers again
            assert eng.call_tool("quiet", "echo", {"message": "b"}) == "[S] b"
        finally:
            eng.shutdown()


class TestExactServerToolAuthorization:
    def test_unknown_server_never_falls_back_to_a_tool_name_match(self, tmp_path):
        eng = _engine(tmp_path, {"quiet": _stdio_cfg("[S] ")})
        try:
            eng.reload()
            with pytest.raises(KeyError):
                eng.call_tool("other-server", "echo", {"message": "x"})
            with pytest.raises(KeyError):
                eng.call_tool("", "echo", {"message": "x"})
        finally:
            eng.shutdown()

    def test_excluded_tool_cannot_be_called_by_original_name(self, tmp_path):
        eng = _engine(tmp_path, {"quiet": _stdio_cfg("[S] ", exclude_tools=["secret_admin"])})
        try:
            eng.reload()
            with pytest.raises(PermissionError):
                eng.call_tool("quiet", "secret_admin", {"message": "x"})
            assert eng.call_tool("quiet", "echo", {"message": "ok"}) == "[S] ok"
        finally:
            eng.shutdown()

    def test_tool_of_another_server_is_not_reachable_through_this_one(self, tmp_path):
        eng = _engine(tmp_path, {
            "a": _stdio_cfg("[A] ", include_tools=["slow"]),
            "b": _stdio_cfg("[B] "),
        })
        try:
            eng.reload()
            with pytest.raises(PermissionError):
                eng.call_tool("a", "echo", {"message": "x"})  # b has echo, a does not
            assert eng.call_tool("b", "echo", {"message": "x"}) == "[B] x"
        finally:
            eng.shutdown()


class StrictRegistry:
    """Mirrors the real hermes-agent `tools.registry` contract.

    register() takes (name, toolset, schema, handler) and the agent invokes
    handlers as ``handler(args_dict, **agent_kwargs)``; a private-dict write
    (the old fallback) would corrupt the real registry, so none is offered.
    """

    class Entry:
        def __init__(self, name, toolset, schema, handler):
            self.name, self.toolset, self.schema, self.handler = name, toolset, schema, handler

    def __init__(self):
        self.entries = {}

    def register(self, name, toolset, schema, handler, **_):
        self.entries[name] = self.Entry(name, toolset, schema, handler)

    def call(self, name, args, **agent_kwargs):
        return self.entries[name].handler(args, **agent_kwargs)


class TestTwoProfilesSharingToolNames:
    def test_registry_dispatch_uses_the_active_profiles_exact_server_and_tool(self, tmp_path):
        import yaml
        from api.mcp_isolated import ProfileMCPManager

        john_srv = HttpFixture(tmp_path, token="tj", prefix="[John] ")
        alice_srv = HttpFixture(tmp_path, token="ta", prefix="[Alice] ")
        homes = {p: tmp_path / p for p in ("john", "alice", "mario")}
        for h in homes.values():
            h.mkdir()
        (homes["john"] / "config.yaml").write_text(yaml.safe_dump({"mcp_servers": {
            "relay": {"url": john_srv.url, "headers": {"Authorization": "Bearer tj"}}}}))
        (homes["alice"] / "config.yaml").write_text(yaml.safe_dump({"mcp_servers": {
            "relay": {"url": alice_srv.url, "headers": {"Authorization": "Bearer ta"}}}}))
        # Mario owns a tool called `echo` too, but on ANOTHER server name.
        (homes["mario"] / "config.yaml").write_text(yaml.safe_dump({"mcp_servers": {
            "other": _stdio_cfg("[Mario] ")}}))

        registry = StrictRegistry()
        tools_mod, reg_mod = types.ModuleType("tools"), types.ModuleType("tools.registry")
        reg_mod.registry = registry
        tools_mod.registry = reg_mod

        def cfg_for(profile=None, **_):
            f = homes[profile] / "config.yaml"
            return yaml.safe_load(f.read_text())

        mgr = ProfileMCPManager.get_instance()
        mgr.shutdown_all()
        try:
            with patch.dict("sys.modules", {"tools": tools_mod, "tools.registry": reg_mod}), \
                 patch("api.config.get_config", side_effect=cfg_for), \
                 patch("api.profiles.get_hermes_home_for_profile", side_effect=lambda p: homes[p]):
                for p in ("john", "alice", "mario"):
                    assert mgr.discover_for_profile(p, homes[p])["ok"] is True

                entry = registry.entries["mcp_relay_echo"]
                assert entry.toolset == "mcp-relay"
                assert entry.schema["name"] == "mcp_relay_echo"
                assert entry.schema["parameters"]["properties"]["message"]["type"] == "string"

                # Agent-side kwargs (task_id...) must never leak in as tool arguments.
                for prof, expected in (("john", "[John] hi"), ("alice", "[Alice] hi"), ("john", "[John] hi")):
                    set_request_profile(prof)
                    try:
                        assert registry.call("mcp_relay_echo", {"message": "hi"}, task_id="t-1") == expected
                    finally:
                        clear_request_profile()

                # Mario has `echo` on server "other": that must NOT authorize relay's echo.
                set_request_profile("mario")
                try:
                    with pytest.raises(PermissionError):
                        registry.call("mcp_relay_echo", {"message": "intrusion"}, task_id="t-1")
                finally:
                    clear_request_profile()
        finally:
            mgr.shutdown_all()
            john_srv.stop()
            alice_srv.stop()

"""
Hermes Web UI -- Dedicated Profile MCP Worker.

Runs as a persistent dedicated process per profile with that profile's clean environment.
Maintains persistent MCP server connections (HTTP & Stdio) and executes tool calls
via JSON-RPC IPC over stdin/stdout.

Guarantees:
1. Secrets from the profile's .env live strictly in this dedicated process.
2. Supports both HTTP/SSE and Stdio transports with include_tools / exclude_tools filters.
3. Exposes tools conforming to Hermes tool naming conventions (mcp_<server>_<tool>).
4. Maintains persistent connections across multiple requests and dispatches live tool calls.
"""

import argparse
import json
import logging
import os
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

# Configure minimal stderr logging
logging.basicConfig(level=logging.INFO, format="[mcp-worker %(name)s] %(message)s", stream=sys.stderr)
logger = logging.getLogger("worker")


def _expand_env_vars(val: Any) -> Any:
    """Recursively expand ${VAR} or $VAR from os.environ."""
    if isinstance(val, str):
        return os.path.expandvars(val)
    if isinstance(val, dict):
        return {k: _expand_env_vars(v) for k, v in val.items()}
    if isinstance(val, list):
        return [_expand_env_vars(item) for item in val]
    return val


def _load_profile_config(home_path: Path) -> dict:
    """Load config.yaml from profile home and expand environment variables."""
    cfg_file = home_path / "config.yaml"
    if not cfg_file.exists():
        return {}
    try:
        import yaml
        with open(cfg_file, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
        if isinstance(data, dict):
            return _expand_env_vars(data)
    except Exception as e:
        logger.warning("Failed to parse %s: %s", cfg_file, e)
    return {}


def _normalize_hermes_tool_name(server_name: str, raw_tool_name: str) -> str:
    """Format tool name matching Hermes agent convention: mcp_<clean_server>_<tool>."""
    clean_server = re.sub(r"[^a-zA-Z0-9_]", "_", server_name.removeprefix("mcp-").removeprefix("mcp_"))
    clean_tool = re.sub(r"[^a-zA-Z0-9_]", "_", raw_tool_name)
    prefix = f"mcp_{clean_server}_"
    if clean_tool.startswith(prefix):
        return clean_tool
    if clean_tool.startswith("mcp_") and clean_server in clean_tool:
        return clean_tool
    return f"{prefix}{clean_tool}"


def _filter_and_format_tools(
    server_name: str,
    raw_tools: List[dict],
    include_tools: Optional[List[str]] = None,
    exclude_tools: Optional[List[str]] = None,
) -> List[dict]:
    """Filter raw tools using include/exclude lists and tag them with Hermes naming."""
    inc_set: Optional[Set[str]] = set(include_tools) if include_tools else None
    exc_set: Optional[Set[str]] = set(exclude_tools) if exclude_tools else None

    out: List[dict] = []
    for t in raw_tools:
        if not isinstance(t, dict):
            continue
        raw_name = str(t.get("name") or "").strip()
        if not raw_name:
            continue

        hermes_name = _normalize_hermes_tool_name(server_name, raw_name)

        # Include filter (allowlist)
        if inc_set is not None:
            if raw_name not in inc_set and hermes_name not in inc_set:
                continue

        # Exclude filter (denylist)
        if exc_set is not None:
            if raw_name in exc_set or hermes_name in exc_set:
                continue

        tool_entry = dict(t)
        tool_entry["original_name"] = raw_name
        tool_entry["name"] = hermes_name
        tool_entry["server"] = server_name
        schema = t.get("inputSchema") or t.get("schema") or {}
        tool_entry["schema"] = schema
        out.append(tool_entry)

    return out


def _extract_content_text(result: Any) -> Any:
    """Extract plain text from MCP tool result structure."""
    if isinstance(result, dict) and "content" in result:
        content = result["content"]
        if isinstance(content, list):
            parts = []
            for item in content:
                if isinstance(item, dict):
                    if item.get("type") == "text":
                        parts.append(str(item.get("text", "")))
                    else:
                        parts.append(json.dumps(item))
                else:
                    parts.append(str(item))
            return "\n".join(parts)
    return result


class BaseMCPServerConnection:
    """Base class for persistent MCP server connections."""

    def __init__(self, name: str, cfg: dict):
        self.name = name
        self.cfg = cfg
        self.enabled = bool(cfg.get("enabled", True))
        self.timeout = int(cfg.get("timeout", 120))
        self.connect_timeout = int(cfg.get("connect_timeout", 5))
        self.include_tools = cfg.get("include_tools")
        self.exclude_tools = cfg.get("exclude_tools")
        self.connected = False
        self.tools: List[dict] = []

    def connect(self) -> None:
        raise NotImplementedError

    def call_tool(self, tool_name: str, arguments: dict) -> Any:
        raise NotImplementedError

    def disconnect(self) -> None:
        self.connected = False
        self.tools = []

    def get_status(self) -> dict:
        raise NotImplementedError


class HTTPMCPServerConnection(BaseMCPServerConnection):
    """Persistent connection client for an HTTP/Streamable MCP server."""

    def __init__(self, name: str, cfg: dict):
        super().__init__(name, cfg)
        self.url = str(cfg.get("url", "")).strip()
        self.headers = dict(cfg.get("headers", {}) or {})
        self._req_id = 0

    def _post_json(self, payload: dict, timeout: Optional[int] = None) -> dict:
        """Send JSON-RPC payload to HTTP MCP endpoint."""
        body_bytes = json.dumps(payload).encode("utf-8")
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            **self.headers,
        }
        req = urllib.request.Request(self.url, data=body_bytes, headers=headers, method="POST")
        effective_timeout = timeout or self.timeout
        try:
            with urllib.request.urlopen(req, timeout=effective_timeout) as resp:
                resp_bytes = resp.read()
                if not resp_bytes:
                    return {}
                resp_str = resp_bytes.decode("utf-8")
                # Handle possible SSE framing
                for line in resp_str.splitlines():
                    line = line.strip()
                    if line.startswith("data:"):
                        line = line[len("data:"):].strip()
                    if line.startswith("{") and line.endswith("}"):
                        try:
                            return json.loads(line)
                        except Exception:
                            continue
                return json.loads(resp_str)
        except urllib.error.HTTPError as he:
            err_body = he.read().decode("utf-8", errors="replace")
            raise ConnectionError(f"HTTP {he.code}: {err_body}") from he
        except Exception as e:
            raise ConnectionError(str(e)) from e

    def connect(self) -> None:
        """Perform MCP initialize handshake and tools/list."""
        if not self.enabled or not self.url:
            self.connected = False
            self.tools = []
            return

        self._req_id += 1
        init_payload = {
            "jsonrpc": "2.0",
            "id": self._req_id,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": {"name": "hermes-worker", "version": "1.0"},
            },
        }
        init_resp = self._post_json(init_payload, timeout=self.connect_timeout)
        if "error" in init_resp:
            err = init_resp["error"]
            err_msg = err.get("message") if isinstance(err, dict) else str(err)
            raise ConnectionError(f"Initialize error: {err_msg}")

        # Post-initialization notification
        try:
            self._post_json({
                "jsonrpc": "2.0",
                "method": "notifications/initialized",
                "params": {},
            }, timeout=self.connect_timeout)
        except Exception:
            pass

        # Discover tools
        self._req_id += 1
        tools_payload = {
            "jsonrpc": "2.0",
            "id": self._req_id,
            "method": "tools/list",
            "params": {},
        }
        tools_resp = self._post_json(tools_payload, timeout=self.connect_timeout)
        if "error" in tools_resp:
            err = tools_resp["error"]
            err_msg = err.get("message") if isinstance(err, dict) else str(err)
            raise ConnectionError(f"tools/list error: {err_msg}")

        raw_tools = []
        if isinstance(tools_resp.get("result"), dict):
            raw_tools = tools_resp["result"].get("tools", [])
        elif isinstance(tools_resp.get("tools"), list):
            raw_tools = tools_resp["tools"]

        self.tools = _filter_and_format_tools(
            self.name,
            raw_tools if isinstance(raw_tools, list) else [],
            self.include_tools,
            self.exclude_tools,
        )
        self.connected = True

    def call_tool(self, tool_name: str, arguments: dict) -> Any:
        """Call an MCP tool on this active server connection."""
        if not self.connected:
            self.connect()

        self._req_id += 1
        payload = {
            "jsonrpc": "2.0",
            "id": self._req_id,
            "method": "tools/call",
            "params": {
                "name": tool_name,
                "arguments": arguments or {},
            },
        }
        resp = self._post_json(payload, timeout=self.timeout)
        if "error" in resp:
            err = resp["error"]
            msg = err.get("message") if isinstance(err, dict) else str(err)
            raise RuntimeError(f"MCP tool call error: {msg}")

        result = resp.get("result")
        return _extract_content_text(result)

    def get_status(self) -> dict:
        return {
            "name": self.name,
            "transport": "http",
            "url": self.url,
            "enabled": self.enabled,
            "active": self.connected,
            "status": "active" if self.connected else ("disabled" if not self.enabled else "configured"),
            "tool_count": len(self.tools) if self.connected else 0,
            "timeout": self.timeout,
            "connect_timeout": self.connect_timeout,
        }


class StdioMCPServerConnection(BaseMCPServerConnection):
    """Persistent connection client for an stdio subprocess MCP server."""

    def __init__(self, name: str, cfg: dict):
        super().__init__(name, cfg)
        self.command = str(cfg.get("command", "")).strip()
        self.args = list(cfg.get("args") or [])
        self.env = dict(cfg.get("env") or {})
        self.cwd = cfg.get("cwd")
        self.proc: Optional[subprocess.Popen] = None
        self._lock = threading.RLock()
        self._req_id = 0

    def _send_rpc(self, payload: dict, timeout: Optional[float] = None) -> dict:
        """Send JSON-RPC request to subprocess stdin and read response from stdout."""
        with self._lock:
            if not self.proc or self.proc.poll() is not None:
                raise ConnectionError(f"Subprocess for server '{self.name}' is not running")

            line = json.dumps(payload) + "\n"
            try:
                self.proc.stdin.write(line)
                self.proc.stdin.flush()
            except Exception as e:
                raise ConnectionError(f"Failed to write to stdio server '{self.name}': {e}") from e

            # Read response with timeout
            effective_timeout = timeout or self.timeout
            start_t = time.time()
            expected_id = payload.get("id")

            while time.time() - start_t < effective_timeout:
                if self.proc.poll() is not None:
                    raise ConnectionError(f"Subprocess for server '{self.name}' exited prematurely")
                
                resp_line = self.proc.stdout.readline()
                if not resp_line:
                    time.sleep(0.05)
                    continue
                resp_line = resp_line.strip()
                if not resp_line:
                    continue
                try:
                    data = json.loads(resp_line)
                    if expected_id is None or data.get("id") == expected_id:
                        return data
                except Exception:
                    continue

            raise TimeoutError(f"Timed out waiting for response from stdio server '{self.name}'")

    def connect(self) -> None:
        if not self.enabled or not self.command:
            self.connected = False
            self.tools = []
            return

        with self._lock:
            self.disconnect()

            cmd = [self.command] + [str(a) for a in self.args]
            sub_env = dict(os.environ)
            if self.env:
                sub_env.update({k: str(v) for k, v in self.env.items()})

            try:
                self.proc = subprocess.Popen(
                    cmd,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1,
                    cwd=str(self.cwd) if self.cwd else None,
                    env=sub_env,
                )
            except Exception as e:
                raise ConnectionError(f"Failed to spawn stdio server '{self.name}': {e}") from e

            self._req_id += 1
            init_payload = {
                "jsonrpc": "2.0",
                "id": self._req_id,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {"name": "hermes-worker", "version": "1.0"},
                },
            }
            init_resp = self._send_rpc(init_payload, timeout=self.connect_timeout)
            if "error" in init_resp:
                err = init_resp["error"]
                err_msg = err.get("message") if isinstance(err, dict) else str(err)
                raise ConnectionError(f"Initialize error: {err_msg}")

            # Send initialized notification
            try:
                line = json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}) + "\n"
                self.proc.stdin.write(line)
                self.proc.stdin.flush()
            except Exception:
                pass

            # List tools
            self._req_id += 1
            tools_payload = {
                "jsonrpc": "2.0",
                "id": self._req_id,
                "method": "tools/list",
                "params": {},
            }
            tools_resp = self._send_rpc(tools_payload, timeout=self.connect_timeout)
            if "error" in tools_resp:
                err = tools_resp["error"]
                err_msg = err.get("message") if isinstance(err, dict) else str(err)
                raise ConnectionError(f"tools/list error: {err_msg}")

            raw_tools = []
            if isinstance(tools_resp.get("result"), dict):
                raw_tools = tools_resp["result"].get("tools", [])
            elif isinstance(tools_resp.get("tools"), list):
                raw_tools = tools_resp["tools"]

            self.tools = _filter_and_format_tools(
                self.name,
                raw_tools if isinstance(raw_tools, list) else [],
                self.include_tools,
                self.exclude_tools,
            )
            self.connected = True

    def call_tool(self, tool_name: str, arguments: dict) -> Any:
        if not self.connected:
            self.connect()

        with self._lock:
            self._req_id += 1
            payload = {
                "jsonrpc": "2.0",
                "id": self._req_id,
                "method": "tools/call",
                "params": {
                    "name": tool_name,
                    "arguments": arguments or {},
                },
            }
            resp = self._send_rpc(payload, timeout=self.timeout)
            if "error" in resp:
                err = resp["error"]
                msg = err.get("message") if isinstance(err, dict) else str(err)
                raise RuntimeError(f"MCP tool call error: {msg}")

            result = resp.get("result")
            return _extract_content_text(result)

    def disconnect(self) -> None:
        with self._lock:
            if self.proc:
                try:
                    self.proc.terminate()
                    self.proc.wait(timeout=1.0)
                except Exception:
                    try:
                        self.proc.kill()
                    except Exception:
                        pass
                self.proc = None
            self.connected = False
            self.tools = []

    def get_status(self) -> dict:
        return {
            "name": self.name,
            "transport": "stdio",
            "command": self.command,
            "args": self.args,
            "enabled": self.enabled,
            "active": self.connected,
            "status": "active" if self.connected else ("disabled" if not self.enabled else "configured"),
            "tool_count": len(self.tools) if self.connected else 0,
            "timeout": self.timeout,
            "connect_timeout": self.connect_timeout,
        }


class ProfileWorkerEngine:
    """Manages server connections and dispatches tool calls for a profile."""

    def __init__(self, profile_name: str, home_path: Path):
        self.profile_name = profile_name
        self.home_path = home_path
        self.servers: Dict[str, BaseMCPServerConnection] = {}

    def shutdown(self) -> None:
        """Disconnect and stop all managed server connections."""
        for s in self.servers.values():
            try:
                s.disconnect()
            except Exception:
                pass
        self.servers.clear()

    def reload(self) -> dict:
        """Reload configuration and refresh all server connections."""
        cfg = _load_profile_config(self.home_path)
        mcp_servers = cfg.get("mcp_servers", {}) if isinstance(cfg, dict) else {}
        self.shutdown()

        tools_list = []
        statuses = {}
        errors = []

        for name, s_cfg in mcp_servers.items():
            if not isinstance(s_cfg, dict):
                continue

            conn: Optional[BaseMCPServerConnection] = None
            target_desc = ""
            if "url" in s_cfg:
                conn = HTTPMCPServerConnection(name, s_cfg)
                target_desc = conn.url
            elif "command" in s_cfg:
                conn = StdioMCPServerConnection(name, s_cfg)
                target_desc = f"{conn.command} {' '.join(str(a) for a in conn.args)}"

            if not conn:
                continue

            self.servers[name] = conn
            if conn.enabled:
                try:
                    conn.connect()
                except Exception as exc:
                    logger.warning("Connection to server '%s' (%s) failed: %s", name, target_desc, exc)
                    errors.append(f"Connection to server '{name}' ({target_desc}) failed: {exc}")

            st = conn.get_status()
            statuses[name] = st
            if conn.connected:
                for t in conn.tools:
                    tools_list.append(dict(t))

        is_ok = True
        err_msg = None
        if errors:
            enabled_servers = [s for s in self.servers.values() if s.enabled]
            if enabled_servers and not any(s.connected for s in enabled_servers):
                is_ok = False
                err_msg = "; ".join(errors)

        return {
            "ok": is_ok,
            "profile": self.profile_name,
            "servers": statuses,
            "tools": tools_list,
            "error": err_msg,
        }

    def call_tool(self, server_name: str, tool_name: str, arguments: dict) -> Any:
        """Execute a tool call against the server."""
        conn = self.servers.get(server_name)
        if not conn and server_name.startswith("mcp-"):
            conn = self.servers.get(server_name[len("mcp-"):])
        if not conn:
            # Search by tool name match if server_name is empty or ambiguous
            for s_name, s_conn in self.servers.items():
                for t in s_conn.tools:
                    if t.get("name") == tool_name or t.get("original_name") == tool_name:
                        conn = s_conn
                        server_name = s_name
                        break
                if conn:
                    break

        if not conn:
            raise KeyError(f"MCP server '{server_name}' not found for profile '{self.profile_name}'")

        # Strip prefixes to send original tool name to the server
        clean_server = re.sub(r"[^a-zA-Z0-9_]", "_", server_name.removeprefix("mcp-").removeprefix("mcp_"))
        target_tool = tool_name
        prefix = f"mcp_{clean_server}_"
        if target_tool.startswith(prefix):
            target_tool = target_tool[len(prefix):]

        return conn.call_tool(target_tool, arguments)

    def get_status(self) -> dict:
        return {
            "ok": True,
            "profile": self.profile_name,
            "servers": {name: s.get_status() for name, s in self.servers.items()},
        }


def main():
    parser = argparse.ArgumentParser(description="Hermes Profile MCP Worker")
    parser.add_argument("--profile", required=True, help="Profile ID")
    parser.add_argument("--home", required=True, help="Profile Hermes home path")
    args = parser.parse_args()

    home_path = Path(args.home).resolve()
    engine = ProfileWorkerEngine(args.profile, home_path)

    # Initial load
    initial_res = engine.reload()
    logger.info("Worker started for profile '%s'. Found %d servers, %d tools",
                args.profile, len(initial_res["servers"]), len(initial_res["tools"]))

    # Read IPC commands from stdin
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except Exception:
            continue

        req_id = req.get("id")
        action = req.get("action", "")

        try:
            if action == "ping":
                resp = {"id": req_id, "ok": True}
            elif action == "discover" or action == "reload":
                res = engine.reload()
                resp = {"id": req_id, **res}
            elif action == "status":
                res = engine.get_status()
                resp = {"id": req_id, **res}
            elif action == "call_tool":
                server_name = req.get("server") or ""
                tool_name = req.get("tool") or ""
                arguments = req.get("arguments") or {}
                result = engine.call_tool(server_name, tool_name, arguments)
                resp = {"id": req_id, "ok": True, "result": result}
            elif action == "shutdown":
                engine.shutdown()
                resp = {"id": req_id, "ok": True}
                sys.stdout.write(json.dumps(resp) + "\n")
                sys.stdout.flush()
                break
            else:
                resp = {"id": req_id, "ok": False, "error": f"Unknown action '{action}'"}
        except Exception as exc:
            resp = {"id": req_id, "ok": False, "error": str(exc), "error_type": type(exc).__name__}

        sys.stdout.write(json.dumps(resp) + "\n")
        sys.stdout.flush()

    engine.shutdown()
    logger.info("Worker exiting cleanly for profile '%s'", args.profile)


if __name__ == "__main__":
    main()

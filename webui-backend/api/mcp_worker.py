"""
Hermes Web UI -- Dedicated Profile MCP Worker.

Runs as a persistent dedicated process per profile with that profile's clean environment.
Maintains persistent MCP server connections and executes tool calls via JSON-RPC IPC over stdin/stdout.

Guarantees:
1. Secrets from the profile's .env live strictly in this dedicated process.
2. Connections are kept alive and active across multiple requests.
3. Provides live execution channel for agent tool calls.
"""

import argparse
import json
import logging
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional

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


class HTTPMCPServerConnection:
    """Persistent connection client for an HTTP/Streamable MCP server."""

    def __init__(self, name: str, cfg: dict):
        self.name = name
        self.cfg = cfg
        self.url = str(cfg.get("url", "")).strip()
        self.headers = dict(cfg.get("headers", {}) or {})
        self.timeout = int(cfg.get("timeout", 120))
        self.connect_timeout = int(cfg.get("connect_timeout", 5))
        self.enabled = bool(cfg.get("enabled", True))
        self.connected = False
        self.tools: List[dict] = []
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
                # Handle possible SSE framing (event: message\ndata: {...})
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

        # Post-initialization notification (if supported)
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

        self.tools = raw_tools if isinstance(raw_tools, list) else []
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


class ProfileWorkerEngine:
    """Manages server connections and dispatches tool calls for a profile."""

    def __init__(self, profile_name: str, home_path: Path):
        self.profile_name = profile_name
        self.home_path = home_path
        self.servers: Dict[str, HTTPMCPServerConnection] = {}

    def reload(self) -> dict:
        """Reload configuration and refresh all server connections."""
        cfg = _load_profile_config(self.home_path)
        mcp_servers = cfg.get("mcp_servers", {}) if isinstance(cfg, dict) else {}
        self.servers.clear()

        tools_list = []
        statuses = {}
        errors = []

        for name, s_cfg in mcp_servers.items():
            if not isinstance(s_cfg, dict):
                continue
            conn = HTTPMCPServerConnection(name, s_cfg)
            self.servers[name] = conn
            if conn.enabled:
                try:
                    conn.connect()
                except Exception as exc:
                    logger.warning("Connection to server '%s' (%s) failed: %s", name, conn.url, exc)
                    errors.append(f"Connection to server '{name}' ({conn.url}) failed: {exc}")
            
            st = conn.get_status()
            statuses[name] = st
            if conn.connected:
                for t in conn.tools:
                    t_copy = dict(t)
                    t_copy["server"] = name
                    tools_list.append(t_copy)

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
        # Strip prefixes if tool_name is namespaced (e.g. mcp_mobile_status -> status)
        conn = self.servers.get(server_name)
        if not conn:
            # Try fuzzy matching without 'mcp-'
            if server_name.startswith("mcp-"):
                conn = self.servers.get(server_name[len("mcp-"):])
        if not conn:
            raise KeyError(f"MCP server '{server_name}' not found for profile '{self.profile_name}'")

        # Strip namespace if needed
        target_tool = tool_name
        prefix = f"mcp_{server_name.replace('-', '_')}_"
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

    logger.info("Worker exiting cleanly for profile '%s'", args.profile)


if __name__ == "__main__":
    main()

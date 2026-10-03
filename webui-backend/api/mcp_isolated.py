"""
Hermes Web UI -- MCP Profile Isolation & Scoping.

Provides:
1. ProfileMCPManager: Manages persistent profile MCP worker processes.
   Each profile with MCP servers gets its own long-running worker process
   spawned with isolated_env (secrets never leak to the shared WebUI process).
2. Live IPC Execution: Tool calls from AIAgent route over IPC (stdin/stdout)
   to the active profile worker, executing against real live MCP connections.
3. ProfileAwareMCPServers: Scopes tools.mcp_tool._servers by profile.
4. Live Status & Inventory: Reports real active/connected status only when
   the worker and its server connections are actually alive.
5. Invalidation: Automatically reloads/terminates workers and purges cached
   inventories when configuration changes.
"""

import json
import logging
import os
import re
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

logger = logging.getLogger(__name__)

# Lock for inventory and status cache updates
_INVENTORY_LOCK = threading.RLock()
# Per-profile discovered inventory: profile_name -> {"tools": [...], "servers": {...}}
_PROFILE_MCP_INVENTORY: Dict[str, Dict[str, Any]] = {}


def _sanitize_mcp_error(err_str: str, secret_values: Any = None) -> str:
    """Mask secrets and credential tokens from error messages."""
    if not err_str:
        return ""
    err_str = re.sub(r'Bearer\s+[A-Za-z0-9_\-\.]+', 'Bearer [REDACTED]', err_str)
    err_str = re.sub(r'token=[A-Za-z0-9_\-\.]+', 'token=[REDACTED]', err_str)
    err_str = re.sub(r'key=[A-Za-z0-9_\-\.]+', 'key=[REDACTED]', err_str)
    if isinstance(secret_values, dict):
        vals = list(secret_values.values())
    elif isinstance(secret_values, (list, tuple, set)):
        vals = list(secret_values)
    else:
        vals = []
    for val in vals:
        val_str = str(val)
        if val_str and len(val_str) >= 4 and val_str in err_str:
            err_str = err_str.replace(val_str, '[REDACTED]')
    return err_str


class ProfileMCPWorkerClient:
    """Client for a persistent profile MCP worker process."""

    def __init__(self, profile_name: str, home_path: Path, profile_env: Optional[Dict[str, str]] = None):
        self.profile_name = profile_name
        self.home_path = Path(home_path).resolve()
        self.profile_env = dict(profile_env or {})
        self.proc: Optional[subprocess.Popen] = None
        self._pending_requests: Dict[str, dict] = {}
        self._pending_events: Dict[str, threading.Event] = {}
        self._lock = threading.RLock()
        self._reader_thread: Optional[threading.Thread] = None
        self._is_alive = False

    def is_alive(self) -> bool:
        return self._is_alive and self.proc is not None and self.proc.poll() is None

    def start(self) -> None:
        with self._lock:
            if self.is_alive():
                return

            from api.config import PYTHON_EXE, REPO_ROOT, _AGENT_DIR

            isolated_env = dict(os.environ)
            # Secrets strictly passed only to child process
            if self.profile_env:
                isolated_env.update(self.profile_env)
            isolated_env["HERMES_HOME"] = str(self.home_path)
            isolated_env["HERMES_WEBUI_DIR"] = str(REPO_ROOT)
            isolated_env["PYTHONPATH"] = f"{REPO_ROOT}:{isolated_env.get('PYTHONPATH', '')}".rstrip(":")
            if _AGENT_DIR:
                isolated_env["HERMES_AGENT_DIR"] = str(_AGENT_DIR)
                isolated_env["PYTHONPATH"] = f"{_AGENT_DIR}:{isolated_env['PYTHONPATH']}".rstrip(":")

            cmd = [
                PYTHON_EXE or sys.executable,
                "-m",
                "api.mcp_worker",
                "--profile",
                self.profile_name,
                "--home",
                str(self.home_path),
            ]

            try:
                self.proc = subprocess.Popen(
                    cmd,
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    bufsize=1,
                    cwd=str(REPO_ROOT),
                    env=isolated_env,
                )
                self._is_alive = True
            except Exception as e:
                logger.error("Failed to spawn MCP worker for profile '%s': %s", self.profile_name, e)
                self._is_alive = False
                return

            self._reader_thread = threading.Thread(
                target=self._read_stdout_loop,
                name=f"mcp-worker-reader-{self.profile_name}",
                daemon=True,
            )
            self._reader_thread.start()

            # Start stderr logger thread
            threading.Thread(
                target=self._read_stderr_loop,
                name=f"mcp-worker-err-{self.profile_name}",
                daemon=True,
            ).start()

    def _read_stdout_loop(self) -> None:
        if not self.proc or not self.proc.stdout:
            return
        try:
            for line in self.proc.stdout:
                line = line.strip()
                if not line:
                    continue
                try:
                    data = json.loads(line)
                    req_id = data.get("id")
                    if req_id:
                        with self._lock:
                            self._pending_requests[req_id] = data
                            evt = self._pending_events.get(req_id)
                            if evt:
                                evt.set()
                except Exception as exc:
                    logger.debug("Failed to parse worker stdout: %s", exc)
        except Exception:
            pass
        finally:
            self._is_alive = False

    def _read_stderr_loop(self) -> None:
        if not self.proc or not self.proc.stderr:
            return
        try:
            for line in self.proc.stderr:
                logger.debug("[%s worker err] %s", self.profile_name, line.strip())
        except Exception:
            pass

    def send_command(self, action: str, timeout: float = 30.0, **params) -> dict:
        """Send command to worker and await response."""
        if not self.is_alive():
            self.start()
        if not self.is_alive() or not self.proc or not self.proc.stdin:
            return {"ok": False, "error": f"Worker process for profile '{self.profile_name}' is not running"}

        req_id = f"req-{uuid.uuid4().hex[:8]}"
        payload = {"id": req_id, "action": action, **params}
        evt = threading.Event()

        with self._lock:
            self._pending_events[req_id] = evt

        try:
            line = json.dumps(payload) + "\n"
            with self._lock:
                self.proc.stdin.write(line)
                self.proc.stdin.flush()
        except Exception as e:
            self._is_alive = False
            return {"ok": False, "error": f"Failed to write to worker stdin: {e}"}

        signaled = False
        start_t = time.time()
        while time.time() - start_t < timeout:
            if evt.wait(timeout=0.1):
                signaled = True
                break
            if not self.is_alive():
                break

        with self._lock:
            self._pending_events.pop(req_id, None)
            resp = self._pending_requests.pop(req_id, None)

        if not signaled or resp is None:
            if not self.is_alive():
                return {"ok": False, "error": f"Worker process for profile '{self.profile_name}' terminated unexpectedly"}
            return {"ok": False, "error": f"Worker timed out waiting for response to {action}"}
        return resp

    def stop(self) -> None:
        """Terminate the worker cleanly."""
        with self._lock:
            self._is_alive = False
            if self.proc:
                try:
                    if self.proc.stdin:
                        self.proc.stdin.write(json.dumps({"action": "shutdown"}) + "\n")
                        self.proc.stdin.flush()
                        time.sleep(0.05)
                except Exception:
                    pass
                try:
                    self.proc.terminate()
                    self.proc.wait(timeout=1.0)
                except Exception:
                    try:
                        self.proc.kill()
                    except Exception:
                        pass
                self.proc = None


class ProfileMCPManager:
    """Singleton manager for profile MCP workers and live tool dispatch."""

    _instance = None
    _lock = threading.RLock()

    def __init__(self):
        self._workers: Dict[str, ProfileMCPWorkerClient] = {}

    @classmethod
    def get_instance(cls) -> "ProfileMCPManager":
        with cls._lock:
            if cls._instance is None:
                cls._instance = ProfileMCPManager()
            return cls._instance

    def get_or_create_worker(
        self,
        profile_name: str,
        home_path: Path,
        profile_env: Optional[Dict[str, str]] = None,
    ) -> ProfileMCPWorkerClient:
        profile_str = str(profile_name or "default")
        with self._lock:
            worker = self._workers.get(profile_str)
            if worker is None:
                worker = ProfileMCPWorkerClient(profile_str, home_path, profile_env)
                self._workers[profile_str] = worker
            elif profile_env:
                worker.profile_env.update(profile_env)
            return worker

    def discover_for_profile(
        self,
        profile_name: str,
        home_path: Path,
        profile_env: Optional[Dict[str, str]] = None,
    ) -> dict:
        """Discover tools via the dedicated worker and register executable callers."""
        profile_str = str(profile_name or "default")
        from api.config import get_config as _get_cfg
        profile_cfg = _get_cfg(profile=profile_str) if profile_str else {}
        mcp_servers = profile_cfg.get("mcp_servers", {}) if isinstance(profile_cfg, dict) else {}

        if not mcp_servers:
            self.invalidate_profile(profile_str)
            return {"ok": True, "servers": {}, "tools": []}

        worker = self.get_or_create_worker(profile_str, home_path, profile_env)
        resp = worker.send_command("discover", timeout=25.0)

        if resp.get("ok"):
            tools = resp.get("tools", [])
            servers = resp.get("servers", {})
            set_profile_discovery_inventory(profile_str, tools, servers)
            # Register executable tool wrappers into registry
            self._register_live_tools_for_profile(profile_str, tools)
            return resp
        else:
            # Server unreachable or failed: mark inactive
            set_profile_discovery_inventory(profile_str, [], {})
            err_msg = resp.get("error", "Discovery failed")
            sanitized_err = _sanitize_mcp_error(str(err_msg), profile_env)
            logger.warning("Profile '%s' MCP discovery failed: %s", profile_str, sanitized_err)
            return resp

    def _register_live_tools_for_profile(self, profile_name: str, tools: List[dict]) -> None:
        """Register live tool callables into tools.registry.registry.
        
        CRUCIAL: To prevent closures from one profile overwriting another profile,
        registered functions dynamically resolve the active profile at execution time
        via api.profiles.get_active_profile_name().
        """
        try:
            from tools.registry import registry
        except Exception:
            return

        for t in tools:
            tool_name = t.get("name")
            orig_name = t.get("original_name") or tool_name
            server_name = t.get("server") or ""
            if not tool_name:
                continue

            input_schema = t.get("schema") or t.get("inputSchema") or {}
            if not isinstance(input_schema, dict) or input_schema.get("type") != "object":
                input_schema = {"type": "object", "properties": {}}
            schema = {
                "name": tool_name,
                "description": t.get("description") or f"MCP tool {orig_name} from {server_name}",
                "parameters": input_schema,
            }
            toolset_name = f"mcp-{server_name}" if not server_name.startswith("mcp-") else server_name

            # Dynamic profile dispatcher: does NOT bind a hardcoded profile in closure!
            def make_dynamic_dispatcher(s_name: str, o_name: str, full_name: str):
                def dynamic_profile_tool_caller(args=None, **kwargs):
                    # The registry calls handler(args_dict, **agent_kwargs); keyword
                    # arguments are then agent context (task_id...), never tool params.
                    # Without an args dict, keywords are the tool params (direct call).
                    params = dict(args) if isinstance(args, dict) else dict(kwargs)
                    from api.profiles import get_active_profile_name
                    active_p = str(get_active_profile_name() or "default")
                    mgr = ProfileMCPManager.get_instance()

                    # The active profile must own this exact (server, tool) pair.
                    # A same-named tool on another server must never authorize it.
                    inv = get_profile_discovered_inventory(active_p)
                    profile_tools = inv.get("tools", [])
                    has_access = any(
                        pt.get("server") == s_name
                        and (pt.get("original_name") or pt.get("name")) == o_name
                        for pt in profile_tools
                    )
                    if not has_access:
                        raise PermissionError(
                            f"MCP tool '{full_name}' from server '{s_name}' is not configured or available for active profile '{active_p}'"
                        )

                    return mgr.call_tool(active_p, s_name, o_name, params)

                dynamic_profile_tool_caller.__name__ = full_name
                dynamic_profile_tool_caller.__doc__ = f"Profile-isolated dynamic MCP tool dispatcher for {full_name}"
                return dynamic_profile_tool_caller

            dispatcher = make_dynamic_dispatcher(server_name, orig_name, tool_name)

            try:
                registry.register(
                    name=tool_name,
                    toolset=toolset_name,
                    schema=schema,
                    handler=dispatcher,
                    description=schema["description"],
                )
            except Exception as exc:
                # Never write into the registry's private state: a raw function
                # where an entry is expected breaks every later agent creation.
                logger.error("Failed to register MCP tool '%s' for profile '%s': %s", tool_name, profile_name, exc)

    def call_tool(self, profile_name: str, server_name: str, tool_name: str, arguments: dict) -> Any:
        """Call a tool via the live persistent worker."""
        profile_str = str(profile_name or "default")
        with self._lock:
            worker = self._workers.get(profile_str)
        if not worker or not worker.is_alive():
            home_path = None
            try:
                from api.profiles import get_hermes_home_for_profile
                home_path = get_hermes_home_for_profile(profile_str)
            except Exception:
                pass
            if home_path:
                worker = self.get_or_create_worker(profile_str, home_path)
            else:
                raise RuntimeError(f"No active MCP worker for profile '{profile_str}'")

        resp = worker.send_command("call_tool", server=server_name, tool=tool_name, arguments=arguments)
        if not resp.get("ok"):
            msg = resp.get("error", "Tool execution failed")
            if resp.get("error_type") == "PermissionError":
                raise PermissionError(msg)
            raise RuntimeError(msg)
        return resp.get("result")

    def get_status_for_profile(self, profile_name: str) -> dict:
        """Return real live connection status from the worker."""
        profile_str = str(profile_name or "default")
        with self._lock:
            worker = self._workers.get(profile_str)
        if not worker or not worker.is_alive():
            return {"active": False, "servers": {}}
        resp = worker.send_command("status", timeout=5.0)
        return resp if resp.get("ok") else {"active": False, "servers": {}}

    def invalidate_profile(self, profile_name: str) -> None:
        """Stop worker and purge discovery cache when config changes."""
        profile_str = str(profile_name or "default")
        with self._lock:
            worker = self._workers.pop(profile_str, None)
            if worker:
                worker.stop()
        with _INVENTORY_LOCK:
            _PROFILE_MCP_INVENTORY.pop(profile_str, None)

        servers_dict = ensure_servers_profile_aware()
        if servers_dict is not None:
            servers_dict.clear_profile(profile_str)

    def shutdown_all(self) -> None:
        with self._lock:
            for w in self._workers.values():
                w.stop()
            self._workers.clear()


class ProfileAwareMCPServers(dict):
    """Profile-partitioned dictionary replacing tools.mcp_tool._servers."""

    def __init__(self, initial=None):
        super().__init__()
        self._by_profile: Dict[str, Dict[str, Any]] = {}
        self._lock = threading.RLock()
        if initial and isinstance(initial, dict):
            self._by_profile["default"] = dict(initial)

    def _active_profile(self) -> str:
        try:
            from api.profiles import get_active_profile_name
            return str(get_active_profile_name() or "default")
        except Exception:
            return "default"

    def _get_profile_dict(self, profile: Optional[str] = None) -> Dict[str, Any]:
        p = profile or self._active_profile()
        with self._lock:
            if p not in self._by_profile:
                self._by_profile[p] = {}
            return self._by_profile[p]

    def __getitem__(self, key: str) -> Any:
        d = self._get_profile_dict()
        return d[key]

    def __setitem__(self, key: str, value: Any) -> None:
        with self._lock:
            d = self._get_profile_dict()
            d[key] = value

    def __delitem__(self, key: str) -> None:
        with self._lock:
            d = self._get_profile_dict()
            del d[key]

    def __contains__(self, key: object) -> bool:
        d = self._get_profile_dict()
        return key in d

    def get(self, key: str, default: Any = None) -> Any:
        d = self._get_profile_dict()
        return d.get(key, default)

    def pop(self, key: str, *args) -> Any:
        with self._lock:
            d = self._get_profile_dict()
            return d.pop(key, *args)

    def keys(self):
        return self._get_profile_dict().keys()

    def values(self):
        return self._get_profile_dict().values()

    def items(self):
        return self._get_profile_dict().items()

    def __iter__(self):
        return iter(self._get_profile_dict())

    def __len__(self) -> int:
        return len(self._get_profile_dict())

    def clear(self) -> None:
        with self._lock:
            self._get_profile_dict().clear()

    def clear_profile(self, profile: str) -> None:
        with self._lock:
            if profile in self._by_profile:
                self._by_profile[profile].clear()

    def get_for_profile(self, profile: str) -> Dict[str, Any]:
        return self._get_profile_dict(profile)


def ensure_servers_profile_aware() -> Optional[ProfileAwareMCPServers]:
    """Ensure tools.mcp_tool._servers is backed by ProfileAwareMCPServers."""
    _mcp_mod = sys.modules.get("tools.mcp_tool")
    if _mcp_mod is None:
        try:
            import tools.mcp_tool as _mcp_mod
        except Exception:
            return None

    current_servers = getattr(_mcp_mod, "_servers", None)
    if isinstance(current_servers, ProfileAwareMCPServers):
        return current_servers

    if isinstance(current_servers, dict) and not ("mock" in type(current_servers).__module__):
        new_servers = ProfileAwareMCPServers(initial=current_servers)
        setattr(_mcp_mod, "_servers", new_servers)
        return new_servers

    return None


def get_profile_discovered_inventory(profile: str) -> Dict[str, Any]:
    """Return cached discovery results (tools, servers) for a profile."""
    profile_str = str(profile or "default")
    with _INVENTORY_LOCK:
        return dict(_PROFILE_MCP_INVENTORY.get(profile_str, {"tools": [], "servers": {}}))


def set_profile_discovery_inventory(
    profile: str,
    tools: List[Dict[str, Any]],
    servers: Dict[str, Any],
) -> None:
    """Store the real discovery results for a profile."""
    profile_str = str(profile or "default")
    with _INVENTORY_LOCK:
        _PROFILE_MCP_INVENTORY[profile_str] = {
            "tools": list(tools or []),
            "servers": dict(servers or {}),
        }


def invalidate_profile_mcp_server(profile: str, server_name: str) -> None:
    """Invalidate cache and worker for this profile upon configuration changes."""
    ProfileMCPManager.get_instance().invalidate_profile(profile)


def discover_profile_mcp_tools(
    profile_name: str,
    profile_home_path: Path,
    profile_env: Optional[Dict[str, str]] = None,
) -> None:
    """Trigger profile-isolated discovery through the persistent worker."""
    mgr = ProfileMCPManager.get_instance()
    mgr.discover_for_profile(profile_name, profile_home_path, profile_env)

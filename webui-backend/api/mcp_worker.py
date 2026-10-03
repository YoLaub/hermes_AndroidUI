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
import asyncio
import concurrent.futures
import json
import logging
import os
import re
import sys
import threading
from contextlib import AsyncExitStack
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Set

from api.mcp_errors import ResultUnknownError

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


def _result_to_text(result: Any) -> str:
    """Flatten an SDK CallToolResult into text; tool-level errors raise."""
    parts: List[str] = []
    for item in getattr(result, "content", None) or []:
        if getattr(item, "type", None) == "text":
            parts.append(str(item.text))
        else:
            parts.append(item.model_dump_json(exclude_none=True))
    text = "\n".join(parts)
    if not text:
        structured = getattr(result, "structuredContent", None)
        if structured is not None:
            text = json.dumps(structured)
    if getattr(result, "isError", False):
        raise RuntimeError(f"MCP tool call error: {text or 'tool reported an error'}")
    return text


def _exc_message(exc: BaseException) -> str:
    """Return the innermost message of an (anyio) exception group."""
    while isinstance(exc, BaseExceptionGroup) and exc.exceptions:
        exc = exc.exceptions[0]
    return str(exc) or type(exc).__name__


# JSON-RPC codes the SDK uses for "connection closed" and "request timed out":
# the request left, but no answer came back, so the outcome is unknown.
_NO_ANSWER_CODES = {-32000, 408}


def _is_server_reported_error(exc: BaseException) -> bool:
    """True when the server itself answered with an error (outcome is known)."""
    code = getattr(getattr(exc, "error", None), "code", None)
    return code is not None and code not in _NO_ANSWER_CODES


class _AsyncRunner:
    """One asyncio loop on a daemon thread.

    The MCP SDK is async while the IPC loop below is a plain blocking
    stdin reader, so every SDK coroutine is submitted to this loop.
    """

    def __init__(self) -> None:
        self.loop = asyncio.new_event_loop()
        threading.Thread(target=self._run, name="mcp-sdk-loop", daemon=True).start()

    def _run(self) -> None:
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def submit(self, coro: Any) -> "concurrent.futures.Future":
        return asyncio.run_coroutine_threadsafe(coro, self.loop)

    def run(self, coro: Any, timeout: Optional[float] = None) -> Any:
        fut = self.submit(coro)
        try:
            return fut.result(timeout)
        except concurrent.futures.TimeoutError:
            if fut.done():
                raise  # raised by the coroutine itself: keep its own message
            fut.cancel()
            raise TimeoutError(f"MCP operation timed out after {timeout}s") from None


_RUNNER: Optional[_AsyncRunner] = None
_RUNNER_LOCK = threading.Lock()


def _get_runner() -> _AsyncRunner:
    global _RUNNER
    with _RUNNER_LOCK:
        if _RUNNER is None:
            _RUNNER = _AsyncRunner()
        return _RUNNER


def _create_http_client(headers: Dict[str, str]) -> Any:
    """httpx client carrying the configured headers (mcp 1.x and 2.x)."""
    try:
        from mcp.shared._httpx_utils import create_mcp_http_client
    except ImportError:
        from mcp.client.streamable_http import create_mcp_http_client
    return create_mcp_http_client(headers=headers or None)


class BaseMCPServerConnection:
    """Persistent MCP server connection backed by the official MCP SDK.

    A dedicated task owns the transport and the ClientSession for the whole
    lifetime of the connection (anyio scopes must be exited by the task that
    entered them); calls are issued on that same live session.
    """

    transport = ""

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
        self._session: Any = None
        self._task: Optional["asyncio.Task"] = None
        self._stop: Optional[asyncio.Event] = None

    # -- transport hook ---------------------------------------------------
    def _has_target(self) -> bool:
        raise NotImplementedError

    async def _open_transport(self, stack: AsyncExitStack) -> Any:
        """Enter the SDK transport on `stack` and return its (read, write)."""
        raise NotImplementedError

    # -- lifecycle --------------------------------------------------------
    async def _list_all_tools(self, session: Any) -> List[dict]:
        from mcp import types

        raw: List[dict] = []
        result = await session.list_tools()
        while True:
            raw.extend(t.model_dump(mode="json", by_alias=True, exclude_none=True) for t in result.tools)
            cursor = getattr(result, "nextCursor", None)
            if not cursor:
                return raw
            result = await session.list_tools(params=types.PaginatedRequestParams(cursor=cursor))

    async def _lifecycle(self, ready: "asyncio.Future") -> None:
        from mcp import ClientSession

        try:
            async with AsyncExitStack() as stack:
                streams = await self._open_transport(stack)
                session = await stack.enter_async_context(ClientSession(streams[0], streams[1]))
                await session.initialize()
                raw_tools = await self._list_all_tools(session)
                self.tools = _filter_and_format_tools(
                    self.name, raw_tools, self.include_tools, self.exclude_tools
                )
                self._session = session
                self.connected = True
                if not ready.done():
                    ready.set_result(None)
                await self._stop.wait()
        except BaseException as exc:  # noqa: BLE001 - reported to the waiting caller
            if not ready.done():
                ready.set_exception(ConnectionError(_exc_message(exc)))
            elif not isinstance(exc, asyncio.CancelledError):
                logger.warning("MCP server '%s' connection ended: %s", self.name, _exc_message(exc))
        finally:
            self.connected = False
            self._session = None

    async def _start(self) -> None:
        loop = asyncio.get_running_loop()
        ready: asyncio.Future = loop.create_future()
        self._stop = asyncio.Event()
        self._task = loop.create_task(self._lifecycle(ready))
        try:
            await asyncio.wait_for(asyncio.shield(ready), self.connect_timeout)
        except asyncio.TimeoutError:
            await self._stop_async()
            raise ConnectionError(f"Timed out after {self.connect_timeout}s connecting to '{self.name}'") from None
        except BaseException:
            await self._stop_async()
            raise

    async def _stop_async(self) -> None:
        task, stop = self._task, self._stop
        self._task = None
        if stop is not None:
            stop.set()
        if task is not None:
            try:
                await asyncio.wait_for(task, 3)
            except BaseException:  # noqa: BLE001 - best effort teardown
                task.cancel()
        self.connected = False
        self._session = None

    def connect(self) -> None:
        self.disconnect()
        if not self.enabled or not self._has_target():
            self.tools = []
            return
        try:
            _get_runner().run(self._start(), timeout=self.connect_timeout + 5)
        except ImportError as exc:
            raise ConnectionError(f"MCP SDK is not installed in the worker interpreter: {exc}") from exc

    def disconnect(self) -> None:
        if self._task is not None:
            try:
                _get_runner().run(self._stop_async(), timeout=6)
            except Exception:
                pass
        self.connected = False
        self.tools = []

    # -- calls ------------------------------------------------------------
    def _find_tool(self, tool_name: str) -> dict:
        """Exact match on this server's discovered (post-filter) tools only."""
        for t in self.tools:
            if tool_name in (t.get("original_name"), t.get("name")):
                return t
        raise PermissionError(
            f"MCP tool '{tool_name}' is not available on server '{self.name}'"
        )

    async def _call(self, tool: str, arguments: dict) -> Any:
        session = self._session
        if session is None:
            raise ConnectionError(f"MCP server '{self.name}' is not connected")  # nothing was sent
        try:
            return await asyncio.wait_for(session.call_tool(tool, arguments or {}), self.timeout)
        except asyncio.TimeoutError:
            raise ResultUnknownError(
                f"MCP tool '{tool}' on server '{self.name}' timed out after {self.timeout}s; "
                "outcome unknown, not retried"
            ) from None
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            if _is_server_reported_error(exc):
                raise RuntimeError(f"MCP tool call error: {_exc_message(exc)}") from exc
            # The request was sent but no answer came back: drop the dead transport
            # so the next call reconnects, and never replay this one.
            if self._stop is not None:
                self._stop.set()
            raise ResultUnknownError(
                f"Connection to MCP server '{self.name}' lost during '{tool}': {_exc_message(exc)}; "
                "outcome unknown, not retried"
            ) from exc

    def call_tool(
        self,
        tool_name: str,
        arguments: dict,
        on_submit: Optional[Callable[["concurrent.futures.Future"], None]] = None,
    ) -> Any:
        if not self.connected:
            self.connect()
        original = self._find_tool(tool_name).get("original_name") or tool_name
        fut = _get_runner().submit(self._call(original, arguments))
        if on_submit is not None:
            on_submit(fut)
        try:
            # Backstop only: _call enforces self.timeout itself, a bit earlier.
            result = fut.result(self.timeout + 5)
        except concurrent.futures.CancelledError:
            raise ResultUnknownError(
                f"MCP tool '{original}' on server '{self.name}' was cancelled; outcome unknown, not retried"
            ) from None
        except TimeoutError:
            if not fut.done():
                fut.cancel()
                raise ResultUnknownError(
                    f"MCP tool '{original}' on server '{self.name}' timed out after {self.timeout}s; "
                    "outcome unknown, not retried"
                ) from None
            raise
        return _result_to_text(result)

    def get_status(self) -> dict:
        status = {
            "name": self.name,
            "transport": self.transport,
            "enabled": self.enabled,
            "active": self.connected,
            "status": "active" if self.connected else ("disabled" if not self.enabled else "configured"),
            "tool_count": len(self.tools) if self.connected else 0,
            "timeout": self.timeout,
            "connect_timeout": self.connect_timeout,
        }
        status.update(self._status_extra())
        return status

    def _status_extra(self) -> dict:
        return {}


class HTTPMCPServerConnection(BaseMCPServerConnection):
    """Streamable-HTTP server: the SDK keeps the MCP session id across calls."""

    transport = "http"

    def __init__(self, name: str, cfg: dict):
        super().__init__(name, cfg)
        self.url = str(cfg.get("url", "")).strip()
        self.headers = {str(k): str(v) for k, v in (cfg.get("headers") or {}).items()}

    def _has_target(self) -> bool:
        return bool(self.url)

    async def _open_transport(self, stack: AsyncExitStack) -> Any:
        from mcp.client.streamable_http import streamable_http_client

        http_client = await stack.enter_async_context(_create_http_client(self.headers))
        return await stack.enter_async_context(streamable_http_client(self.url, http_client=http_client))

    def _status_extra(self) -> dict:
        return {"url": self.url}


class StdioMCPServerConnection(BaseMCPServerConnection):
    """Stdio subprocess server, spawned and framed by the SDK."""

    transport = "stdio"

    def __init__(self, name: str, cfg: dict):
        super().__init__(name, cfg)
        self.command = str(cfg.get("command", "")).strip()
        self.args = [str(a) for a in (cfg.get("args") or [])]
        self.env = {str(k): str(v) for k, v in (cfg.get("env") or {}).items()}
        self.cwd = cfg.get("cwd")

    def _has_target(self) -> bool:
        return bool(self.command)

    async def _open_transport(self, stack: AsyncExitStack) -> Any:
        from mcp import StdioServerParameters
        from mcp.client.stdio import stdio_client

        # No env configured -> the SDK passes only its safe default variables,
        # so this profile's other secrets do not leak into the child.
        params = StdioServerParameters(
            command=self.command,
            args=self.args,
            env=self.env or None,
            cwd=str(self.cwd) if self.cwd else None,
        )
        return await stack.enter_async_context(stdio_client(params))

    def _status_extra(self) -> dict:
        return {"command": self.command, "args": self.args}


class ProfileWorkerEngine:
    """Manages server connections and dispatches tool calls for a profile."""

    def __init__(self, profile_name: str, home_path: Path):
        self.profile_name = profile_name
        self.home_path = home_path
        self.servers: Dict[str, BaseMCPServerConnection] = {}
        self._calls_lock = threading.Lock()
        self._pending_ids: Set[str] = set()
        self._inflight: Dict[str, "concurrent.futures.Future"] = {}
        self._cancel_requested: Set[str] = set()

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

    def call_tool(self, server_name: str, tool_name: str, arguments: dict, call_id: Optional[str] = None) -> Any:
        """Execute a tool call on the exact (server, tool) pair, never by tool name alone."""
        conn = self.servers.get(server_name)
        if conn is None:
            raise KeyError(f"MCP server '{server_name}' not found for profile '{self.profile_name}'")
        on_submit = None
        if call_id:
            with self._calls_lock:
                self._pending_ids.add(call_id)

            def on_submit(fut: "concurrent.futures.Future") -> None:
                with self._calls_lock:
                    self._inflight[call_id] = fut
                    cancelled = call_id in self._cancel_requested
                if cancelled:
                    fut.cancel()

        try:
            return conn.call_tool(tool_name, arguments, on_submit)
        finally:
            if call_id:
                with self._calls_lock:
                    self._pending_ids.discard(call_id)
                    self._inflight.pop(call_id, None)
                    self._cancel_requested.discard(call_id)

    def cancel(self, call_id: str) -> bool:
        """Cancel a call the parent stopped waiting for. True if it was still running."""
        with self._calls_lock:
            fut = self._inflight.get(call_id)
            if fut is None:
                if call_id in self._pending_ids:
                    self._cancel_requested.add(call_id)  # not submitted yet: cancel on submit
                    return True
                return False
        fut.cancel()
        return True

    def get_status(self) -> dict:
        with self._calls_lock:
            in_flight = len(self._inflight)
        return {
            "ok": True,
            "profile": self.profile_name,
            "in_flight": in_flight,
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

    out_lock = threading.Lock()

    def emit(resp: dict) -> None:
        with out_lock:
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()

    def run_call(req: dict) -> None:
        req_id = req.get("id")
        try:
            result = engine.call_tool(
                req.get("server") or "", req.get("tool") or "", req.get("arguments") or {}, call_id=req_id
            )
            resp = {"id": req_id, "ok": True, "result": result}
        except Exception as exc:
            resp = {"id": req_id, "ok": False, "error": str(exc), "error_type": type(exc).__name__}
        emit(resp)

    # Read IPC commands from stdin. Tool calls run on their own threads so this
    # loop keeps reading and can honour a `cancel` while a call is in flight.
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
            if action == "call_tool":
                threading.Thread(target=run_call, args=(req,), name=f"mcp-call-{req_id}", daemon=True).start()
                continue
            if action == "ping":
                resp = {"id": req_id, "ok": True}
            elif action == "discover" or action == "reload":
                res = engine.reload()
                resp = {"id": req_id, **res}
            elif action == "status":
                res = engine.get_status()
                resp = {"id": req_id, **res}
            elif action == "cancel":
                resp = {"id": req_id, "ok": True, "cancelled": engine.cancel(str(req.get("target") or ""))}
            elif action == "shutdown":
                engine.shutdown()
                emit({"id": req_id, "ok": True})
                break
            else:
                resp = {"id": req_id, "ok": False, "error": f"Unknown action '{action}'"}
        except Exception as exc:
            resp = {"id": req_id, "ok": False, "error": str(exc), "error_type": type(exc).__name__}

        emit(resp)

    engine.shutdown()
    logger.info("Worker exiting cleanly for profile '%s'", args.profile)


if __name__ == "__main__":
    main()

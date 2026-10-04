"""
End-to-end: the real hermes-agent running inside the real WebUI server calls an
MCP tool through the profile worker, SDK client and a local MCP server.

Everything is local: a fake OpenAI-compatible LLM, local MCP test servers and an
isolated HERMES_HOME / state dir. No mobile relay, no phone action.
Skipped when hermes-agent (`run_agent`) is not importable by this interpreter.
"""

import json
import os
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import pytest

pytest.importorskip("run_agent", reason="needs the real hermes-agent in this interpreter")

from test_mcp_worker_sdk import FIXTURES, HttpFixture, _free_port  # noqa: E402

BACKEND = Path(__file__).parent.parent.resolve()
pytestmark = pytest.mark.integration


def _wait_port(port: int, timeout: float = 30.0) -> None:
    end = time.time() + timeout
    while time.time() < end:
        try:
            socket.create_connection(("127.0.0.1", port), timeout=0.2).close()
            return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError(f"port {port} never opened")


def _write_profile(root: Path, name: str, llm_port: int, mcp_url: str, token_var: str, token: str,
                   vision: bool = False) -> None:
    prof = root / "profiles" / name
    prof.mkdir(parents=True)
    (prof / "config.yaml").write_text(
        f"model:\n  default: fake-model\n  provider: custom\n"
        f"  base_url: http://127.0.0.1:{llm_port}/v1\n  api_key: sk-fake\n"
        + ("  supports_vision: true\n" if vision else "")
        + ""
        f"mcp_servers:\n  relay:\n    url: {mcp_url}\n    headers:\n"
        f"      Authorization: Bearer ${{{token_var}}}\n",
        encoding="utf-8",
    )
    (prof / ".env").write_text(f"{token_var}={token}\n", encoding="utf-8")


def _chat(base: str, profile: str, workspace: Path, message: str) -> list:
    headers = {"Content-Type": "application/json", "X-Hermes-Profile": profile,
               "Cookie": f"hermes_profile={profile}"}

    def post(path, body):
        req = urllib.request.Request(base + path, data=json.dumps(body).encode(), headers=headers)
        return json.loads(urllib.request.urlopen(req, timeout=60).read())

    sess = post("/api/session/new", {"profile": profile, "workspace": str(workspace), "model": "fake-model"})
    sid = (sess.get("session") or sess)["session_id"]
    started = post("/api/chat/start", {"session_id": sid, "message": message, "profile": profile,
                                       "workspace": str(workspace), "model": "fake-model"})
    events, current = [], None
    req = urllib.request.Request(f"{base}/api/chat/stream?stream_id={started['stream_id']}", headers=headers)
    deadline = time.time() + 90
    with urllib.request.urlopen(req, timeout=90) as resp:
        for raw in resp:
            line = raw.decode().rstrip("\n")
            if line.startswith("event:"):
                current = line[6:].strip()
            elif line.startswith("data:"):
                events.append((current, json.loads(line[5:].strip() or "{}")))
                if current in ("done", "error", "apperror") or time.time() > deadline:
                    break
    return events


def _run_stack(tmp_path, tool="mcp_relay_echo", vision=False):
    procs = []
    home, state, ws = tmp_path / "home", tmp_path / "state", tmp_path / "ws"
    for d in (home, state, ws):
        d.mkdir()
    john = HttpFixture(tmp_path, token="tok_john", prefix="[JOHN-MCP] ")
    alice = HttpFixture(tmp_path, token="tok_alice", prefix="[ALICE-MCP] ")
    llm_port, web_port = _free_port(), _free_port()
    llm_log = tmp_path / "llm.log"
    llm_log.write_text("")
    procs.append(subprocess.Popen(
        [sys.executable, str(FIXTURES / "fake_llm.py"), str(llm_port), str(llm_log), tool],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
    _wait_port(llm_port)
    (home / "config.yaml").write_text(
        f"model:\n  default: fake-model\n  provider: custom\n"
        f"  base_url: http://127.0.0.1:{llm_port}/v1\n  api_key: sk-fake\n"
        + ("  supports_vision: true\n" if vision else ""), encoding="utf-8")
    _write_profile(home, "john", llm_port, john.url, "J_TOKEN", "tok_john", vision=vision)
    _write_profile(home, "alice", llm_port, alice.url, "A_TOKEN", "tok_alice")

    env = {k: v for k, v in os.environ.items() if k not in ("J_TOKEN", "A_TOKEN")}
    env.update({"HERMES_HOME": str(home), "HERMES_WEBUI_STATE_DIR": str(state),
                "HERMES_WEBUI_PORT": str(web_port), "HERMES_WEBUI_HOST": "127.0.0.1",
                "HERMES_WEBUI_PYTHON": sys.executable, "HERMES_WEBUI_DEFAULT_WORKSPACE": str(ws)})
    server_log = open(tmp_path / "server.log", "w")
    procs.append(subprocess.Popen([sys.executable, "server.py"], cwd=str(BACKEND), env=env,
                                  stdout=server_log, stderr=subprocess.STDOUT))
    try:
        _wait_port(web_port)
        yield {"base": f"http://127.0.0.1:{web_port}", "ws": ws, "llm_log": llm_log}
    finally:
        for p in procs:
            p.terminate()
        john.stop()
        alice.stop()
        server_log.close()


@pytest.fixture
def stack(tmp_path):
    yield from _run_stack(tmp_path)


@pytest.fixture
def stack_snap_vision(tmp_path):
    yield from _run_stack(tmp_path, tool="mcp_relay_snap", vision=True)


def test_real_agent_calls_mcp_tool_of_its_own_profile(stack):
    results = {}
    for profile in ("john", "alice"):
        events = _chat(stack["base"], profile, stack["ws"], "call the relay echo tool")
        kinds = [e for e, _ in events]
        assert "apperror" not in kinds and "error" not in kinds, events
        started = [d for e, d in events if e == "tool" and d.get("event_type") == "tool.started"]
        completed = [d for e, d in events if e == "tool_complete"]
        assert started and started[0]["name"] == "mcp_relay_echo"
        assert completed and completed[0]["is_error"] is False
        results[profile] = "".join(d.get("text", "") for e, d in events if e == "token")

    # Same server name and tool name in both profiles, each answered by its own server.
    assert "RESULT=[JOHN-MCP] from-agent" in results["john"]
    assert "RESULT=[ALICE-MCP] from-agent" in results["alice"]


def test_real_agent_hands_a_screenshot_to_a_vision_model_as_an_image(stack_snap_vision):
    s = stack_snap_vision
    events = _chat(s["base"], "john", s["ws"], "take a screenshot")
    kinds = [e for e, _ in events]
    assert "apperror" not in kinds and "error" not in kinds, events
    answer = "".join(d.get("text", "") for e, d in events if e == "token")
    # The fake model reports what the tool message it received contained.
    assert "IMAGE_PARTS=1" in answer, answer
    assert "[JOHN-MCP] caption" in answer, answer

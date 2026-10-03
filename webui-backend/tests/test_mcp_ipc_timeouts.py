"""
IPC / MCP timeout alignment and cancellation, with a real slow tool.

The parent's wait must cover the MCP call timeout plus a transmission margin, a
parent that gives up must cancel explicitly, and a call whose outcome is unknown
must never be replayed (the slow tool logs each real execution to a file).
"""

import sys
import time
from pathlib import Path
from unittest.mock import patch

import pytest
import yaml

_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from test_mcp_worker_sdk import FIXTURES

from api import mcp_isolated
from api.mcp_errors import ResultUnknownError
from api.mcp_isolated import ProfileMCPManager


@pytest.fixture
def slow_profile(tmp_path):
    """Yield (manager, call_log, start) where start(timeout) discovers a stdio server."""
    call_log = tmp_path / "calls.log"
    call_log.write_text("")
    home = tmp_path / "slowp"
    home.mkdir()
    mgr = ProfileMCPManager.get_instance()
    mgr.shutdown_all()

    def start(timeout: int):
        cfg = {"mcp_servers": {"slowsrv": {
            "command": sys.executable,
            "args": [str(FIXTURES / "stdio_server.py")],
            "env": {"FIXTURE_PREFIX": "[S] ", "FIXTURE_CALL_LOG": str(call_log)},
            "timeout": timeout,
        }}}
        (home / "config.yaml").write_text(yaml.safe_dump(cfg), encoding="utf-8")
        patchers = [
            patch("api.config.get_config", side_effect=lambda profile=None, **_: cfg),
            patch("api.profiles.get_hermes_home_for_profile", side_effect=lambda p: home),
        ]
        for p in patchers:
            p.start()
        start.patchers = patchers
        assert mgr.discover_for_profile("slowp", home)["ok"] is True

    start.patchers = []
    yield mgr, call_log, start
    for p in start.patchers:
        p.stop()
    mgr.shutdown_all()


def _runs(call_log: Path) -> int:
    return len([l for l in call_log.read_text().splitlines() if l.strip()])


def test_parent_wait_covers_the_mcp_timeout_plus_a_margin(slow_profile):
    mgr, _, start = slow_profile
    start(timeout=90)
    assert mgr._call_wait_timeout("slowp", "slowsrv") == 90 + mcp_isolated.IPC_MARGIN_SECONDS
    assert mcp_isolated.IPC_MARGIN_SECONDS > 0
    # Unknown server: fall back to the worker's default MCP timeout, still plus the margin.
    assert mgr._call_wait_timeout("slowp", "nope") == (
        mcp_isolated.DEFAULT_MCP_CALL_TIMEOUT + mcp_isolated.IPC_MARGIN_SECONDS
    )


def test_slow_tool_within_the_mcp_timeout_succeeds_and_runs_exactly_once(slow_profile):
    mgr, call_log, start = slow_profile
    start(timeout=20)
    assert mgr.call_tool("slowp", "slowsrv", "slow", {"seconds": 2}) == "[S] slept"
    time.sleep(1)
    assert _runs(call_log) == 1


def test_mcp_timeout_reports_unknown_outcome_and_is_never_replayed(slow_profile):
    mgr, call_log, start = slow_profile
    start(timeout=2)
    t0 = time.time()
    with pytest.raises(ResultUnknownError) as exc:
        mgr.call_tool("slowp", "slowsrv", "slow", {"seconds": 5})
    assert time.time() - t0 < 8, "the MCP timeout, not a parent timeout, ends the call"
    assert "timed out" in str(exc.value).lower()
    time.sleep(4)  # long enough for any automatic retry to have re-run the tool
    assert _runs(call_log) == 1


def test_parent_giving_up_cancels_explicitly_and_does_not_replay(slow_profile):
    mgr, call_log, start = slow_profile
    start(timeout=30)
    with patch.object(ProfileMCPManager, "_call_wait_timeout", return_value=1.0):
        t0 = time.time()
        with pytest.raises(ResultUnknownError) as exc:
            mgr.call_tool("slowp", "slowsrv", "slow", {"seconds": 6})
        assert time.time() - t0 < 4
    assert "outcome" in str(exc.value).lower()

    # The worker must have dropped the call well before the tool would have finished.
    deadline = time.time() + 2
    while time.time() < deadline:
        if mgr.get_status_for_profile("slowp").get("in_flight") == 0:
            break
        time.sleep(0.1)
    assert mgr.get_status_for_profile("slowp").get("in_flight") == 0, "explicit cancel not honoured"

    # Same worker still serves calls, and the abandoned action was never re-run.
    assert mgr.call_tool("slowp", "slowsrv", "echo", {"message": "after"}) == "[S] after"
    time.sleep(1)
    assert _runs(call_log) == 1

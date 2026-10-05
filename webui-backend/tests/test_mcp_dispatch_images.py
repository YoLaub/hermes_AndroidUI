"""The registered tool hands a screenshot to the routing rule with the ACTIVE profile's own config."""
import sys
import types
from pathlib import Path
from unittest.mock import patch

import pytest
import yaml

_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from api.mcp_isolated import ProfileMCPManager
from api.profiles import clear_request_profile, set_request_profile

FIXTURES = Path(__file__).parent / "mcp_fixtures"


class StrictRegistry:
    """Same contract as the real hermes-agent registry: register(name, toolset, schema, handler)."""

    def __init__(self):
        self.handlers = {}

    def register(self, name, toolset, schema, handler, **_):
        self.handlers[name] = handler

    def call(self, name, args, **ctx):
        return self.handlers[name](args, **ctx)


def _profile(tmp_path, name, model, supports_vision=None):
    home = tmp_path / name
    home.mkdir()
    model_cfg = {"provider": "custom", "default": model}
    if supports_vision is not None:
        model_cfg["supports_vision"] = supports_vision
    cfg = {
        "model": model_cfg,
        "mcp_servers": {"cam": {"command": sys.executable, "args": [str(FIXTURES / "stdio_server.py")],
                                "env": {"FIXTURE_PREFIX": f"[{name}] "}}},
    }
    (home / "config.yaml").write_text(yaml.safe_dump(cfg), encoding="utf-8")
    return home, cfg


@pytest.fixture
def two_profiles(tmp_path):
    # John's model is declared able to read images, so Hermes's rule keeps the native path (with or without
    # the real agent installed); Alice declares nothing.
    john_home, john_cfg = _profile(tmp_path, "john", "model-of-john", supports_vision=True)
    alice_home, alice_cfg = _profile(tmp_path, "alice", "model-of-alice")
    homes = {"john": john_home, "alice": alice_home}
    cfgs = {"john": john_cfg, "alice": alice_cfg}
    registry = StrictRegistry()
    tools_mod, reg_mod = types.ModuleType("tools"), types.ModuleType("tools.registry")
    reg_mod.registry = registry
    tools_mod.registry = reg_mod
    mgr = ProfileMCPManager.get_instance()
    mgr.shutdown_all()
    with patch.dict("sys.modules", {"tools": tools_mod, "tools.registry": reg_mod}), \
         patch("api.config.get_config", side_effect=lambda profile=None, **_: cfgs[profile]), \
         patch("api.profiles.get_hermes_home_for_profile", side_effect=lambda p: homes[p]):
        for p in homes:
            assert mgr.discover_for_profile(p, homes[p])["ok"] is True
        yield registry, cfgs
    mgr.shutdown_all()


def test_a_model_declared_able_to_read_images_gets_the_envelope(two_profiles):
    registry, _ = two_profiles
    set_request_profile("john")
    try:
        out = registry.call("mcp_cam_snap", {}, task_id="t")
    finally:
        clear_request_profile()
    assert isinstance(out, dict) and out["_multimodal"] is True
    assert out["content"][1]["image_url"]["url"].startswith("data:image/jpeg;base64,")


def test_each_call_is_routed_with_its_own_profiles_config(two_profiles):
    registry, cfgs = two_profiles
    seen = []

    def fake_route(result, cfg, **_):
        seen.append(cfg["model"]["default"])
        return f"ROUTED for {cfg['model']['default']}"

    with patch("api.mcp_isolated.route_screenshot_result", fake_route):
        for profile in ("john", "alice", "john"):
            set_request_profile(profile)
            try:
                out = registry.call("mcp_cam_snap", {}, task_id="t")
            finally:
                clear_request_profile()
            assert out == f"ROUTED for model-of-{profile}"
    assert seen == ["model-of-john", "model-of-alice", "model-of-john"]


def test_a_text_tool_is_not_touched_by_the_routing(two_profiles):
    registry, _ = two_profiles
    set_request_profile("john")
    try:
        out = registry.call("mcp_cam_echo", {"message": "hi"}, task_id="t")
    finally:
        clear_request_profile()
    assert out == "[john] hi"

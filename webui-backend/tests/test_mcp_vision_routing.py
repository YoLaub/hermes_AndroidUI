"""
Where a screenshot goes once the worker returned it: straight to the model when it can read images,
otherwise to an auxiliary vision model that describes it as text, and never as raw image to a model
that cannot read it. Hermes's own rule decides; the temporary file the auxiliary path needs is private
and removed at once.
"""
import asyncio
import os
import stat
import sys
import types
from pathlib import Path

_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from api.mcp_vision import main_route_from_cfg, route_screenshot_result

B64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//AA=="
ENVELOPE = {
    "_multimodal": True,
    "content": [
        {"type": "text", "text": "Capture 720x1600 - [el_1] Button: Commenter"},
        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{B64}"}},
    ],
    "text_summary": "Capture 720x1600 - [el_1] Button: Commenter\n[1 image(s) attached, not readable here]",
}
CFG = {"model": {"provider": "custom", "default": "fake-model"}}


def envelope():
    return {**ENVELOPE, "content": [dict(c) for c in ENVELOPE["content"]]}


def never(*_a, **_k):
    raise AssertionError("must not be called")


# ── what is not a screenshot is left alone ───────────────────────────────────

def test_a_plain_string_is_returned_untouched_and_nothing_is_asked():
    assert route_screenshot_result("hello", CFG, should_route=never, describe=never) == "hello"


def test_an_envelope_without_image_is_returned_untouched():
    env = {"_multimodal": True, "content": [{"type": "text", "text": "x"}], "text_summary": "x"}
    assert route_screenshot_result(env, CFG, should_route=never, describe=never) is env


# ── native: the main model reads the image ───────────────────────────────────

def test_a_vision_capable_model_gets_the_envelope_as_is():
    seen = {}

    def should_route(provider, model, cfg):
        seen.update(provider=provider, model=model, cfg=cfg)
        return False

    env = envelope()
    assert route_screenshot_result(env, CFG, should_route=should_route, describe=never) == env
    assert seen["provider"] == "custom" and seen["model"] == "fake-model" and seen["cfg"] is CFG


# ── auxiliary: text only reaches the main model ──────────────────────────────

def test_a_text_model_gets_a_description_and_never_the_image():
    got = {}

    def describe(images, prompt):
        got["images"], got["prompt"] = images, prompt
        return "A LinkedIn feed with a Comment button."

    out = route_screenshot_result(envelope(), CFG, should_route=lambda *_: True, describe=describe)
    assert isinstance(out, str)
    assert "A LinkedIn feed with a Comment button." in out
    assert "[el_1] Button: Commenter" in out, "the element list stays useful"
    assert "data:image" not in out and B64 not in out
    assert got["images"] == [("image/jpeg", B64)]
    assert "[el_1] Button: Commenter" in got["prompt"], "the analysis gets the element list to cross-reference"


def test_several_images_are_each_described_by_the_default_describer(monkeypatch):
    replies = iter(['{"success": true, "analysis": "first"}', '{"success": true, "analysis": "second"}'])
    seen = []

    async def vision_analyze_tool(path, prompt, *a, **k):
        seen.append(Path(path).suffix)
        return next(replies)

    vt = types.ModuleType("tools.vision_tools")
    vt.vision_analyze_tool = vision_analyze_tool
    mt = types.ModuleType("model_tools")
    mt._run_async = lambda coro: asyncio.run(coro)
    monkeypatch.setitem(sys.modules, "tools", types.ModuleType("tools"))
    monkeypatch.setitem(sys.modules, "tools.vision_tools", vt)
    monkeypatch.setitem(sys.modules, "model_tools", mt)

    env = envelope()
    env["content"].append({"type": "image_url", "image_url": {"url": f"data:image/png;base64,{B64}"}})
    out = route_screenshot_result(env, CFG, should_route=lambda *_: True)
    assert "Image 1: first" in out and "Image 2: second" in out and "data:image" not in out
    assert seen == [".jpg", ".png"]


def test_when_the_vision_model_fails_the_main_model_still_never_gets_the_raw_image():
    for failing in (lambda imgs, p: None, lambda imgs, p: (_ for _ in ()).throw(RuntimeError("vision node down"))):
        out = route_screenshot_result(envelope(), CFG, should_route=lambda *_: True, describe=failing)
        assert isinstance(out, str)
        assert "data:image" not in out and B64 not in out
        assert "vision" in out.lower()
        assert "[el_1] Button: Commenter" in out


# ── the routing decision itself ──────────────────────────────────────────────

def test_if_the_decision_cannot_be_made_the_envelope_goes_through_like_hermes_does():
    def broken(*_a):
        raise ImportError("no hermes here")

    env = envelope()
    assert route_screenshot_result(env, CFG, should_route=broken, describe=never) == env


def test_the_profiles_own_config_decides_provider_and_model():
    assert main_route_from_cfg({"model": {"provider": "OpenRouter", "default": "anthropic/claude"}}) == ("openrouter", "anthropic/claude")
    assert main_route_from_cfg({"model": "plain-model-name"}) == ("", "plain-model-name")
    assert main_route_from_cfg({"model": {"provider": "custom"}}) == ("custom", "")
    assert main_route_from_cfg({}) == ("", "")
    assert main_route_from_cfg(None) == ("", "")


def test_the_default_decision_is_hermes_own_rule(monkeypatch):
    calls = []
    fake = types.ModuleType("tools.computer_use.vision_routing")
    fake.should_route_capture_to_aux_vision = lambda p, m, c: calls.append((p, m, c)) or True
    for name, mod in (("tools", types.ModuleType("tools")),
                      ("tools.computer_use", types.ModuleType("tools.computer_use")),
                      ("tools.computer_use.vision_routing", fake)):
        monkeypatch.setitem(sys.modules, name, mod)
    out = route_screenshot_result(envelope(), CFG, describe=lambda imgs, p: "desc")
    assert calls == [("custom", "fake-model", CFG)]
    assert isinstance(out, str) and "desc" in out


# ── the auxiliary path's temporary file ──────────────────────────────────────

def _fake_vision_modules(monkeypatch, record):
    async def vision_analyze_tool(path, prompt, *a, **k):
        p = Path(path)
        record["path"] = p
        record["existed"] = p.exists()
        record["mode"] = stat.S_IMODE(p.stat().st_mode)
        record["dir_mode"] = stat.S_IMODE(p.parent.stat().st_mode)
        record["bytes"] = p.read_bytes()
        record["suffix"] = p.suffix
        return '{"success": true, "analysis": "A login form."}'

    vt = types.ModuleType("tools.vision_tools")
    vt.vision_analyze_tool = vision_analyze_tool
    mt = types.ModuleType("model_tools")
    mt._run_async = lambda coro: asyncio.run(coro)
    monkeypatch.setitem(sys.modules, "tools", types.ModuleType("tools"))
    monkeypatch.setitem(sys.modules, "tools.vision_tools", vt)
    monkeypatch.setitem(sys.modules, "model_tools", mt)


def test_the_default_description_uses_a_private_temp_file_and_deletes_it(monkeypatch):
    record = {}
    _fake_vision_modules(monkeypatch, record)
    out = route_screenshot_result(envelope(), CFG, should_route=lambda *_: True)
    assert "A login form." in out
    assert record["existed"] is True
    assert record["mode"] == 0o600 and record["dir_mode"] == 0o700
    assert record["suffix"] == ".jpg"
    assert record["bytes"].startswith(b"\xff\xd8\xff"), "the decoded JPEG"
    assert not record["path"].exists() and not record["path"].parent.exists(), "file and directory are gone"


def test_the_temp_file_is_deleted_even_when_the_vision_call_fails(monkeypatch):
    record = {}
    _fake_vision_modules(monkeypatch, record)

    async def boom(path, prompt, *a, **k):
        record["path"] = Path(path)
        raise RuntimeError("provider down")
    sys.modules["tools.vision_tools"].vision_analyze_tool = boom
    out = route_screenshot_result(envelope(), CFG, should_route=lambda *_: True)
    assert "data:image" not in out and B64 not in out
    assert not record["path"].exists() and not record["path"].parent.exists()
    assert os.path.basename(str(record["path"])).startswith("screenshot_")

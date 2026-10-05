"""
Images in MCP tool results: Hermes accepts a tool result that carries an image only as its multimodal
envelope. The worker used to flatten any non-text block into JSON text, which would put a base64 image in
the model's context as text. It must return the envelope instead, with hostile or oversized data refused.
Also covers SDK-compat: mcp 1.x names fields mimeType/isError/nextCursor, mcp 2.x mime_type/is_error/next_cursor.
"""
import asyncio
import sys
from pathlib import Path

import pytest
import yaml
from mcp import types

_BACKEND_DIR = Path(__file__).parent.parent.resolve()
if str(_BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(_BACKEND_DIR))

from api.mcp_worker import HTTPMCPServerConnection, ProfileWorkerEngine, _result_to_payload

FIXTURES = Path(__file__).parent / "mcp_fixtures"
JPEG_B64 = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//AA=="


def result(*blocks, is_error=False):
    return types.CallToolResult(content=list(blocks), isError=is_error)


def text(t):
    return types.TextContent(type="text", text=t)


def image(data=JPEG_B64, mime="image/jpeg"):
    return types.ImageContent(type="image", data=data, mimeType=mime)


# ── payload shape ────────────────────────────────────────────────────────────

def test_text_only_results_are_still_plain_strings():
    assert _result_to_payload(result(text("a"), text("b"))) == "a\nb"


def test_an_image_becomes_hermes_multimodal_envelope():
    payload = _result_to_payload(result(text("Capture 720x1600"), image()))
    assert payload["_multimodal"] is True
    assert payload["content"][0] == {"type": "text", "text": "Capture 720x1600"}
    assert payload["content"][1] == {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{JPEG_B64}"}}
    assert "Capture 720x1600" in payload["text_summary"]
    assert JPEG_B64 not in payload["text_summary"], "the text fallback must never carry the image"


def test_several_images_are_all_kept_in_order():
    payload = _result_to_payload(result(text("two"), image(mime="image/png"), image(mime="image/webp")))
    urls = [c["image_url"]["url"] for c in payload["content"] if c["type"] == "image_url"]
    assert urls[0].startswith("data:image/png;base64,") and urls[1].startswith("data:image/webp;base64,")


def test_an_image_only_result_still_has_some_text():
    payload = _result_to_payload(result(image()))
    assert payload["content"][0]["type"] == "text" and payload["content"][0]["text"]


# ── hostile or oversized data is refused, never injected into a data: URI ─────

@pytest.mark.parametrize("mime", [
    "image/png;base64,AAAA#", "text/html", "image/svg+xml", "image/jpeg\n", "application/javascript", "", "IMAGE/JPEG;x=1",
])
def test_an_unexpected_image_type_is_dropped_with_a_note(mime):
    payload = _result_to_payload(result(text("cap"), image(mime=mime)))
    assert isinstance(payload, str), "no usable image left: plain text"
    assert "data:" not in payload and JPEG_B64 not in payload
    assert "image" in payload.lower()


@pytest.mark.parametrize("data", ["not base64!", "AAAA\n<script>", "AA AA", "data:image/png;base64,AAAA"])
def test_malformed_base64_is_dropped(data):
    payload = _result_to_payload(result(text("cap"), image(data=data)))
    assert isinstance(payload, str) and "data:image" not in payload


def test_an_oversized_image_is_dropped():
    payload = _result_to_payload(result(text("cap"), image(data="A" * 3_000_000)))
    assert isinstance(payload, str) and len(payload) < 1000


def test_one_good_image_survives_next_to_a_bad_one():
    payload = _result_to_payload(result(image(mime="text/html"), image()))
    urls = [c["image_url"]["url"] for c in payload["content"] if c["type"] == "image_url"]
    assert urls == [f"data:image/jpeg;base64,{JPEG_B64}"]


# ── SDK compat: tool errors and pagination under both field-naming styles ─────

def test_a_tool_level_error_is_raised_not_returned_as_a_success():
    with pytest.raises(RuntimeError) as exc:
        _result_to_payload(result(text("boom happened"), is_error=True))
    assert "boom happened" in str(exc.value)


def test_an_error_result_never_forwards_an_image():
    with pytest.raises(RuntimeError) as exc:
        _result_to_payload(result(text("failed"), image(), is_error=True))
    assert JPEG_B64 not in str(exc.value)


def test_every_page_of_the_tool_list_is_read():
    class FakeSession:
        def __init__(self):
            self.pages = [
                types.ListToolsResult(tools=[types.Tool(name="a", inputSchema={"type": "object"})], nextCursor="c2"),
                types.ListToolsResult(tools=[types.Tool(name="b", inputSchema={"type": "object"})]),
            ]
            self.calls = 0

        async def list_tools(self, *, params=None, **_):
            page = self.pages[self.calls]
            self.calls += 1
            return page

    conn = HTTPMCPServerConnection("srv", {"url": "http://unused"})
    session = FakeSession()
    names = [t["name"] for t in asyncio.run(conn._list_all_tools(session))]
    assert names == ["a", "b"] and session.calls == 2


# ── through a real MCP server and the worker engine ──────────────────────────

def _engine(tmp_path, tools_filter=None):
    cfg = {"command": sys.executable, "args": [str(FIXTURES / "stdio_server.py")], "env": {"FIXTURE_PREFIX": "[S] "}}
    home = tmp_path / "p"
    home.mkdir()
    (home / "config.yaml").write_text(yaml.safe_dump({"mcp_servers": {"cam": cfg}}), encoding="utf-8")
    return ProfileWorkerEngine("p", home)


def test_a_real_server_image_reaches_the_caller_as_the_envelope(tmp_path):
    eng = _engine(tmp_path)
    try:
        assert eng.reload()["ok"] is True
        payload = eng.call_tool("cam", "snap", {})
    finally:
        eng.shutdown()
    assert payload["_multimodal"] is True
    assert payload["content"][0]["text"] == "[S] caption"
    assert payload["content"][1]["image_url"]["url"].startswith("data:image/jpeg;base64,")


def test_a_real_server_tool_failure_surfaces_as_an_error(tmp_path):
    eng = _engine(tmp_path)
    try:
        eng.reload()
        with pytest.raises(RuntimeError) as exc:
            eng.call_tool("cam", "broken", {})
    finally:
        eng.shutdown()
    # Newer SDKs hide the exception text behind "Error executing tool broken": only the failure itself is ours.
    assert "broken" in str(exc.value)

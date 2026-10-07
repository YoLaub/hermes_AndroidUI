"""
Which toolsets a WebUI session gets from a profile's config.

The rule that matters: a profile that lists nothing keeps the full default tool set it always had, and a profile that
restricts its tools (John, who controls a phone) never gets more than it lists.
Needs the real hermes-agent (hermes_cli); skipped otherwise.
"""
import pytest

pytest.importorskip("hermes_cli.tools_config", reason="needs the real hermes-agent in this interpreter")

from api.config import _resolve_cli_toolsets  # noqa: E402


def test_a_profile_with_no_toolset_list_keeps_the_full_default_set():
    tools = set(_resolve_cli_toolsets({}, platform="webui"))
    assert {"terminal", "file", "web"} <= tools
    assert "hermes-webui" not in tools      # an undefined composite name means no tools at all


def test_a_profile_with_no_platform_toolsets_but_other_settings_is_the_same():
    tools = set(_resolve_cli_toolsets({"model": {"default": "x"}}, platform="webui"))
    assert {"terminal", "file"} <= tools


def test_a_profile_that_only_lists_cli_uses_that_list():
    tools = set(_resolve_cli_toolsets({"platform_toolsets": {"cli": ["terminal", "file", "web"]}}, platform="webui"))
    assert {"terminal", "file", "web"} <= tools


def test_a_restricted_profile_never_gets_more_than_it_lists():
    john = {"platform_toolsets": {"api_server": ["browser", "web", "mcp-mobile"]},
            "mcp_servers": {"mobile": {"url": "http://relay/mcp"}}}
    tools = set(_resolve_cli_toolsets(john, platform="webui"))
    assert "mobile" in tools and "mcp-mobile" in tools and "web" in tools
    assert not ({"terminal", "file", "code_execution"} & tools)


def test_an_explicit_webui_list_wins_over_the_other_platforms():
    cfg = {"platform_toolsets": {"webui": ["terminal", "file", "web"], "api_server": ["web"], "cli": ["web"]}}
    tools = set(_resolve_cli_toolsets(cfg, platform="webui"))
    assert {"terminal", "file", "web"} <= tools

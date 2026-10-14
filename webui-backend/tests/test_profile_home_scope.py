"""
The agent library must see the session's profile home, not the process one.

hermes-agent finds a profile's memory plugin under get_hermes_home()/plugins, and get_hermes_home() only knows
os.environ and its own context-local override. The WebUI keeps HERMES_HOME in a private thread dict, so without the
override a `memtest` session looked for its plugin in the WebUI process home and never loaded it.
Runs against a stand-in hermes_constants so it does not need the real hermes-agent.
"""
import contextvars
import sys
import types

import pytest
from api import streaming


@pytest.fixture
def fake_constants(monkeypatch):
    var = contextvars.ContextVar("home", default=None)
    mod = types.ModuleType("hermes_constants")
    mod.set_hermes_home_override = lambda path: var.set(None if path is None else str(path))
    mod.reset_hermes_home_override = lambda token: var.reset(token)
    mod.current = var.get
    monkeypatch.setitem(sys.modules, "hermes_constants", mod)
    return mod


def test_the_library_sees_the_profile_home_until_the_run_ends(fake_constants):
    token = streaming._enter_profile_home("/data/profiles/memtest")
    assert fake_constants.current() == "/data/profiles/memtest"
    streaming._exit_profile_home(token)
    assert fake_constants.current() is None


def test_an_empty_profile_home_changes_nothing(fake_constants):
    token = streaming._enter_profile_home("")
    assert token is None
    assert fake_constants.current() is None
    streaming._exit_profile_home(token)


def test_a_library_without_the_override_does_not_break_the_run(monkeypatch):
    monkeypatch.setitem(sys.modules, "hermes_constants", types.ModuleType("hermes_constants"))
    assert streaming._enter_profile_home("/data/profiles/memtest") is None
    streaming._exit_profile_home(None)


# --- the profile's memory token, visible to the plugin only while the agent is being built ---

def test_the_memory_token_is_in_the_environment_only_while_the_agent_is_built(monkeypatch):
    monkeypatch.delenv("AGENT_MEMORY_TOKEN", raising=False)
    seen = {}

    class Agent:
        def __init__(self, **kwargs):
            seen["during"] = streaming.os.environ.get("AGENT_MEMORY_TOKEN")

    streaming._build_agent(Agent, {}, {"AGENT_MEMORY_TOKEN": "tok-memtest", "OPENAI_API_KEY": "sk-x"})
    assert seen["during"] == "tok-memtest"
    assert "AGENT_MEMORY_TOKEN" not in streaming.os.environ


def test_other_secrets_of_the_profile_are_never_exposed(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    seen = {}

    class Agent:
        def __init__(self, **kwargs):
            seen["key"] = streaming.os.environ.get("OPENAI_API_KEY")

    streaming._build_agent(Agent, {}, {"AGENT_MEMORY_TOKEN": "t", "OPENAI_API_KEY": "sk-x"})
    assert seen["key"] is None


def test_the_token_is_removed_even_when_building_fails(monkeypatch):
    monkeypatch.delenv("AGENT_MEMORY_TOKEN", raising=False)

    class Agent:
        def __init__(self, **kwargs):
            raise RuntimeError("boom")

    with pytest.raises(RuntimeError):
        streaming._build_agent(Agent, {}, {"AGENT_MEMORY_TOKEN": "t"})
    assert "AGENT_MEMORY_TOKEN" not in streaming.os.environ


def test_a_value_already_in_the_environment_is_put_back(monkeypatch):
    monkeypatch.setenv("AGENT_MEMORY_TOKEN", "from-process")

    class Agent:
        def __init__(self, **kwargs):
            pass

    streaming._build_agent(Agent, {}, {"AGENT_MEMORY_TOKEN": "tok-memtest"})
    assert streaming.os.environ["AGENT_MEMORY_TOKEN"] == "from-process"


def test_a_profile_without_a_token_builds_the_agent_unchanged(monkeypatch):
    monkeypatch.delenv("AGENT_MEMORY_TOKEN", raising=False)

    class Agent:
        def __init__(self, **kwargs):
            self.kwargs = kwargs

    agent = streaming._build_agent(Agent, {"a": 1}, {})
    assert agent.kwargs == {"a": 1}
    assert "AGENT_MEMORY_TOKEN" not in streaming.os.environ

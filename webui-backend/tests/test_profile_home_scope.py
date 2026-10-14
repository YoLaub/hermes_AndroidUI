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

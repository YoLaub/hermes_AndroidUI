"""
A profile's agent-memory credentials must stay in that profile's .env.

_write_env_file used to mirror every written key into os.environ, which every profile and plugin of the WebUI
process can read. AGENT_MEMORY_TOKEN is read by _build_agent straight from the profile's own env instead.
"""
import pytest
from api import providers


@pytest.fixture
def profile_env(tmp_path, monkeypatch):
    monkeypatch.setattr(providers, "_get_hermes_home", lambda: tmp_path)
    for key in ("AGENT_MEMORY_TOKEN", "AGENT_MEMORY_URL", "OPENAI_API_KEY"):
        monkeypatch.delenv(key, raising=False)
    return tmp_path / ".env"


@pytest.mark.parametrize("key", ["AGENT_MEMORY_TOKEN", "AGENT_MEMORY_URL"])
def test_profile_scoped_keys_reach_the_env_file_but_not_the_process(profile_env, key):
    result = providers.set_profile_env_var(key, "value-memtest")
    assert result["ok"]
    assert f"{key}=value-memtest" in profile_env.read_text().splitlines()
    assert key not in providers.os.environ


def test_deleting_a_profile_scoped_key_leaves_the_process_env_alone(profile_env, monkeypatch):
    monkeypatch.setenv("AGENT_MEMORY_TOKEN", "from-process")
    providers.set_profile_env_var("AGENT_MEMORY_TOKEN", "tok")
    providers.set_profile_env_var("AGENT_MEMORY_TOKEN", None)
    assert "AGENT_MEMORY_TOKEN" not in profile_env.read_text()
    assert providers.os.environ["AGENT_MEMORY_TOKEN"] == "from-process"


def test_model_provider_keys_still_reach_the_process(profile_env):
    providers.set_profile_env_var("OPENAI_API_KEY", "sk-test")
    assert "OPENAI_API_KEY=sk-test" in profile_env.read_text().splitlines()
    assert providers.os.environ["OPENAI_API_KEY"] == "sk-test"


def test_switching_profile_does_not_load_scoped_keys_into_the_process(tmp_path, monkeypatch):
    from api import profiles

    (tmp_path / ".env").write_text("AGENT_MEMORY_TOKEN=tok-memtest\nAGENT_MEMORY_URL=https://m\nOPENAI_API_KEY=sk-test\n")
    for key in ("AGENT_MEMORY_TOKEN", "AGENT_MEMORY_URL", "OPENAI_API_KEY"):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setattr(profiles, "_loaded_profile_env_keys", set())

    profiles._reload_dotenv(tmp_path)

    assert "AGENT_MEMORY_TOKEN" not in profiles.os.environ
    assert "AGENT_MEMORY_URL" not in profiles.os.environ
    assert profiles.os.environ["OPENAI_API_KEY"] == "sk-test"
    profiles.os.environ.pop("OPENAI_API_KEY", None)

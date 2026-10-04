"""
The throttle keys on the client address, so what uvicorn does with X-Forwarded-For decides whether
an attacker can evade it or lock a victim out. This pins that behaviour on a real relay process,
so an uvicorn upgrade that changes it fails here instead of in production.
"""
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

RELAY_DIR = Path(__file__).parent.parent.resolve()


def _run(allowed: str):
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    tmp = tempfile.mkdtemp()
    env = {**os.environ, "MOBILE_RELAY_DB_PATH": f"{tmp}/r.db", "MOBILE_RELAY_ADMIN_TOKEN": "a",
           "MOBILE_CONTROL_TOKEN_JOHN": "john-x", "MOBILE_RELAY_AUTH_MAX_FAILURES": "3",
           "FORWARDED_ALLOW_IPS": allowed}
    proc = subprocess.Popen([sys.executable, "-m", "uvicorn", "server:app", "--host", "127.0.0.1", "--port", str(port)],
                            cwd=RELAY_DIR, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(100):
            try:
                socket.create_connection(("127.0.0.1", port), timeout=0.2).close()
                break
            except OSError:
                time.sleep(0.1)

        def call(token, xff):
            req = urllib.request.Request(
                f"http://127.0.0.1:{port}/mcp",
                data=json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/list"}).encode(),
                headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}",
                         "X-Forwarded-For": xff})
            try:
                return urllib.request.urlopen(req, timeout=5).status
            except urllib.error.HTTPError as e:
                return e.code

        # The attacker is really 198.51.100.10 (what the proxy saw, appended last) and forges a
        # fresh leftmost address on every request.
        rotating = [call("bad", f"203.0.113.{i}, 198.51.100.10") for i in range(1, 8)]
        # Then it forges the victim's address to try to lock the victim out.
        for _ in range(4):
            call("bad", "192.0.2.77, 198.51.100.10")
        victim = call("john-x", "192.0.2.77")
        return rotating, victim
    finally:
        proc.terminate()
        proc.wait(timeout=5)


def test_trusting_only_the_proxy_network_resists_a_forged_forwarded_for():
    rotating, victim = _run("127.0.0.1")  # stands in for the proxy network
    assert 429 in rotating, "rotating forged addresses must not evade the throttle"
    assert victim == 200, "a forged victim address must not lock the victim out"


def test_trusting_every_peer_lets_the_attacker_evade_and_lock_a_victim_out():
    # Documents why '*' is not used in the deployment.
    rotating, victim = _run("*")
    assert 429 not in rotating
    assert victim == 429

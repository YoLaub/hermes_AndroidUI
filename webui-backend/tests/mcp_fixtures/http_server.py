"""Streamable-HTTP MCP server with sessions, a bearer token and a request log.

Usage: http_server.py PORT TOKEN LOGFILE
Every HTTP request is appended to LOGFILE as one JSON line so tests can prove
that a persistent client reuses a single MCP session.
"""
import json
import sys

sys.path.insert(0, __import__("os").path.dirname(__file__))
from _server_factory import build_server  # noqa: E402


class GuardAndLog:
    def __init__(self, app, token, logfile):
        self.app, self.token, self.logfile = app, token, logfile

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http":
            headers = {k.decode().lower(): v.decode() for k, v in scope["headers"]}
            with open(self.logfile, "a", encoding="utf-8") as fh:
                fh.write(json.dumps({
                    "method": scope["method"],
                    "auth_ok": headers.get("authorization") == f"Bearer {self.token}",
                    "session": headers.get("mcp-session-id"),
                }) + "\n")
            if headers.get("authorization") != f"Bearer {self.token}":
                await send({"type": "http.response.start", "status": 401,
                            "headers": [(b"content-type", b"application/json")]})
                await send({"type": "http.response.body", "body": b'{"error":"Unauthorized"}'})
                return
        await self.app(scope, receive, send)


def main():
    import uvicorn

    port, token, logfile = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    server = build_server("fixture-http")
    app = GuardAndLog(server.streamable_http_app(), token, logfile)
    uvicorn.run(app, host="127.0.0.1", port=port, log_level="critical")


if __name__ == "__main__":
    main()

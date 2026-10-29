import base64
import json
from uuid import uuid4

import httpx
import pytest
from aether_chat import ChatService
from aether_chat_upstream import ChatUnavailable, HermesChat
from test_aether_routes import client, pair  # noqa: F401

JPEG = bytes.fromhex("ffd8 ffc0 000b 08 0002 0002 01 011100 ffda 0008 010100003f00 00 ffd9")
IMAGE = {"mime_type": "image/jpeg", "data_base64": base64.b64encode(JPEG).decode()}


@pytest.mark.asyncio
async def test_upload_uses_bound_session_and_server_metadata():
    paths = []

    def handle(request):
        paths.append(request.url.path)
        if request.url.path == "/api/auth/login":
            return httpx.Response(200, json={})
        if request.url.path == "/api/upload":
            assert b'name="session_id"\r\n\r\nsid' in request.content
            assert JPEG in request.content
            return httpx.Response(
                200,
                json={
                    "filename": "frame.jpg",
                    "path": "/uploads/sid/frame.jpg",
                    "mime": "image/jpeg",
                    "size": len(JPEG),
                    "is_image": True,
                },
            )
        if request.url.path == "/api/chat/start":
            body = json.loads(request.content)
            assert body["session_id"] == "sid"
            assert body["attachments"][0]["path"] == "/uploads/sid/frame.jpg"
            return httpx.Response(200, json={"session_id": "sid", "stream_id": "stream"})
        return httpx.Response(200, text="event: done\ndata: {}\n\n")

    chat = HermesChat("https://hermes.test", "secret", transport=httpx.MockTransport(handle))
    result = [e async for e in chat.turn("john", "sid", "Regarde", observation=JPEG, authorized=lambda: True)]
    assert result == [("completed", "")]
    assert paths.count("/api/upload") == 1


@pytest.mark.asyncio
async def test_revocation_after_login_prevents_upload():
    valid = True

    def handle(request):
        nonlocal valid
        assert request.url.path == "/api/auth/login"
        valid = False
        return httpx.Response(200, json={})

    chat = HermesChat("https://hermes.test", "secret", transport=httpx.MockTransport(handle))
    with pytest.raises(ChatUnavailable):
        async for _ in chat.turn("john", "sid", "Regarde", observation=JPEG, authorized=lambda: valid):
            pass


def test_capability_validation_and_no_duplicate_upload(client):  # noqa: F811
    import server

    calls = []

    class Upstream:
        async def turn(self, profile, session, message, *, observation, authorized):
            assert authorized()
            calls.append(observation)
            yield "completed", ""

    server.app.state.aether_chat = ChatService(Upstream())
    token = pair(client, "aether")
    headers = {"Authorization": "Bearer " + token, "X-Aether-Device": "aether"}
    assert client.get("/api/aether/chat/status", headers=headers).json() == {"available": True}
    assert client.get(
        "/api/aether/chat/status", headers={**headers, "X-Aether-Features": "screen-observation-v1"}
    ).json() == {"available": True, "screen_observation": True}
    body = {"request_id": str(uuid4()), "message": "Regarde", "observation": IMAGE}
    assert client.post("/api/aether/chat/send", headers=headers, json=body).status_code == 200
    for _ in range(30):
        if client.get("/api/aether/chat/turn/" + body["request_id"], headers=headers).json()["state"] == "completed":
            break
    assert client.post("/api/aether/chat/send", headers=headers, json=body).status_code == 200
    assert calls == [JPEG]
    for image in (
        {**IMAGE, "path": "/etc/passwd"},
        {**IMAGE, "data_base64": "invalid"},
        {**IMAGE, "mime_type": "image/png"},
    ):
        response = client.post("/api/aether/chat/send", headers=headers, json={**body, "observation": image})
        assert response.status_code == 422
        assert "data_base64" not in response.text  # Never reflect image input in validation errors.
    assert client.post("/api/aether/chat/send", headers=headers, content=b"x" * 750001).status_code == 413


@pytest.mark.asyncio
@pytest.mark.parametrize("upload_status,metadata", [(500, {}), (200, {"path": "/untrusted"})])
async def test_failed_upload_never_starts_or_retries(upload_status, metadata):
    paths = []

    def handle(request):
        paths.append(request.url.path)
        if request.url.path == "/api/auth/login":
            return httpx.Response(200, json={})
        assert request.url.path == "/api/upload"
        return httpx.Response(upload_status, json=metadata)

    chat = HermesChat("https://hermes.test", "secret", transport=httpx.MockTransport(handle))
    with pytest.raises(ChatUnavailable):
        async for _ in chat.turn("john", "sid", "Regarde", observation=JPEG):
            pass
    assert paths == ["/api/auth/login", "/api/upload"]


@pytest.mark.asyncio
async def test_chunked_body_is_bounded_before_application():
    from aether_observation import ObservationBodyLimit

    called, sent = [], []

    async def app(*args):
        called.append(True)

    chunks = iter([{"type": "http.request", "body": b"x" * 400000, "more_body": True}] * 2)

    async def receive():
        return next(chunks)

    async def send(event):
        sent.append(event)

    await ObservationBodyLimit(app)({"type": "http", "path": "/api/aether/chat/send"}, receive, send)
    assert not called
    assert sent[0]["status"] == 413


@pytest.mark.parametrize(
    "raw", [b"not jpeg", JPEG.replace(b"\x00\x02\x00\x02", b"\x04\x00\x08\x00"), JPEG[:-2], b"x" * 524289]
)
def test_reject_invalid_or_oversized_jpeg(raw):
    from aether_observation import decode_observation

    with pytest.raises(ValueError):
        decode_observation(base64.b64encode(raw).decode())

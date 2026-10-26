import json
import httpx
import pytest
from aether_chat_upstream import HermesChat, ChatUnavailable

@pytest.mark.asyncio
async def test_profile_session_and_text_only_stream():
    calls = []
    def handle(request):
        calls.append((request.url.path, json.loads(request.content) if request.content else None))
        if request.url.path == '/api/auth/login':
            return httpx.Response(200, json={'ok': True}, headers={'set-cookie': 'hermes_session=test; Path=/'})
        assert 'hermes_session=test' in request.headers.get('cookie', '')
        if request.url.path == '/api/session/new': return httpx.Response(200, json={'session': {'session_id': 'session-1'}})
        if request.url.path == '/api/chat/start': return httpx.Response(200, json={'stream_id': 'stream-1', 'session_id': 'session-1'})
        return httpx.Response(200, text='event: reasoning\ndata: {"text":"private"}\n\nevent: token\ndata: {"text":"Bonjour"}\n\nevent: done\ndata: {}\n\n')
    chat = HermesChat('https://hermes.test', 'secret', transport=httpx.MockTransport(handle))
    events = [event async for event in chat.turn('john', None, 'Salut')]
    assert events == [('session', 'session-1'), ('text', 'Bonjour'), ('completed', '')]
    assert calls[1] == ('/api/session/new', {'profile': 'john'})
    assert calls[2] == ('/api/chat/start', {'session_id': 'session-1', 'profile': 'john', 'message': 'Salut'})

@pytest.mark.asyncio
async def test_approval_is_not_answered_and_requests_cancellation():
    paths = []
    def handle(request):
        paths.append(request.url.path)
        if request.url.path == '/api/auth/login': return httpx.Response(200, json={'ok': True})
        if request.url.path == '/api/chat/start': return httpx.Response(200, json={'session_id': 'sid', 'stream_id': 'stream'})
        if request.url.path == '/api/chat/cancel': return httpx.Response(200, json={'ok': True})
        return httpx.Response(200, text='event: approval\ndata: {"command":"private"}\n\n')
    chat = HermesChat('https://hermes.test', 'secret', transport=httpx.MockTransport(handle))
    assert [e async for e in chat.turn('john', 'sid', 'Salut')] == [('needs_action', '')]
    assert paths[-1] == '/api/chat/cancel'
    assert '/api/approval/respond' not in paths

@pytest.mark.asyncio
@pytest.mark.parametrize('body', ['event: token\ndata: {"text":"' + 'x' * 70000 + '"}\n\n', 'event: token\ndata: {"text":"partial"}\n\n'])
async def test_oversized_or_truncated_stream_never_completes(body):
    def handle(request):
        if request.url.path == '/api/auth/login': return httpx.Response(200, json={'ok': True})
        if request.url.path == '/api/chat/start': return httpx.Response(200, json={'session_id': 'sid', 'stream_id': 'stream'})
        if request.url.path == '/api/chat/cancel': return httpx.Response(200, json={'ok': True})
        return httpx.Response(200, text=body)
    chat = HermesChat('https://hermes.test', 'secret', transport=httpx.MockTransport(handle))
    with pytest.raises(ChatUnavailable):
        async for _ in chat.turn('john', 'sid', 'Salut'): pass

def test_origin_and_secrets_are_not_exposed():
    for origin in ['http://public.example', 'https://x/path', 'https://user:pass@x', 'https://x?token=x']:
        with pytest.raises(ValueError): HermesChat(origin, 'secret')
    assert 'secret' not in repr(HermesChat('https://hermes.test', 'secret'))

@pytest.mark.asyncio
async def test_large_session_history_in_done_is_discarded_without_losing_completion():
    from aether_chat_upstream import events
    body = 'event: done\ndata: ' + json.dumps({'session': {'messages': ['x' * 200000]}}) + '\n\n'
    response = httpx.Response(200, content=body.encode())
    assert [event async for event in events(response)] == [('done', {})]

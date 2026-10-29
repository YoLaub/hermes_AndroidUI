"""Validation at the relay trust boundary; images are never persisted locally."""


def decode_observation(encoded):
    """Only bounded baseline/progressive JPEG frames with bounded pixel dimensions."""
    import base64
    import binascii

    if not isinstance(encoded, str) or len(encoded) > 699052:
        raise ValueError("Image too large")
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise ValueError("Invalid image") from None
    if len(raw) > 524288 or not raw.startswith(b"\xff\xd8") or not raw.endswith(b"\xff\xd9"):
        raise ValueError("JPEG required")
    offset, dimensions = 2, None
    while offset + 4 <= len(raw):
        if raw[offset] != 255:
            raise ValueError("Invalid JPEG marker")
        marker = raw[offset + 1]
        size = int.from_bytes(raw[offset + 2 : offset + 4], "big")
        if size < 2 or offset + 2 + size > len(raw):
            raise ValueError("Invalid JPEG segment")
        if marker in (0xC0, 0xC2):
            if size < 8:
                raise ValueError("Invalid JPEG dimensions")
            height = int.from_bytes(raw[offset + 5 : offset + 7], "big")
            width = int.from_bytes(raw[offset + 7 : offset + 9], "big")
            if not 1 <= width <= 1280 or not 1 <= height <= 720:
                raise ValueError("Image dimensions exceed limit")
            dimensions = (width, height)
        if marker == 0xDA:
            if dimensions is None:
                raise ValueError("Missing JPEG dimensions")
            return raw
        offset += 2 + size
    raise ValueError("Incomplete JPEG")


class ObservationBodyLimit:
    """Bound the body before JSON decoding, including chunked requests."""

    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope.get("path") != "/api/aether/chat/send":
            return await self.app(scope, receive, send)
        import asyncio

        from starlette.responses import JSONResponse

        chunks, size = [], 0
        try:
            async with asyncio.timeout(5):
                while True:
                    event = await receive()
                    if event["type"] == "http.disconnect":
                        return
                    size += len(event.get("body", b""))
                    if size > 750000:
                        return await JSONResponse({"detail": "Request too large"}, status_code=413)(
                            scope, receive, send
                        )
                    chunks.append(event.get("body", b""))
                    if not event.get("more_body", False):
                        break
        except TimeoutError:
            return await JSONResponse({"detail": "Request timeout"}, status_code=408)(scope, receive, send)
        body = b"".join(chunks)
        delivered = False

        async def bounded_receive():
            nonlocal delivered
            if delivered:
                return await receive()
            delivered = True
            return {"type": "http.request", "body": body, "more_body": False}

        await self.app(scope, bounded_receive, send)

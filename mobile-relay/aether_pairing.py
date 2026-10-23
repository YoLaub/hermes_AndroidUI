"""Single-use pairing links. Never embed an enduring device credential."""
from urllib.parse import urlencode, urlsplit


def pairing_uri(public_url: str, code: str) -> str:
    parsed = urlsplit(public_url)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment or parsed.path not in ('', '/')):
        raise ValueError('AETHER_PUBLIC_URL must be an HTTPS origin')
    return 'aether://pair?' + urlencode({'relay': public_url.rstrip('/'), 'code': code})

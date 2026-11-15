"""Isolated real-API fixture for ADB/browser tests. Never serves production data.

Run with Python 3.12 from any directory; binds localhost:8879. Its printed image
path can be passed as draftImagePath to the instrumented LinkedIn test.
"""

import json
import struct
import sys
import tempfile
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from api import documents
from api.document_store import DocumentStore


def main():
    with tempfile.TemporaryDirectory(prefix="hermes-documents-trial-") as directory:
        root = Path(directory).resolve()
        (root / "devis.pdf").write_bytes(b"%PDF-1.7\x00sample")
        (root / "bonjour.md").write_text("# Bonjour", encoding="utf-8")

        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

        image = root / "test-linkedin.png"
        pixels = b"".join(b"\0" + bytes((25, 130, 180)) * 64 for _ in range(64))
        image.write_bytes(
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels))
            + chunk(b"IEND", b"")
        )
        store = DocumentStore({"trial": root})
        documents._context = lambda: (store, [{"id": "trial", "name": "Espace de test", "profile": "john"}])
        static = Path(__file__).resolve().parents[1] / "static"
        index = (static / "index.html").read_text()
        start = index.index('<dialog id="documentsDialog"')
        dialog = index[start : index.index("</dialog>", start) + len("</dialog>")]

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                parsed = urlparse(self.path)
                if parsed.path == "/api/documents":
                    return documents.handle_get(self, parsed)
                if parsed.path == "/trial-info":
                    data = json.dumps({"image_path": str(image)}).encode()
                    content_type = "application/json"
                elif parsed.path in ("/static/documents.js", "/static/documents.css"):
                    path = static / Path(parsed.path).name
                    data = path.read_bytes()
                    content_type = "text/css" if path.suffix == ".css" else "application/javascript"
                elif parsed.path == "/":
                    data = (
                        """<!DOCTYPE html><meta charset="utf-8"><link rel="stylesheet" href="static/documents.css"><script>const S={activeProfile:'john'};async function api(url,opts){const r=await fetch(url,{headers:{'Content-Type':'application/json'},...opts});const data=await r.json();if(!r.ok)throw Error(data.error);return data;}</script><script src="static/documents.js" defer></script><button onclick="openDocuments()">Fichiers</button>"""
                        + dialog
                    ).encode()
                    content_type = "text/html; charset=utf-8"
                else:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self):
                if urlparse(self.path).path != "/api/documents":
                    self.send_error(404)
                    return
                documents.handle_post(self, json.loads(self.rfile.read(int(self.headers["Content-Length"]))))

        print(json.dumps({"url": "http://127.0.0.1:8879", "image_path": str(image)}), flush=True)
        ThreadingHTTPServer(("127.0.0.1", 8879), Handler).serve_forever()


if __name__ == "__main__":
    main()

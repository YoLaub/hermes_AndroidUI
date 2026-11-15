"""Document HTTP adapter; authentication and profile context belong to server.py."""

import mimetypes
from urllib.parse import parse_qs

from api.document_catalog import document_catalog
from api.document_store import DocumentConflict, DocumentStore
from api.helpers import j


def _context():
    from api.profiles import get_active_hermes_home, get_active_profile_name
    from api.workspace import load_workspaces

    roots, catalog = document_catalog(get_active_profile_name(), get_active_hermes_home(), load_workspaces())
    return DocumentStore(roots), catalog


def _reply(handler, operation, respond=None):
    try:
        return (respond or j)(handler, operation())
    except DocumentConflict:
        return j(handler, {"error": "Document changed; reload before retrying"}, status=409)
    except FileExistsError:
        return j(handler, {"error": "A document with that name already exists"}, status=409)
    except FileNotFoundError:
        return j(handler, {"error": "Document not found"}, status=404)
    except (ValueError, KeyError, TypeError):
        return j(handler, {"error": "Invalid document request"}, status=400)
    except OSError:
        return j(handler, {"error": "Document operation unavailable"}, status=403)


def _download(handler, path):
    from api.routes import _serve_file_bytes

    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    return _serve_file_bytes(handler, path, mime, "attachment", "no-store")


def handle_get(handler, parsed):
    query = parse_qs(parsed.query)
    if query.get("action") == ["image"]:
        return _reply(handler, lambda: _context()[0].image_path(query.get("path", [""])[0]), respond=_download)
    if query.get("action") == ["download"]:
        return _reply(
            handler,
            lambda: _context()[0].download_path(query.get("space", [""])[0], query.get("path", [""])[0]),
            respond=_download,
        )

    def execute():
        store, catalog = _context()
        query = parse_qs(parsed.query)
        action = query.get("action", ["spaces"])[0]
        if action == "spaces":
            return {"spaces": catalog}
        space = query.get("space", [""])[0]
        path = query.get("path", ["."])[0]
        if action == "list":
            return {"entries": store.list(space, path), "path": path}
        if action == "read":
            return store.read(space, path)
        if action == "revision":
            return {"revision": store.revision(space, path)}
        raise ValueError("Unknown operation")

    return _reply(handler, execute)


def handle_post(handler, body):
    def execute():
        store, _ = _context()
        space, path, action = body["space"], body["path"], body["action"]
        if action == "create":
            return store.create(space, path, body["content"])
        if action == "save":
            return store.save(space, path, body["content"], body["revision"])
        if action == "rename":
            store.rename(space, path, body["name"], body["revision"])
        elif action == "delete":
            store.delete(space, path, body["revision"])
        else:
            raise ValueError("Unknown operation")
        return {"ok": True}

    return _reply(handler, execute)

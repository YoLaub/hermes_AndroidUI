from urllib.parse import urlparse

import pytest
from api import documents
from api.document_store import DocumentStore


@pytest.fixture
def api(tmp_path, monkeypatch):
    store = DocumentStore({"john": tmp_path})
    monkeypatch.setattr(documents, "_context", lambda: (store, [{"id": "john", "name": "Documents"}]))
    monkeypatch.setattr(documents, "j", lambda handler, data, status=200: (status, data))
    return store


def test_catalog_and_crud_need_no_conversation(api):
    assert documents.handle_get(None, urlparse("/api/documents"))[1]["spaces"][0]["id"] == "john"
    status, row = documents.handle_post(None, dict(action="create", space="john", path="test.txt", content="one"))
    assert status == 200
    assert documents.handle_get(None, urlparse("/api/documents?action=read&space=john&path=test.txt"))[1] == row
    changed = documents.handle_post(
        None, dict(action="save", space="john", path="test.txt", content="two", revision=row["revision"])
    )
    assert changed[0] == 200
    assert (
        documents.handle_post(
            None, dict(action="save", space="john", path="test.txt", content="stale", revision=row["revision"])
        )[0]
        == 409
    )
    assert (
        documents.handle_post(
            None, dict(action="delete", space="john", path="test.txt", revision=changed[1]["revision"])
        )[0]
        == 200
    )
    assert documents.handle_get(None, urlparse("/api/documents?action=read&space=john&path=test.txt"))[0] == 404


def test_arbitrary_paths_and_missing_fields_are_refused(api):
    assert documents.handle_post(None, {})[0] == 400
    assert documents.handle_get(None, urlparse("/api/documents?action=list&space=/etc"))[0] == 400
    assert documents.handle_post(None, dict(action="create", space="john", path="../escape", content="x"))[0] == 400


def test_download_preserves_binary_bytes_and_never_uses_a_session(api, tmp_path, monkeypatch):
    data = b"%PDF-1.7\x00binary\xff"
    (tmp_path / "devis.pdf").write_bytes(data)
    monkeypatch.setattr(documents, "_download", lambda handler, path: path.read_bytes())
    assert documents.handle_get(None, urlparse("/api/documents?action=download&space=john&path=devis.pdf")) == data
    assert documents.handle_get(None, urlparse("/api/documents?action=download&space=john&path=../escape"))[0] == 400

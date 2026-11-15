"""Document operations are scoped to server-selected roots, never client absolute paths."""
import pytest
from api.document_store import DocumentStore, DocumentConflict

@pytest.fixture
def store(tmp_path):
    john = tmp_path / 'john'; john.mkdir()
    common = tmp_path / 'common'; common.mkdir()
    return DocumentStore({'john': john, 'common': common}, max_text_bytes=64)

def test_crud_preserves_other_workspaces_and_returns_revision(store):
    first = store.create('john', 'notes/devis.md', 'bonjour')
    assert store.read('john', 'notes/devis.md') == first
    assert store.list('john', 'notes')[0]['name'] == 'devis.md'
    changed = store.save('john', 'notes/devis.md', 'modifié', first['revision'])
    assert changed['revision'] != first['revision']
    store.rename('john', 'notes/devis.md', 'devis-final.md', changed['revision'])
    store.delete('john', 'notes/devis-final.md', changed['revision'])
    assert store.list('john', 'notes') == []
    assert store.list('common') == []

def test_edit_conflict_never_overwrites_agent_changes(store):
    old = store.create('john', 'a.txt', 'a')
    store.save('john', 'a.txt', 'agent edit', old['revision'])
    with pytest.raises(DocumentConflict):
        store.save('john', 'a.txt', 'stale edit', old['revision'])
    with pytest.raises(DocumentConflict):
        store.delete('john', 'a.txt', old['revision'])
    assert store.read('john', 'a.txt')['content'] == 'agent edit'

@pytest.mark.parametrize('path', ['../outside', '/etc/passwd', 'a/../../outside', '.', ''])
def test_mutations_reject_escape_or_workspace_root(store, path):
    with pytest.raises(ValueError):
        store.create('john', path, 'x')

def test_unknown_space_cannot_be_used_as_a_path(store):
    with pytest.raises(ValueError):
        store.list('/etc')

def test_symlinks_are_not_followed_even_inside_workspace(store, tmp_path):
    outside = tmp_path / 'secret'; outside.write_text('secret')
    root = tmp_path / 'john'
    (root / 'link').symlink_to(outside)
    with pytest.raises(ValueError): store.read('john', 'link')
    assert store.list('john') == []

def test_create_and_rename_never_replace_existing_files(store):
    a = store.create('john', 'a.txt', 'a')
    store.create('john', 'b.txt', 'b')
    with pytest.raises(FileExistsError): store.create('john', 'a.txt', 'oops')
    with pytest.raises(FileExistsError): store.rename('john', 'a.txt', 'b.txt', a['revision'])
    assert store.read('john', 'b.txt')['content'] == 'b'

@pytest.mark.parametrize('name', ['../escape', '/escape', 'a/b', '.', '..', ''])
def test_rename_requires_a_single_filename(store, name):
    a = store.create('john', 'a.txt', 'a')
    with pytest.raises(ValueError): store.rename('john', 'a.txt', name, a['revision'])

def test_binary_and_large_documents_are_not_text_edited(store, tmp_path):
    (tmp_path / 'john' / 'photo.jpg').write_bytes(b'\xff\xd8\x00image')
    with pytest.raises(ValueError): store.read('john', 'photo.jpg')
    with pytest.raises(ValueError): store.create('john', 'huge.txt', 'x' * 65)
    assert not (tmp_path / 'john' / 'huge.txt').exists()

def test_directories_cannot_be_deleted_as_documents(store):
    store.create('john', 'dir/a', 'a')
    with pytest.raises(ValueError): store.delete('john', 'dir', 'unused')

def test_binary_files_can_be_renamed_and_deleted_without_decoding(store, tmp_path):
    data = b'\xff\xd8\x00image'
    (tmp_path / 'john' / 'photo.jpg').write_bytes(data)
    revision = store.revision('john', 'photo.jpg')
    store.rename('john', 'photo.jpg', 'image.jpg', revision)
    store.delete('john', 'image.jpg', revision)
    assert not (tmp_path / 'john' / 'image.jpg').exists()

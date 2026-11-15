"""Session-independent document operations over roots selected by the server.

Transport handlers supply an authorized workspace id, never an arbitrary filesystem
root. Text writes use revisions and atomic replacement; binaries remain downloadable.
"""
import hashlib
import os
from pathlib import Path, PurePosixPath
import tempfile
from threading import RLock


_DOCUMENT_LOCK = RLock()


class DocumentConflict(Exception):
    """The document changed since it was loaded by this client."""


class DocumentStore:
    def __init__(self, roots, *, max_text_bytes=2 * 1024 * 1024):
        self._roots = {key: Path(root).resolve(strict=True) for key, root in roots.items()}
        self._max_text_bytes = max_text_bytes
        self._lock = _DOCUMENT_LOCK

    def _path(self, space, relative, *, allow_root=False):
        root = self._roots.get(space)
        if root is None:
            raise ValueError('Unknown workspace')
        if not isinstance(relative, str) or '\x00' in relative or '\\' in relative:
            raise ValueError('Invalid path')
        path = PurePosixPath(relative)
        if path.is_absolute() or '..' in path.parts:
            raise ValueError('Path escapes workspace')
        if not allow_root and (not path.parts or str(path) == '.'):
            raise ValueError('Workspace root is not a document')
        target = root
        for part in path.parts:
            target = target / part
            if target.is_symlink():
                raise ValueError('Symbolic links are not documents')
        if not target.resolve().is_relative_to(root):
            raise ValueError('Path escapes workspace')
        return target

    @staticmethod
    def _revision(data):
        return hashlib.sha256(data).hexdigest()

    def _bytes(self, path):
        if not path.is_file():
            if path.is_dir():
                raise ValueError('Path is a directory')
            raise FileNotFoundError(path.name)
        with path.open('rb') as stream:
            data = stream.read(self._max_text_bytes + 1)
        if len(data) > self._max_text_bytes:
            raise ValueError('Document too large to edit')
        return data

    def revision(self, space, relative):
        return self._file_revision(self._path(space, relative))

    @staticmethod
    def _file_revision(path):
        if path.is_dir():
            raise ValueError('Path is a directory')
        with path.open('rb') as stream:
            return hashlib.file_digest(stream, 'sha256').hexdigest()

    def _checked(self, path, revision):
        if not revision or self._file_revision(path) != revision:
            raise DocumentConflict('Document has changed; reload before retrying')

    def _text_bytes(self, content):
        if not isinstance(content, str) or '\x00' in content:
            raise ValueError('Not a text document')
        data = content.encode('utf-8')
        if len(data) > self._max_text_bytes:
            raise ValueError('Document too large to edit')
        return data

    def list(self, space, relative='.'):
        folder = self._path(space, relative, allow_root=True)
        entries = []
        for item in folder.iterdir():
            if item.is_symlink() or not (item.is_file() or item.is_dir()):
                continue
            info = item.stat()
            entries.append(dict(name=item.name, is_dir=item.is_dir(), size=info.st_size,
                                modified=info.st_mtime))
        return sorted(entries, key=lambda row: (not row['is_dir'], row['name'].casefold()))

    def read(self, space, relative):
        data = self._bytes(self._path(space, relative))
        try:
            content = data.decode('utf-8')
        except UnicodeError as exc:
            raise ValueError('Not a text document') from exc
        if '\x00' in content:
            raise ValueError('Not a text document')
        return dict(content=content, revision=self._revision(data))

    def create(self, space, relative, content):
        data = self._text_bytes(content)
        with self._lock:
            path = self._path(space, relative)
            path.parent.mkdir(parents=True, exist_ok=True)
            with path.open('xb') as stream:
                stream.write(data)
            return dict(content=content, revision=self._revision(data))

    def save(self, space, relative, content, revision):
        data = self._text_bytes(content)
        with self._lock:
            path = self._path(space, relative)
            self._checked(path, revision)
            fd, temporary = tempfile.mkstemp(prefix='.hermes-write-', dir=path.parent)
            try:
                os.fchmod(fd, path.stat().st_mode & 0o777)
                with os.fdopen(fd, 'wb') as stream:
                    stream.write(data)
                    stream.flush()
                    os.fsync(stream.fileno())
                self._checked(path, revision)
                os.replace(temporary, path)
            finally:
                if os.path.exists(temporary):
                    os.unlink(temporary)
            return dict(content=content, revision=self._revision(data))

    def rename(self, space, relative, name, revision):
        if not isinstance(name, str) or not name or name in ('.', '..') or '/' in name or '\\' in name or '\x00' in name:
            raise ValueError('Expected a single filename')
        with self._lock:
            path = self._path(space, relative)
            self._checked(path, revision)
            destination = path.with_name(name)
            # Linking fails atomically if the destination already exists.
            os.link(path, destination)
            path.unlink()

    def delete(self, space, relative, revision):
        with self._lock:
            path = self._path(space, relative)
            self._checked(path, revision)
            path.unlink()

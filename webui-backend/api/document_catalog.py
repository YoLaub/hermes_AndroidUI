"""Derive the document roots for the current profile from server configuration."""
import hashlib
from pathlib import Path


def document_catalog(profile, profile_home, workspaces):
    home = Path(profile_home).resolve()
    profiles = home.parent if home.parent.name == 'profiles' else home / 'profiles'
    candidates = [(home / 'workspace', 'Documents du profil')]
    candidates.extend((Path(row['path']), row.get('name') or Path(row['path']).name)
                      for row in workspaces if isinstance(row, dict) and isinstance(row.get('path'), str))
    roots, rows, seen = {}, [], set()
    for original, label in candidates:
        root = original.resolve()
        if not root.is_dir() or original.is_symlink() or root in seen:
            continue
        # Do not turn an agent's configuration directory (or its parent) into a file browser.
        if home.is_relative_to(root) or root == Path.home():
            continue
        if root.is_relative_to(profiles) and not root.is_relative_to(home / 'workspace'):
            continue
        key = hashlib.sha256((profile + '\x00' + str(root)).encode()).hexdigest()[:24]
        roots[key] = root
        rows.append(dict(id=key, name=label, profile=profile))
        seen.add(root)
    return roots, rows

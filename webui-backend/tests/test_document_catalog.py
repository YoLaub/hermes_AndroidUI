from api.document_catalog import document_catalog


def test_profile_folder_and_common_root_are_distinct_and_do_not_include_secrets(tmp_path):
    home = tmp_path / "profiles" / "john"
    (home / "workspace").mkdir(parents=True)
    (home / ".env").write_text("private")
    common = tmp_path / "common"
    common.mkdir()
    roots, rows = document_catalog("john", home, [{"path": str(common), "name": "Commun"}])
    assert len(rows) == 2
    assert set(roots.values()) == {home / "workspace", common}
    assert home not in roots.values()
    assert all("path" not in row for row in rows)


def test_duplicate_roots_are_listed_once(tmp_path):
    root = tmp_path / "workspace"
    root.mkdir()
    roots, rows = document_catalog("john", tmp_path, [{"path": str(root)}, {"path": str(root)}])
    assert len(roots) == len(rows) == 1


def test_missing_folders_are_not_created(tmp_path):
    roots, rows = document_catalog("john", tmp_path, [{"path": str(tmp_path / "missing")}])
    assert roots == {} and rows == []
    assert not (tmp_path / "workspace").exists()


def test_profile_home_and_ancestor_roots_are_not_exposed(tmp_path):
    home = tmp_path / "profiles" / "john"
    home.mkdir(parents=True)
    roots, rows = document_catalog("john", home, [{"path": str(home)}, {"path": str(tmp_path)}, {"path": "/"}])
    assert not roots and not rows


def test_another_profiles_directory_is_never_registered(tmp_path):
    home = tmp_path / "profiles" / "john"
    home.mkdir(parents=True)
    other = tmp_path / "profiles" / "mario" / "workspace"
    other.mkdir(parents=True)
    roots, _ = document_catalog("john", home, [{"path": str(other)}])
    assert not roots

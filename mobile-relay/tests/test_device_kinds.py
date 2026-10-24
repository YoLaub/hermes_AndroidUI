"""Pairing authority is scoped to a device kind, including legacy databases."""
import sqlite3
import pytest
import database

@pytest.fixture
def db(tmp_path, monkeypatch):
    monkeypatch.setattr(database, 'DB_PATH', str(tmp_path / 'relay.db'))
    database.init_db()
    return database


def test_pairing_code_cannot_cross_device_kind(db):
    db.save_pairing_code('ABC123', 'admin', device_kind='aether')
    assert db.consume_pairing_code('ABC123') is None
    assert db.consume_pairing_code('ABC123', device_kind='aether') == 'admin'
    assert db.consume_pairing_code('ABC123', device_kind='aether') is None


def test_tokens_and_replacements_cannot_cross_device_kind(db):
    assert db.atomic_register_or_update_device('mac', 'secret', 'Mac', device_kind='aether')[0]
    assert not db.verify_device_token('mac', 'secret')
    assert db.verify_device_token('mac', 'secret', device_kind='aether')
    assert not db.atomic_register_or_update_device('mac', 'new', 'Phone', is_admin_authorized=True)[0]
    assert db.verify_device_token('mac', 'secret', device_kind='aether')


def test_legacy_devices_and_codes_remain_android(tmp_path, monkeypatch):
    path = str(tmp_path / 'legacy.db')
    with sqlite3.connect(path) as conn:
        conn.execute('CREATE TABLE paired_devices (device_id TEXT PRIMARY KEY, token_hash TEXT NOT NULL, device_name TEXT, created_at REAL NOT NULL, last_seen REAL NOT NULL)')
        conn.execute('INSERT INTO paired_devices VALUES (?, ?, ?, 0, 0)', ('phone', database.hash_token('old'), 'Phone'))
        conn.execute('CREATE TABLE pairing_codes (code TEXT PRIMARY KEY, user_id TEXT NOT NULL, expires_at REAL NOT NULL)')
        conn.execute("INSERT INTO pairing_codes VALUES ('ABC123', 'admin', 9999999999)")
    monkeypatch.setattr(database, 'DB_PATH', path)
    database.init_db()
    database.init_db()
    assert database.verify_device_token('phone', 'old')
    assert not database.verify_device_token('phone', 'old', device_kind='aether')
    assert database.consume_pairing_code('ABC123', device_kind='aether') is None
    assert database.consume_pairing_code('ABC123') == 'admin'


def test_aether_avatar_records_its_owner_profile(db):
    assert db.atomic_register_or_update_device('avatar', 'secret', 'Avatar', device_kind='aether', profile='mario')[0]
    assert db.aether_device_profile('avatar') == 'mario'
    assert db.atomic_register_or_update_device('phone', 'secret', 'Phone')[0]
    assert db.aether_device_profile('phone') is None
    assert db.aether_device_profile('missing') is None

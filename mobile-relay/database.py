import sqlite3
import hashlib
import time
import os
import secrets
from typing import Optional, List, Dict, Any

DB_PATH = os.environ.get("MOBILE_RELAY_DB_PATH", "/data/mobile_relay.db")

def get_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    with conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS paired_devices (
                device_id TEXT PRIMARY KEY,
                token_hash TEXT NOT NULL,
                device_name TEXT,
                created_at REAL NOT NULL,
                last_seen REAL NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS pairing_codes (
                code TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                expires_at REAL NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS profile_tokens (
                profile TEXT PRIMARY KEY,
                token_hash TEXT NOT NULL,
                created_at REAL NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS admin_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id TEXT PRIMARY KEY,
                timestamp REAL NOT NULL,
                device_id TEXT,
                profile TEXT,
                operation TEXT,
                status TEXT,
                message TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS pairing_attempts (
                identifier TEXT PRIMARY KEY,
                attempts INTEGER NOT NULL,
                last_attempt REAL NOT NULL
            )
        """)
    conn.close()

def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode('utf-8')).hexdigest()

def verify_device_token(device_id: str, token: str) -> bool:
    conn = get_db()
    h = hash_token(token)
    row = conn.execute("SELECT token_hash FROM paired_devices WHERE device_id = ?", (device_id,)).fetchone()
    if not row:
        conn.close()
        return False
    is_valid = secrets.compare_digest(row["token_hash"], h)
    if is_valid:
        conn.execute("UPDATE paired_devices SET last_seen = ? WHERE device_id = ?", (time.time(), device_id))
        conn.commit()
    conn.close()
    return is_valid

def is_device_registered(device_id: str) -> bool:
    conn = get_db()
    row = conn.execute("SELECT 1 FROM paired_devices WHERE device_id = ?", (device_id,)).fetchone()
    conn.close()
    return row is not None

def register_device(device_id: str, token: str, name: str, allow_overwrite: bool = False) -> bool:
    conn = get_db()
    h = hash_token(token)
    now = time.time()
    success = False
    try:
        with conn:
            existing = conn.execute("SELECT 1 FROM paired_devices WHERE device_id = ?", (device_id,)).fetchone()
            if existing and not allow_overwrite:
                return False
            conn.execute("""
                INSERT INTO paired_devices (device_id, token_hash, device_name, created_at, last_seen)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(device_id) DO UPDATE SET
                    token_hash = excluded.token_hash,
                    device_name = excluded.device_name,
                    last_seen = excluded.last_seen
            """, (device_id, h, name, now, now))
            success = True
    finally:
        conn.close()
    return success

def check_and_record_pairing_attempt(identifier: str, max_attempts: int = 5, window_seconds: int = 300) -> bool:
    """
    Returns True if attempt is permitted, False if rate limit exceeded.
    """
    conn = get_db()
    now = time.time()
    allowed = False
    try:
        with conn:
            conn.execute("DELETE FROM pairing_attempts WHERE last_attempt < ?", (now - window_seconds,))
            row = conn.execute("SELECT attempts, last_attempt FROM pairing_attempts WHERE identifier = ?", (identifier,)).fetchone()
            if row:
                if row["attempts"] >= max_attempts:
                    allowed = False
                else:
                    conn.execute("UPDATE pairing_attempts SET attempts = attempts + 1, last_attempt = ? WHERE identifier = ?", (now, identifier))
                    allowed = True
            else:
                conn.execute("INSERT INTO pairing_attempts (identifier, attempts, last_attempt) VALUES (?, 1, ?)", (identifier, now))
                allowed = True
    finally:
        conn.close()
    return allowed

def reset_pairing_attempts(identifier: str):
    conn = get_db()
    with conn:
        conn.execute("DELETE FROM pairing_attempts WHERE identifier = ?", (identifier,))
    conn.close()

def count_active_pairing_codes() -> int:
    conn = get_db()
    now = time.time()
    row = conn.execute("SELECT COUNT(*) as cnt FROM pairing_codes WHERE expires_at >= ?", (now,)).fetchone()
    count = row["cnt"] if row else 0
    conn.close()
    return count

def save_pairing_code(code: str, user_id: str, expires_in_seconds: int = 300):
    conn = get_db()
    expires_at = time.time() + expires_in_seconds
    with conn:
        conn.execute("DELETE FROM pairing_codes WHERE expires_at < ?", (time.time(),))
        conn.execute("INSERT OR REPLACE INTO pairing_codes (code, user_id, expires_at) VALUES (?, ?, ?)",
                     (code, user_id, expires_at))
    conn.close()

def consume_pairing_code(code: str) -> Optional[str]:
    conn = get_db()
    now = time.time()
    clean_code = code.strip().upper()
    user_id = None
    try:
        # SQLite 3.35+ supports DELETE ... RETURNING user_id, expires_at as a single atomic execution
        cursor = conn.execute(
            "DELETE FROM pairing_codes WHERE code = ? RETURNING user_id, expires_at",
            (clean_code,)
        )
        row = cursor.fetchone()
        conn.commit()
        if row and row["expires_at"] >= now:
            user_id = row["user_id"]
    except sqlite3.OperationalError:
        # Fallback with BEGIN IMMEDIATE for atomic select+delete if RETURNING is not supported
        try:
            conn.isolation_level = None
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT user_id, expires_at FROM pairing_codes WHERE code = ?", (clean_code,)).fetchone()
            if row:
                conn.execute("DELETE FROM pairing_codes WHERE code = ?", (clean_code,))
                if row["expires_at"] >= now:
                    user_id = row["user_id"]
            conn.execute("COMMIT")
        except Exception:
            try:
                conn.execute("ROLLBACK")
            except Exception:
                pass
    finally:
        conn.close()
    return user_id

def register_profile_token(profile: str, token: str):
    conn = get_db()
    h = hash_token(token)
    with conn:
        conn.execute("""
            INSERT INTO profile_tokens (profile, token_hash, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(profile) DO UPDATE SET
                token_hash = excluded.token_hash,
                created_at = excluded.created_at
        """, (profile.lower(), h, time.time()))
    conn.close()

def verify_profile_token_in_db(token: str) -> Optional[str]:
    conn = get_db()
    h = hash_token(token)
    rows = conn.execute("SELECT profile, token_hash FROM profile_tokens").fetchall()
    conn.close()
    for row in rows:
        if secrets.compare_digest(row["token_hash"], h):
            return row["profile"].lower()
    return None

def get_or_create_admin_token() -> str:
    env_token = os.environ.get("MOBILE_RELAY_ADMIN_TOKEN") or os.environ.get("HERMES_ADMIN_TOKEN")
    if env_token:
        return env_token.strip()

    conn = get_db()
    row = conn.execute("SELECT value FROM admin_settings WHERE key = 'admin_token'").fetchone()
    if row:
        token = row["value"]
    else:
        token = "adm_" + secrets.token_urlsafe(32)
        with conn:
            conn.execute("INSERT INTO admin_settings (key, value) VALUES ('admin_token', ?)", (token,))
    conn.close()
    return token

def verify_admin_token(token: str) -> bool:
    if not token:
        return False
    clean_token = token.strip()
    expected = get_or_create_admin_token()
    return secrets.compare_digest(clean_token, expected)

def log_audit(entry_id: str, device_id: str, profile: str, operation: str, status: str, message: str):
    conn = get_db()
    with conn:
        conn.execute("""
            INSERT INTO audit_logs (id, timestamp, device_id, profile, operation, status, message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (entry_id, time.time(), device_id, profile, operation, status, message))
    conn.close()

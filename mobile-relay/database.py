import sqlite3
import hashlib
import time
import os
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
    is_valid = (row["token_hash"] == h)
    if is_valid:
        conn.execute("UPDATE paired_devices SET last_seen = ? WHERE device_id = ?", (time.time(), device_id))
        conn.commit()
    conn.close()
    return is_valid

def register_device(device_id: str, token: str, name: str):
    conn = get_db()
    h = hash_token(token)
    now = time.time()
    with conn:
        conn.execute("""
            INSERT INTO paired_devices (device_id, token_hash, device_name, created_at, last_seen)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                token_hash = excluded.token_hash,
                device_name = excluded.device_name,
                last_seen = excluded.last_seen
        """, (device_id, h, name, now, now))
    conn.close()

def save_pairing_code(code: str, user_id: str, expires_in_seconds: int = 300):
    conn = get_db()
    expires_at = time.time() + expires_in_seconds
    with conn:
        conn.execute("INSERT OR REPLACE INTO pairing_codes (code, user_id, expires_at) VALUES (?, ?, ?)",
                     (code, user_id, expires_at))
    conn.close()

def consume_pairing_code(code: str) -> Optional[str]:
    conn = get_db()
    now = time.time()
    row = conn.execute("SELECT user_id, expires_at FROM pairing_codes WHERE code = ?", (code,)).fetchone()
    if not row:
        conn.close()
        return None
    if row["expires_at"] < now:
        conn.execute("DELETE FROM pairing_codes WHERE code = ?", (code,))
        conn.commit()
        conn.close()
        return None
    user_id = row["user_id"]
    conn.execute("DELETE FROM pairing_codes WHERE code = ?", (code,))
    conn.commit()
    conn.close()
    return user_id

def log_audit(entry_id: str, device_id: str, profile: str, operation: str, status: str, message: str):
    conn = get_db()
    with conn:
        conn.execute("""
            INSERT INTO audit_logs (id, timestamp, device_id, profile, operation, status, message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (entry_id, time.time(), device_id, profile, operation, status, message))
    conn.close()

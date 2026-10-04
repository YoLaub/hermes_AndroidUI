import asyncio
import json
import logging
import os
import secrets
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any, Dict, Optional, Tuple

from database import (
    atomic_register_or_update_device,
    check_and_record_pairing_attempt,
    consume_pairing_code,
    count_active_pairing_codes,
    init_db,
    is_auth_blocked,
    log_audit,
    purge_old_records,
    record_auth_failure,
    reset_pairing_attempts,
    retry_after_seconds,
    save_pairing_code,
    verify_admin_token,
    verify_device_token,
    verify_profile_token_in_db,
)
from fastapi import FastAPI, Header, HTTPException, Request, Response, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse
from models import (
    MobileCommand,
    MobileCommandArguments,
    MobileCommandResult,
    PairingGenerateRequest,
    PairingGenerateResponse,
    PairingVerifyRequest,
    PairingVerifyResponse,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("mobile-relay")

# Identifies this running relay. Exposed by /health and by mobile_control_status so
# the phone and John can prove they talk to the same instance (state is in memory).
INSTANCE_ID = uuid.uuid4().hex[:12]


def log_event(event: str, level: int = logging.INFO, **fields: Any) -> None:
    """One `event=... key=value` line, built from explicit fields only.

    Never pass tokens: keys that look like credentials are dropped as a safety net.
    """
    parts = [f"event={event}"]
    for key, value in fields.items():
        if any(w in key.lower() for w in ("token", "secret", "password")):
            continue
        text = "-" if value is None or value == "" else "_".join(str(value).split())
        parts.append(f"{key}={text[:96]}")
    logger.log(level, " ".join(parts))

async def _purge_loop(interval: float) -> None:
    while True:
        await asyncio.sleep(interval)
        try:
            result = await asyncio.to_thread(purge_old_records)
            if any(result.values()):
                log_event("records_purged", **result)
        except Exception as exc:  # keep purging on the next tick
            log_event("purge_failed", logging.WARNING, error=type(exc).__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    result = purge_old_records()
    if any(result.values()):
        log_event("records_purged", **result)
    proxy_headers = os.environ.get("FORWARDED_ALLOW_IPS")
    log_event("relay_started", instance=INSTANCE_ID,
              proxy_headers="configured" if proxy_headers else "default")
    if not proxy_headers:
        # Throttling is per source address. Behind a reverse proxy without this setting every client
        # looks like the proxy, so a few failed logins from anyone would lock everybody out.
        logger.warning(
            "FORWARDED_ALLOW_IPS is not set: client addresses are those of the direct peer. "
            "Behind a reverse proxy (Coolify/Traefik) set FORWARDED_ALLOW_IPS so the real client "
            "address from X-Forwarded-For is used, or failed authentication from one source "
            "will block every client."
        )
    interval = max(0.05, float(os.environ.get("MOBILE_RELAY_PURGE_INTERVAL_SECONDS", 3600)))
    purge_task = asyncio.create_task(_purge_loop(interval))
    logger.info("Mobile Relay service initialized successfully.")
    try:
        yield
    finally:
        purge_task.cancel()

app = FastAPI(title="Hermes Mobile Relay", version="1.0.0", lifespan=lifespan)

# No CORS middleware on purpose: the only clients are the Android app and MCP clients, never a
# browser, and a wildcard origin with credentials would let any web page talk to the relay.

# ── Active State & Connection Management (In-Memory) ─────────────────────────

class ActiveSession:
    def __init__(self, session_id: str, device_id: str, target_package: str, allowed_profile: str, mode: str, expires_at: float):
        prof = allowed_profile.strip().lower()
        if prof != "john":
            raise ValueError(f"Profil non autorisé '{allowed_profile}'. Seul le profil 'john' est autorisé.")
        self.session_id = session_id
        self.device_id = device_id
        self.target_package = target_package
        self.allowed_profile = "john"
        self.mode = mode.lower()
        self.expires_at = expires_at

    @property
    def is_expired(self) -> bool:
        return time.time() >= self.expires_at

class DeviceConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.active_sessions: Dict[str, ActiveSession] = {} # device_id -> ActiveSession
        # Pending commands: command_id -> (Future[MobileCommandResult], target_device_id, expires_at)
        self.pending_commands: Dict[str, Tuple[asyncio.Future, str, float]] = {}
        # Concurrency limit: device_id -> command_id currently in flight
        self.device_in_flight: Dict[str, str] = {}

    def register_connection(self, device_id: str, websocket: WebSocket):
        previous = self.active_connections.get(device_id)
        self.active_connections[device_id] = websocket
        if previous is not None and previous is not websocket:
            log_event("connection_superseded", device_id=device_id)
        logger.info(f"Device connected: {device_id}")

    def unregister_connection(self, device_id: str, websocket: Optional[WebSocket] = None):
        if websocket is not None:
            current_ws = self.active_connections.get(device_id)
            if current_ws != websocket:
                logger.info(f"Ignoring unregister for device {device_id} from stale/superseded connection")
                return
        self.active_connections.pop(device_id, None)
        self.active_sessions.pop(device_id, None)
        self.device_in_flight.pop(device_id, None)
        logger.info(f"Device disconnected: {device_id}")

    def set_active_session(self, session: ActiveSession):
        if session.allowed_profile != "john":
            logger.warning(f"Refusing to activate session for unauthorized profile: {session.allowed_profile}")
            return False
        self.active_sessions[session.device_id] = session
        logger.info(f"Active session started on device {session.device_id}: {session.session_id} (target: {session.target_package}, profile: {session.allowed_profile}, mode: {session.mode})")
        return True

    def end_active_session(self, device_id: str, session_id: Optional[str] = None):
        current = self.active_sessions.get(device_id)
        if current and (session_id is None or current.session_id == session_id):
            self.active_sessions.pop(device_id, None)
            logger.info(f"Active session ended on device {device_id}")

    def get_session_for_profile(self, profile: str) -> Optional[ActiveSession]:
        p = profile.lower()
        for session in list(self.active_sessions.values()):
            if session.allowed_profile == p:
                if session.is_expired:
                    self.active_sessions.pop(session.device_id, None)
                else:
                    return session
        return None

    async def send_command_to_device(self, device_id: str, cmd: MobileCommand, timeout: float = 25.0) -> MobileCommandResult:
        ws = self.active_connections.get(device_id)
        if not ws:
            return MobileCommandResult(
                command_id=cmd.command_id,
                status="rejected",
                error_code="DEVICE_OFFLINE",
                message="Le téléphone n'est pas connecté au relais."
            )

        # Enforce single command in flight per device
        if device_id in self.device_in_flight:
            current_cmd_id = self.device_in_flight[device_id]
            logger.warning(f"Device {device_id} is busy with command {current_cmd_id}, rejecting {cmd.command_id}")
            return MobileCommandResult(
                command_id=cmd.command_id,
                status="rejected",
                error_code="CONCURRENT_COMMAND_DENIED",
                message=f"Une commande est déjà en cours d'exécution sur cet appareil ({current_cmd_id})."
            )

        loop = asyncio.get_running_loop()
        future = loop.create_future()
        expires_at = time.time() + timeout

        self.device_in_flight[device_id] = cmd.command_id
        self.pending_commands[cmd.command_id] = (future, device_id, expires_at)

        try:
            try:
                await ws.send_text(cmd.model_dump_json())
            except Exception as exc:
                # The socket looked alive but is not: drop it and its session, and say so
                # instead of failing the whole MCP request.
                log_event("device_send_failed", logging.WARNING, device_id=device_id,
                          command_id=cmd.command_id, error=type(exc).__name__)
                self.unregister_connection(device_id, ws)
                return MobileCommandResult(
                    command_id=cmd.command_id,
                    status="rejected",
                    error_code="DEVICE_OFFLINE",
                    message="Le téléphone n'est plus joignable (connexion fermée). La session du relais a été supprimée : redémarrez-la depuis l'app.",
                )
            result = await asyncio.wait_for(future, timeout=timeout)
            return result
        except asyncio.TimeoutError:
            logger.warning(f"Command {cmd.command_id} timed out on device {device_id}")
            return MobileCommandResult(
                command_id=cmd.command_id,
                status="rejected",
                error_code="RESULT_UNKNOWN",
                message="Délai d'exécution dépassé sans confirmation du téléphone."
            )
        finally:
            self.device_in_flight.pop(device_id, None)
            self.pending_commands.pop(cmd.command_id, None)

    def resolve_command_result(self, from_device_id: str, result: MobileCommandResult):
        pending = self.pending_commands.get(result.command_id)
        if not pending:
            logger.warning(f"Ignoring result for unknown or expired command_id: {result.command_id} from device {from_device_id}")
            return

        future, target_device_id, _ = pending
        # Security: Accept results ONLY from the device that the command was sent to
        if target_device_id != from_device_id:
            logger.error(f"SECURITY ALERT: Received result for {result.command_id} from {from_device_id} but command was sent to {target_device_id}!")
            return

        if not future.done():
            future.set_result(result)

manager = DeviceConnectionManager()

# ── Authentication Helpers ───────────────────────────────────────────────────

def client_ip(conn) -> str:
    """Source address for throttling. Behind a reverse proxy this is the real client only if uvicorn
    trusts the proxy's X-Forwarded-For (FORWARDED_ALLOW_IPS); otherwise it is the proxy's address."""
    return conn.client.host if getattr(conn, "client", None) else "unknown"


def enforce_not_blocked(source: str, scope: str) -> None:
    """429 for a source that sent too many rejected credentials recently (even if this one is right)."""
    if is_auth_blocked(source):
        log_event("auth_blocked", logging.WARNING, source=source, scope=scope)
        raise HTTPException(
            status_code=429,
            detail="Too many failed authentication attempts. Try again later.",
            headers={"Retry-After": str(retry_after_seconds(source))},
        )


def note_auth_failure(source: str, scope: str) -> None:
    record_auth_failure(source)
    log_event("auth_failed", logging.WARNING, source=source, scope=scope)


def authenticate_admin_request(request: Request):
    auth_header = request.headers.get("Authorization", "")
    token = None
    if auth_header.startswith("Bearer "):
        token = auth_header[7:].strip()
    elif "X-Admin-Token" in request.headers:
        token = request.headers["X-Admin-Token"].strip()

    source = client_ip(request)
    enforce_not_blocked(source, "admin")
    if not token:
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing admin token.")
    if not verify_admin_token(token):
        note_auth_failure(source, "admin")
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing admin token.")

def authenticate_hermes_profile(request: Request) -> str:
    """
    Validates token from Authorization or X-Hermes-Token and strictly deduces
    the authorized profile. Never trusts user-supplied profile headers blindly.
    Tokens in URLs / query params are strictly rejected.
    """
    token = None
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header[7:].strip()
    elif "X-Hermes-Token" in request.headers:
        token = request.headers["X-Hermes-Token"].strip()

    source = client_ip(request)
    enforce_not_blocked(source, "mcp")

    if not token:
        raise HTTPException(
            status_code=401,
            detail="Unauthorized: Missing Hermes profile token (Authorization: Bearer <token> or X-Hermes-Token)."
        )

    # 1. Check environment profile tokens mapping: strictly {profile: token}
    env_tokens_json = os.environ.get("HERMES_PROFILE_TOKENS")
    if env_tokens_json:
        try:
            mapping = json.loads(env_tokens_json)
            if isinstance(mapping, dict):
                # Only compare the provided token against the configured token (dict value)
                for profile, configured_token in mapping.items():
                    if secrets.compare_digest(str(configured_token).strip(), token):
                        return profile.lower()
        except Exception as e:
            logger.error(f"Failed to parse HERMES_PROFILE_TOKENS: {e}")

    # 2. Check individual environment variables
    for prof in ["mario", "gaston", "john"]:
        env_val = os.environ.get(f"MOBILE_CONTROL_TOKEN_{prof.upper()}")
        if env_val and secrets.compare_digest(env_val.strip(), token):
            return prof

    # 3. Check generic MOBILE_CONTROL_TOKEN (maps to default mobile control profile john)
    generic_token = os.environ.get("MOBILE_CONTROL_TOKEN")
    if generic_token and secrets.compare_digest(generic_token.strip(), token):
        return "john"

    # 4. Check SQLite database profile_tokens
    db_profile = verify_profile_token_in_db(token)
    if db_profile:
        return db_profile

    # If test mode allows fallback test token
    test_token = os.environ.get("MOBILE_CONTROL_TEST_TOKEN")
    if test_token and secrets.compare_digest(test_token.strip(), token):
        return "john"

    note_auth_failure(source, "mcp")
    raise HTTPException(status_code=401, detail="Unauthorized: Invalid Hermes profile token.")

# ── Health & Pairing Routes ──────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "service": "hermes-mobile-relay", "instance_id": INSTANCE_ID, "connected_devices": len(manager.active_connections)}

@app.post("/api/pair/generate", response_model=PairingGenerateResponse)
def generate_pairing_code(request: Request, req: PairingGenerateRequest):
    # Requirement 2: Authentifier /api/pair/generate
    authenticate_admin_request(request)

    # Quota check
    if count_active_pairing_codes() >= 10:
        raise HTTPException(status_code=429, detail="Too many active pairing codes. Please wait or use existing codes.")

    code = secrets.token_hex(3).upper() # 6 characters
    save_pairing_code(code, req.user_id or "admin", expires_in_seconds=300)
    # Requirement 2: Ne jamais journaliser ce code
    logger.info("Generated pairing code for user: %s (expires in 300s)", req.user_id or "admin")
    return PairingGenerateResponse(code=code, expires_in_seconds=300)

@app.post("/api/pair/verify", response_model=PairingVerifyResponse)
def verify_pairing_code(request: Request, req: PairingVerifyRequest):
    client_ip = request.client.host if request.client else "unknown"
    rate_key_ip = f"ip_{client_ip}"

    # Rate limiting strictly on client IP (independent of device_id to prevent bypass)
    if not check_and_record_pairing_attempt(rate_key_ip, max_attempts=5, window_seconds=300):
        raise HTTPException(
            status_code=429,
            detail="Trop de tentatives de vérification depuis cette adresse IP. Veuillez patienter avant de réessayer."
        )

    # 1. Extract candidate auth tokens from headers / payload
    candidate_device_token = req.current_device_token or request.headers.get("X-Device-Token")
    candidate_admin_token = req.admin_token or request.headers.get("X-Admin-Token")
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        bearer_tok = auth_header[7:].strip()
        if not candidate_admin_token and verify_admin_token(bearer_tok):
            candidate_admin_token = bearer_tok
        if not candidate_device_token:
            candidate_device_token = bearer_tok

    is_admin_auth = bool(candidate_admin_token and verify_admin_token(candidate_admin_token))

    # 2. Consume pairing code atomically FIRST
    user_id = consume_pairing_code(req.code.strip().upper())
    if not user_id:
        raise HTTPException(status_code=400, detail="Code d'appairage invalide ou expiré.")

    # 3. Perform atomic existence check, replacement authorization, and INSERT / UPDATE in a single transaction
    device_token = "tok_" + secrets.token_urlsafe(24)
    success, reason = atomic_register_or_update_device(
        device_id=req.device_id,
        new_token=device_token,
        device_name=req.device_name or "Android Phone",
        current_token=candidate_device_token,
        is_admin_authorized=is_admin_auth
    )

    if not success:
        if reason == "UNAUTHORIZED_OVERWRITE":
            raise HTTPException(
                status_code=403,
                detail="Ce téléphone est déjà enregistré. L'écrasement nécessite le token actuel de l'appareil ou une autorisation administrateur dédiée."
            )
        else:
            raise HTTPException(status_code=409, detail="Impossible d'enregistrer l'appareil.")

    # Reset failed attempts on success
    reset_pairing_attempts(rate_key_ip)

    logger.info("Paired device %s successfully (%s) for user %s.", req.device_id, reason, user_id)
    return PairingVerifyResponse(ok=True, device_token=device_token)

# ── WebSocket Device Endpoint ────────────────────────────────────────────────

def session_state_message(device_id: str) -> dict:
    """The relay's own view of this device's session (the source of truth)."""
    session = manager.active_sessions.get(device_id)
    if session is not None and session.is_expired:
        manager.active_sessions.pop(device_id, None)
        session = None
    msg: Dict[str, Any] = {"protocol": "mobile-control/1", "type": "session_state", "active": session is not None}
    if session is not None:
        msg.update({
            "session_id": session.session_id,
            "profile": session.allowed_profile,
            "device_id": session.device_id,
            "target_package": session.target_package,
            "mode": session.mode,
            "expires_in_seconds": max(0, int(session.expires_at - time.time())),
        })
    return msg


async def refuse_session(websocket: WebSocket, device_id: Optional[str], session_id: str, code: str, message: str):
    log_event("session_refused", logging.WARNING, device_id=device_id, session_id=session_id, reason=code)
    await websocket.send_text(json.dumps({
        "protocol": "mobile-control/1",
        "type": "session_error",
        "session_id": session_id,
        "error_code": code,
        "message": message,
    }))


@app.websocket("/ws/device")
async def websocket_device_endpoint(
    websocket: WebSocket,
    x_device_id: Optional[str] = Header(None),
    x_device_token: Optional[str] = Header(None)
):
    await websocket.accept()
    authenticated_device_id = None
    close_code: Any = None
    close_reason = "unknown"
    source = client_ip(websocket)
    failure_counted = False  # a bad header plus a bad auth message is one failed attempt, not two

    try:
        # Initial authentication via headers if present (skipped for a blocked source)
        if x_device_id and x_device_token and not is_auth_blocked(source):
            if verify_device_token(x_device_id, x_device_token):
                authenticated_device_id = x_device_id
                manager.register_connection(authenticated_device_id, websocket)
                log_event("device_authenticated", device_id=authenticated_device_id, via="header")
            else:
                note_auth_failure(source, "ws")
                failure_counted = True

        while True:
            text = await websocket.receive_text()
            try:
                data = json.loads(text)
            except Exception:
                continue

            msg_type = data.get("type", "")

            if msg_type == "auth":
                dev_id = data.get("device_id")
                token = data.get("device_token")
                if is_auth_blocked(source):
                    log_event("auth_blocked", logging.WARNING, source=source, scope="ws", device_id=dev_id)
                    await websocket.send_text(json.dumps({
                        "protocol": "mobile-control/1", "type": "auth_error",
                        "error_code": "AUTH_RATE_LIMITED",
                        "message": "Trop d'échecs d'authentification depuis cette adresse. Réessayez plus tard.",
                    }))
                    close_reason = "auth_rate_limited"
                    await websocket.close()
                    break
                if dev_id and token and verify_device_token(dev_id, token):
                    authenticated_device_id = dev_id
                    manager.register_connection(dev_id, websocket)
                    log_event("device_authenticated", device_id=dev_id, via="message")
                    await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth_ok"}))
                    # Reconnection sync: tell the phone what the relay actually holds.
                    await websocket.send_text(json.dumps(session_state_message(dev_id)))
                else:
                    if not failure_counted:
                        note_auth_failure(source, "ws")
                        failure_counted = True
                    log_event("device_auth_failed", logging.WARNING, device_id=dev_id,
                              reason="UNKNOWN_DEVICE_OR_BAD_CREDENTIALS")
                    await websocket.send_text(json.dumps({
                        "protocol": "mobile-control/1", "type": "auth_error",
                        "error_code": "DEVICE_AUTH_FAILED",
                        "message": "Identifiants invalides.",
                    }))
                    close_reason = "auth_failed"
                    await websocket.close()
                    break

            elif msg_type == "session_start":
                req_session_id = str(data.get("session_id", ""))[:64]
                req_profile = str(data.get("allowed_profile", "")).strip().lower()
                log_event("session_start_received", device_id=authenticated_device_id,
                          session_id=req_session_id, profile_requested=req_profile,
                          target=data.get("target_package"), mode=data.get("mode"))

                if not authenticated_device_id:
                    await refuse_session(websocket, None, req_session_id, "DEVICE_NOT_AUTHENTICATED",
                                         "Appareil non authentifié : appairez à nouveau le téléphone.")
                    continue

                # Server-side restriction: Reject any session whose allowed_profile is not 'john'
                if req_profile != "john":
                    await refuse_session(websocket, authenticated_device_id, req_session_id, "PROFILE_NOT_ALLOWED",
                                         f"Seul le profil 'john' est autorisé pour le contrôle mobile (reçu: '{req_profile}').")
                    continue

                # Durée maximale bornée côté relais (max 1800s / 30 min)
                try:
                    duration = int(data.get("duration_seconds", 900))
                except (ValueError, TypeError):
                    duration = 900
                bounded_duration = max(60, min(duration, 1800))

                try:
                    session = ActiveSession(
                        session_id=data.get("session_id", str(uuid.uuid4())),
                        device_id=authenticated_device_id,
                        target_package=data.get("target_package", ""),
                        allowed_profile="john",
                        mode=data.get("mode", "interaction"),
                        expires_at=time.time() + bounded_duration
                    )
                except ValueError as ve:
                    await refuse_session(websocket, authenticated_device_id, req_session_id, "PROFILE_NOT_ALLOWED", str(ve))
                    continue

                if not manager.set_active_session(session):
                    await refuse_session(websocket, authenticated_device_id, session.session_id,
                                         "SESSION_REJECTED", "Le relais a refusé d'enregistrer la session.")
                    continue

                log_event("session_registered", device_id=session.device_id, session_id=session.session_id,
                          profile=session.allowed_profile, target=session.target_package,
                          mode=session.mode, expires_in=bounded_duration)
                log_audit(str(uuid.uuid4()), authenticated_device_id, session.allowed_profile, "SESSION_START", "STARTED", f"Package: {session.target_package}, mode: {session.mode}, duration: {bounded_duration}s")
                await websocket.send_text(json.dumps({
                    "protocol": "mobile-control/1",
                    "type": "session_started_ack",
                    "session_id": session.session_id,
                    "profile": session.allowed_profile,
                    "device_id": session.device_id,
                    "expires_in_seconds": bounded_duration,
                }))
                log_event("session_ack_sent", device_id=session.device_id, session_id=session.session_id)

            elif msg_type == "session_end":
                if not authenticated_device_id:
                    continue
                sid = data.get("session_id")
                manager.end_active_session(authenticated_device_id, sid)
                log_audit(str(uuid.uuid4()), authenticated_device_id, "", "SESSION_END", "STOPPED", data.get("reason", "user_cancelled"))

            elif msg_type == "result":
                # Requirement 5: Accepter uniquement depuis le téléphone authentifié
                if not authenticated_device_id:
                    logger.warning("Rejected result message from unauthenticated WebSocket connection.")
                    continue
                res = MobileCommandResult(**data)
                manager.resolve_command_result(authenticated_device_id, res)

            elif msg_type == "ping":
                await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "pong"}))

    except WebSocketDisconnect as wd:
        close_code, close_reason = wd.code, (wd.reason or "client_disconnected")
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id, websocket)
    except Exception as e:
        # Log the exception type only: its message could echo request content.
        close_reason = f"error:{type(e).__name__}"
        logger.error(f"WebSocket exception for device {authenticated_device_id}: {type(e).__name__}")
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id, websocket)
    finally:
        log_event("connection_closed", device_id=authenticated_device_id, code=close_code, reason=close_reason)

# ── MCP Streamable HTTP Endpoint for Hermes Profiles ─────────────────────────

MCP_TOOLS = [
    {
        "name": "mobile_control_status",
        "description": "Vérifie si une session de contrôle mobile est active sur le téléphone Android pour votre profil Hermes.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "mobile_observe",
        "description": "Observe l'interface actuellement affichée de l'application autorisée et retourne les éléments textuels et interactifs avec leurs identifiants (element_ref et screen_revision).",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "mobile_launch_allowed_app",
        "description": "Ouvre l'application autorisée (ex: LinkedIn) sur le téléphone au premier plan.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "mobile_click_element",
        "description": "Effectue un clic sur un élément interactif (bouton, lien) à l'aide de son element_ref et de la screen_revision la plus récente.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "element_ref": {"type": "string", "description": "Référence de l'élément (ex: el_1, el_42)"},
                "screen_revision": {"type": "string", "description": "Référence de révision de l'écran retournée lors de la dernière observation"}
            },
            "required": ["element_ref"]
        }
    },
    {
        "name": "mobile_scroll",
        "description": "Fait défiler l'application autorisée vers le bas ('down') ou vers le haut ('up').",
        "inputSchema": {
            "type": "object",
            "properties": {
                "direction": {"type": "string", "enum": ["up", "down"], "default": "down", "description": "Direction du défilement"}
            }
        }
    },
    {
        "name": "mobile_set_text",
        "description": "Saisit du texte dans un champ de formulaire ou un champ de commentaire sélectionné.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "element_ref": {"type": "string", "description": "Référence du champ de saisie"},
                "text": {"type": "string", "description": "Texte à insérer"},
                "screen_revision": {"type": "string", "description": "Référence de révision de l'écran"}
            },
            "required": ["element_ref", "text"]
        }
    },
    {
        "name": "mobile_back",
        "description": "Effectue l'action de retour arrière système (Back) dans l'application autorisée.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "mobile_end_session",
        "description": "Termine la session de contrôle mobile.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    }
]

INTERACTION_OPERATIONS = {"click_element", "set_text", "scroll", "launch_app", "back"}

# Phone-side answers meaning "the relay thinks a session exists, the phone disagrees".
PHONE_SESSION_DESYNC_CODES = {"SESSION_NOT_ON_PHONE", "SESSION_ID_MISMATCH"}

SUPPORTED_MCP_PROTOCOL_VERSIONS = ["2025-03-26", "2024-11-05"]
DEFAULT_MCP_PROTOCOL_VERSION = "2025-03-26"

@app.post("/mcp")
@app.post("/mcp/v1/stream")
async def mcp_stream_endpoint(request: Request):
    # Requirement 1: Authentifier /mcp et déduire le profil uniquement du token
    authenticated_profile = authenticate_hermes_profile(request)

    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON-RPC request") from None

    method = body.get("method")
    req_id = body.get("id")
    params = body.get("params", {})

    # Protocol version negotiation
    client_version = request.headers.get("MCP-Protocol-Version") or (params.get("protocolVersion") if isinstance(params, dict) else None)
    if client_version in SUPPORTED_MCP_PROTOCOL_VERSIONS:
        negotiated_version = client_version
    else:
        negotiated_version = DEFAULT_MCP_PROTOCOL_VERSION

    # Initialize handshake
    if method == "initialize":
        return JSONResponse(
            status_code=200,
            headers={"MCP-Protocol-Version": negotiated_version},
            content={
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": negotiated_version,
                    "capabilities": {
                        "tools": { "listChanged": False }
                    },
                    "serverInfo": {
                        "name": "hermes-mobile-relay",
                        "version": "1.0.0"
                    }
                }
            }
        )

    # Handle notifications (e.g. notifications/initialized) - return HTTP 202 Accepted
    if (method and method.startswith("notifications/")) or (req_id is None):
        return Response(status_code=202, headers={"MCP-Protocol-Version": negotiated_version})

    if method == "ping":
        return JSONResponse(
            status_code=200,
            headers={"MCP-Protocol-Version": negotiated_version},
            content={
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {}
            }
        )

    if method == "tools/list":
        return JSONResponse(
            status_code=200,
            headers={"MCP-Protocol-Version": negotiated_version},
            content={
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "tools": MCP_TOOLS
                }
            }
        )

    elif method == "tools/call":
        tool_name = params.get("name")
        arguments = params.get("arguments", {})

        # 1. mobile_control_status
        if tool_name == "mobile_control_status":
            known_profiles = sorted({sess.allowed_profile for sess in manager.active_sessions.values()})
            sessions_known = len(manager.active_sessions)
            session = manager.get_session_for_profile(authenticated_profile)
            log_event("status_lookup", profile=authenticated_profile, sessions_known=sessions_known,
                      session_profiles=",".join(known_profiles), match=str(session is not None).lower(),
                      instance=INSTANCE_ID)
            if session:
                remaining = int(session.expires_at - time.time())
                connected = "oui" if session.device_id in manager.active_connections else "non"
                return format_mcp_response(
                    req_id,
                    f"Session active trouvée sur le téléphone.\nSession : {session.session_id}\nAppareil : {session.device_id} (connecté au relais : {connected})\nProfil : {session.allowed_profile}\nApplication : {session.target_package}\nMode : {session.mode}\nTemps restant : {remaining // 60}m {remaining % 60}s\nRelais : {INSTANCE_ID}",
                    protocol_version=negotiated_version
                )
            else:
                return format_mcp_response(
                    req_id,
                    f"Aucune session de contrôle mobile n'est actuellement active pour le profil '{authenticated_profile}'. L'utilisateur doit démarrer une session sur son application Hermes Android. (Relais : {INSTANCE_ID})",
                    protocol_version=negotiated_version
                )

        # 2. All other tools require an active validated session for this authenticated profile
        session = manager.get_session_for_profile(authenticated_profile)
        if not session:
            log_event("mcp_tool_refused", logging.WARNING, tool=tool_name, profile=authenticated_profile,
                      reason="SESSION_REQUIRED", sessions_known=len(manager.active_sessions),
                      session_profiles=",".join(sorted({x.allowed_profile for x in manager.active_sessions.values()})))
            return format_mcp_error(
                req_id,
                f"SESSION_REQUIRED: Le relais n'a aucune session active pour le profil '{authenticated_profile}'. Demandez à l'utilisateur de lancer une session dans l'app Hermes.",
                protocol_version=negotiated_version
            )

        # Vérifier l'expiration côté relais
        if session.is_expired:
            log_event("mcp_tool_refused", logging.WARNING, tool=tool_name, profile=authenticated_profile,
                      reason="SESSION_EXPIRED", session_id=session.session_id)
            manager.end_active_session(session.device_id, session.session_id)
            return format_mcp_error(
                req_id,
                "SESSION_EXPIRED: La session de contrôle a expiré. Une nouvelle session doit être démarrée sur le téléphone.",
                protocol_version=negotiated_version
            )

        op_map = {
            "mobile_observe": "observe",
            "mobile_launch_allowed_app": "launch_app",
            "mobile_click_element": "click_element",
            "mobile_scroll": "scroll",
            "mobile_set_text": "set_text",
            "mobile_back": "back",
            "mobile_end_session": "end_session"
        }

        op = op_map.get(tool_name)
        if not op:
            return format_mcp_error(req_id, f"UNSUPPORTED_TOOL: Outil inconnu '{tool_name}'.", protocol_version=negotiated_version)

        # Permissions: Refuser tout mode autre que l'exact mode 'interaction' pour les opérations interactives
        if op in INTERACTION_OPERATIONS and session.mode != "interaction":
            log_event("mcp_tool_refused", logging.WARNING, tool=tool_name, profile=authenticated_profile,
                      reason="MODE_DENIED", session_id=session.session_id, mode=session.mode)
            return format_mcp_error(
                req_id,
                f"MODE_DENIED: Le mode actuel de la session est '{session.mode}'. Seul le mode 'interaction' autorise les actions interactives ({op}).",
                protocol_version=negotiated_version
            )

        cmd = MobileCommand(
            command_id="cmd_" + str(uuid.uuid4()),
            session_id=session.session_id,
            device_id=session.device_id,
            operation=op,
            target_package=session.target_package,
            screen_revision=arguments.get("screen_revision"),
            expires_at=int((time.time() + 25) * 1000),
            arguments=MobileCommandArguments(
                element_ref=arguments.get("element_ref"),
                text=arguments.get("text"),
                direction=arguments.get("direction", "down")
            )
        )

        log_event("mcp_tool_call", tool=tool_name, profile=authenticated_profile, session_id=session.session_id,
                  device_id=session.device_id, mode=session.mode, command_id=cmd.command_id,
                  device_connected=str(session.device_id in manager.active_connections).lower())
        result = await manager.send_command_to_device(session.device_id, cmd)
        log_event("mcp_tool_result", tool=tool_name, session_id=session.session_id, device_id=session.device_id,
                  command_id=cmd.command_id, status=result.status, error_code=result.error_code)

        # The phone says it has no (or another) session: the relay's view is stale. Close it,
        # so the two sides agree again instead of status saying "active" while the phone refuses.
        if result.error_code in PHONE_SESSION_DESYNC_CODES:
            manager.end_active_session(session.device_id, session.session_id)
            log_event("session_desync", logging.WARNING, device_id=session.device_id,
                      session_id=session.session_id, phone_code=result.error_code)
            return format_mcp_error(
                req_id,
                f"{result.error_code}: {result.message or 'Session absente sur le téléphone.'} "
                "La session côté relais a été fermée : le téléphone doit démarrer une nouvelle session.",
                protocol_version=negotiated_version,
            )

        if result.status == "success":
            if result.data and result.data.elements:
                elements_summary = []
                for el in result.data.elements:
                    attrs = []
                    if el.clickable:
                        attrs.append("clickable")
                    if el.editable:
                        attrs.append("editable")
                    if el.scrollable:
                        attrs.append("scrollable")
                    attr_str = f" [{', '.join(attrs)}]" if attrs else ""
                    c_name = el.class_name or "View"
                    text_display = f"\"{el.text}\"" if el.text else (f"desc=\"{el.content_desc}\"" if el.content_desc else c_name)
                    elements_summary.append(f"- [{el.element_ref}] {c_name}: {text_display}{attr_str}")

                content = (
                    f"Observation de {result.data.package_name} (Révision: {result.data.screen_revision}):\n" +
                    "\n".join(elements_summary)
                )
                return format_mcp_response(req_id, content, protocol_version=negotiated_version)
            else:
                return format_mcp_response(req_id, result.message or "Action exécutée avec succès.", protocol_version=negotiated_version)
        else:
            error_code = result.error_code or "ACTION_FAILED"
            error_message = (
                result.message or "Erreur lors de l'exécution sur le téléphone"
            )
            return format_mcp_error(
                req_id,
                f"{error_code}: {error_message}",
                protocol_version=negotiated_version,
            )

    else:
        return JSONResponse(
            status_code=200,
            headers={"MCP-Protocol-Version": negotiated_version},
            content={
                "jsonrpc": "2.0",
                "id": req_id,
                "error": {
                    "code": -32601,
                    "message": f"Method '{method}' not found"
                }
            }
        )

def format_mcp_response(req_id: Any, text: str, protocol_version: str = DEFAULT_MCP_PROTOCOL_VERSION) -> JSONResponse:
    return JSONResponse(
        status_code=200,
        headers={"MCP-Protocol-Version": protocol_version},
        content={
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "content": [
                    {
                        "type": "text",
                        "text": text
                    }
                ]
            }
        }
    )

def format_mcp_error(req_id: Any, message: str, protocol_version: str = DEFAULT_MCP_PROTOCOL_VERSION) -> JSONResponse:
    return JSONResponse(
        status_code=200,
        headers={"MCP-Protocol-Version": protocol_version},
        content={
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "isError": True,
                "content": [
                    {
                        "type": "text",
                        "text": message
                    }
                ]
            }
        }
    )


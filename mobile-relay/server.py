import asyncio
import json
import logging
import os
import secrets
import time
import uuid
from contextlib import asynccontextmanager
from typing import Dict, Optional, Any, Tuple

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Header, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from database import (
    init_db,
    verify_device_token,
    register_device,
    is_device_registered,
    save_pairing_code,
    consume_pairing_code,
    count_active_pairing_codes,
    check_and_record_pairing_attempt,
    reset_pairing_attempts,
    verify_admin_token,
    verify_profile_token_in_db,
    log_audit
)
from models import (
    MobileCommand,
    MobileCommandResult,
    MobileCommandArguments,
    PairingGenerateRequest,
    PairingGenerateResponse,
    PairingVerifyRequest,
    PairingVerifyResponse
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("mobile-relay")

@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    logger.info("Mobile Relay service initialized successfully.")
    yield

app = FastAPI(title="Hermes Mobile Relay", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Active State & Connection Management (In-Memory) ─────────────────────────

class ActiveSession:
    def __init__(self, session_id: str, device_id: str, target_package: str, allowed_profile: str, mode: str, expires_at: float):
        self.session_id = session_id
        self.device_id = device_id
        self.target_package = target_package
        self.allowed_profile = allowed_profile.lower()
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
        self.active_connections[device_id] = websocket
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
        self.active_sessions[session.device_id] = session
        logger.info(f"Active session started on device {session.device_id}: {session.session_id} (target: {session.target_package}, profile: {session.allowed_profile}, mode: {session.mode})")

    def end_active_session(self, device_id: str, session_id: Optional[str] = None):
        current = self.active_sessions.get(device_id)
        if current and (session_id is None or current.session_id == session_id):
            self.active_sessions.pop(device_id, None)
            logger.info(f"Active session ended on device {device_id}")

    def get_session_for_profile(self, profile: str) -> Optional[ActiveSession]:
        p = profile.lower()
        now = time.time()
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
            await ws.send_text(cmd.model_dump_json())
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

def authenticate_admin_request(request: Request):
    auth_header = request.headers.get("Authorization", "")
    token = None
    if auth_header.startswith("Bearer "):
        token = auth_header[7:].strip()
    elif "X-Admin-Token" in request.headers:
        token = request.headers["X-Admin-Token"].strip()

    if not token or not verify_admin_token(token):
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

    # 3. Check generic MOBILE_CONTROL_TOKEN (maps to default pilot profile mario)
    generic_token = os.environ.get("MOBILE_CONTROL_TOKEN")
    if generic_token and secrets.compare_digest(generic_token.strip(), token):
        return "mario"

    # 4. Check SQLite database profile_tokens
    db_profile = verify_profile_token_in_db(token)
    if db_profile:
        return db_profile

    # If test mode allows fallback test token
    test_token = os.environ.get("MOBILE_CONTROL_TEST_TOKEN")
    if test_token and secrets.compare_digest(test_token.strip(), token):
        return "mario"

    raise HTTPException(status_code=401, detail="Unauthorized: Invalid Hermes profile token.")

# ── Health & Pairing Routes ──────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "service": "hermes-mobile-relay", "connected_devices": len(manager.active_connections)}

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
    rate_key = f"pair_{client_ip}_{req.device_id}"

    # Rate limiting on pairing verification attempts
    if not check_and_record_pairing_attempt(rate_key, max_attempts=5, window_seconds=300):
        raise HTTPException(
            status_code=429,
            detail="Trop de tentatives de vérification. Veuillez patienter avant de réessayer."
        )

    # Prevent overwriting an already paired device without explicit authorization
    if is_device_registered(req.device_id) and not req.allow_overwrite:
        raise HTTPException(
            status_code=409,
            detail="Ce téléphone est déjà enregistré. L'écrasement sans autorisation explicite est refusé."
        )

    user_id = consume_pairing_code(req.code.strip().upper())
    if not user_id:
        raise HTTPException(status_code=400, detail="Code d'appairage invalide ou expiré.")

    reset_pairing_attempts(rate_key)
    device_token = "tok_" + secrets.token_urlsafe(24)
    if not register_device(req.device_id, device_token, req.device_name or "Android Phone", allow_overwrite=bool(req.allow_overwrite)):
        raise HTTPException(status_code=409, detail="Impossible d'enregistrer l'appareil.")

    logger.info("Paired device %s successfully for user %s.", req.device_id, user_id)
    return PairingVerifyResponse(ok=True, device_token=device_token)

# ── WebSocket Device Endpoint ────────────────────────────────────────────────

@app.websocket("/ws/device")
async def websocket_device_endpoint(
    websocket: WebSocket,
    x_device_id: Optional[str] = Header(None),
    x_device_token: Optional[str] = Header(None)
):
    await websocket.accept()
    authenticated_device_id = None

    try:
        # Initial authentication via headers if present
        if x_device_id and x_device_token and verify_device_token(x_device_id, x_device_token):
            authenticated_device_id = x_device_id
            manager.register_connection(authenticated_device_id, websocket)

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
                if dev_id and token and verify_device_token(dev_id, token):
                    authenticated_device_id = dev_id
                    manager.register_connection(dev_id, websocket)
                    await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth_ok"}))
                else:
                    await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "auth_error", "message": "Identifiants invalides."}))
                    await websocket.close()
                    break

            elif msg_type == "session_start":
                if not authenticated_device_id:
                    continue

                # Requirement 6: Durée maximale bornée côté relais (max 1800s / 30 min)
                try:
                    duration = int(data.get("duration_seconds", 900))
                except (ValueError, TypeError):
                    duration = 900
                bounded_duration = max(60, min(duration, 1800))

                session = ActiveSession(
                    session_id=data.get("session_id", str(uuid.uuid4())),
                    device_id=authenticated_device_id,
                    target_package=data.get("target_package", ""),
                    allowed_profile=data.get("allowed_profile", "mario"),
                    mode=data.get("mode", "interaction"),
                    expires_at=time.time() + bounded_duration
                )
                manager.set_active_session(session)
                log_audit(str(uuid.uuid4()), authenticated_device_id, session.allowed_profile, "SESSION_START", "STARTED", f"Package: {session.target_package}, mode: {session.mode}, duration: {bounded_duration}s")
                await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "session_started_ack", "session_id": session.session_id}))

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

    except WebSocketDisconnect:
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id, websocket)
    except Exception as e:
        logger.error(f"WebSocket exception for device {authenticated_device_id}: {e}")
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id, websocket)

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

@app.post("/mcp")
@app.post("/mcp/v1/stream")
async def mcp_stream_endpoint(request: Request):
    # Requirement 1: Authentifier /mcp et déduire le profil uniquement du token
    authenticated_profile = authenticate_hermes_profile(request)

    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON-RPC request")

    method = body.get("method")
    req_id = body.get("id")
    params = body.get("params", {})

    # Requirement 3: Implémenter l'initialisation MCP et les notifications
    if method == "initialize":
        client_version = params.get("protocolVersion")
        supported_versions = ["2024-11-05"]
        # Protocol version negotiation: match requested version if supported, otherwise fallback to highest supported
        negotiated_version = client_version if client_version in supported_versions else "2024-11-05"

        return {
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

    # Handle notifications (e.g. notifications/initialized) - return HTTP 202 Accepted
    if (method and method.startswith("notifications/")) or (req_id is None):
        return Response(status_code=202)

    if method == "ping":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {}
        }

    if method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "tools": MCP_TOOLS
            }
        }

    elif method == "tools/call":
        tool_name = params.get("name")
        arguments = params.get("arguments", {})

        # 1. mobile_control_status
        if tool_name == "mobile_control_status":
            session = manager.get_session_for_profile(authenticated_profile)
            if session:
                remaining = int(session.expires_at - time.time())
                return format_mcp_response(
                    req_id,
                    f"Session active trouvée sur le téléphone.\nApplication : {session.target_package}\nMode : {session.mode}\nTemps restant : {remaining // 60}m {remaining % 60}s"
                )
            else:
                return format_mcp_response(
                    req_id,
                    f"Aucune session de contrôle mobile n'est actuellement active pour le profil '{authenticated_profile}'. L'utilisateur doit démarrer une session sur son application Hermes Android."
                )

        # 2. All other tools require an active validated session for this authenticated profile
        session = manager.get_session_for_profile(authenticated_profile)
        if not session:
            return format_mcp_error(
                req_id,
                f"SESSION_REQUIRED: Aucune session active pour le profil '{authenticated_profile}'. Demandez à l'utilisateur de lancer une session dans l'app Hermes."
            )

        # Requirement 6: Vérifier l'expiration côté relais
        if session.is_expired:
            manager.end_active_session(session.device_id, session.session_id)
            return format_mcp_error(
                req_id,
                "SESSION_EXPIRED: La session de contrôle a expiré. Une nouvelle session doit être démarrée sur le téléphone."
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
            return format_mcp_error(req_id, f"UNSUPPORTED_TOOL: Outil inconnu '{tool_name}'.")

        # Permissions: Refuser tout mode autre que l'exact mode 'interaction' pour les opérations interactives
        if op in INTERACTION_OPERATIONS and session.mode != "interaction":
            return format_mcp_error(
                req_id,
                f"MODE_DENIED: Le mode actuel de la session est '{session.mode}'. Seul le mode 'interaction' autorise les actions interactives ({op})."
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

        result = await manager.send_command_to_device(session.device_id, cmd)

        if result.status == "success":
            # Requirement 4: Corriger el.className en el.class_name
            if result.data and result.data.elements:
                elements_summary = []
                for el in result.data.elements:
                    attrs = []
                    if el.clickable: attrs.append("clickable")
                    if el.editable: attrs.append("editable")
                    if el.scrollable: attrs.append("scrollable")
                    attr_str = f" [{', '.join(attrs)}]" if attrs else ""
                    c_name = el.class_name or "View"
                    text_display = f"\"{el.text}\"" if el.text else (f"desc=\"{el.content_desc}\"" if el.content_desc else c_name)
                    elements_summary.append(f"- [{el.element_ref}] {c_name}: {text_display}{attr_str}")

                content = (
                    f"Observation de {result.data.package_name} (Révision: {result.data.screen_revision}):\n" +
                    "\n".join(elements_summary)
                )
                return format_mcp_response(req_id, content)
            else:
                return format_mcp_response(req_id, result.message or "Action exécutée avec succès.")
        else:
            return format_mcp_error(req_id, f"{result.error_code or 'ACTION_FAILED'}: {result.message or 'Erreur lors de l\'exécution sur le téléphone'}")

    else:
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {
                "code": -32601,
                "message": f"Method '{method}' not found"
            }
        }

def format_mcp_response(req_id: Any, text: str) -> Dict[str, Any]:
    return {
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

def format_mcp_error(req_id: Any, message: str) -> Dict[str, Any]:
    return {
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

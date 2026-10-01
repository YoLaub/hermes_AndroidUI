import asyncio
import json
import logging
import secrets
import time
import uuid
from typing import Dict, Optional, Any

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from database import (
    init_db,
    verify_device_token,
    register_device,
    save_pairing_code,
    consume_pairing_code,
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

app = FastAPI(title="Hermes Mobile Relay", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def on_startup():
    init_db()
    logger.info("Mobile Relay service initialized successfully.")

# ── Active State & Connection Management (In-Memory) ─────────────────────────

class ActiveSession:
    def __init__(self, session_id: str, device_id: str, target_package: str, allowed_profile: str, mode: str, expires_at: float):
        self.session_id = session_id
        self.device_id = device_id
        self.target_package = target_package
        self.allowed_profile = allowed_profile.lower()
        self.mode = mode
        self.expires_at = expires_at

    @property
    def is_expired(self) -> bool:
        return time.time() >= self.expires_at

class DeviceConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
        self.active_sessions: Dict[str, ActiveSession] = {} # device_id -> ActiveSession
        self.pending_command_futures: Dict[str, asyncio.Future] = {} # command_id -> Future[MobileCommandResult]

    def register_connection(self, device_id: str, websocket: WebSocket):
        self.active_connections[device_id] = websocket
        logger.info(f"Device connected: {device_id}")

    def unregister_connection(self, device_id: str):
        self.active_connections.pop(device_id, None)
        self.active_sessions.pop(device_id, None)
        logger.info(f"Device disconnected: {device_id}")

    def set_active_session(self, session: ActiveSession):
        self.active_sessions[session.device_id] = session
        logger.info(f"Active session started on device {session.device_id}: {session.session_id} ({session.target_package}, profile: {session.allowed_profile})")

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

        loop = asyncio.get_running_loop()
        future = loop.create_future()
        self.pending_command_futures[cmd.command_id] = future

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
            self.pending_command_futures.pop(cmd.command_id, None)

    def resolve_command_result(self, result: MobileCommandResult):
        fut = self.pending_command_futures.get(result.command_id)
        if fut and not fut.done():
            fut.set_result(result)

manager = DeviceConnectionManager()

# ── Health & Pairing Routes ──────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "service": "hermes-mobile-relay", "connected_devices": len(manager.active_connections)}

@app.post("/api/pair/generate", response_model=PairingGenerateResponse)
def generate_pairing_code(req: PairingGenerateRequest):
    code = secrets.token_hex(3).upper() # 6 characters
    save_pairing_code(code, req.user_id or "admin", expires_in_seconds=300)
    logger.info(f"Generated pairing code: {code}")
    return PairingGenerateResponse(code=code, expires_in_seconds=300)

@app.post("/api/pair/verify", response_model=PairingVerifyResponse)
def verify_pairing_code(req: PairingVerifyRequest):
    user_id = consume_pairing_code(req.code.strip().upper())
    if not user_id:
        raise HTTPException(status_code=400, detail="Code d'appairage invalide ou expiré.")

    device_token = "tok_" + secrets.token_urlsafe(24)
    register_device(req.device_id, device_token, req.device_name or "Android Phone")
    logger.info(f"Paired device {req.device_id} successfully.")
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
        # Initial message must be Auth or headers must authenticate
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
                session = ActiveSession(
                    session_id=data.get("session_id", str(uuid.uuid4())),
                    device_id=authenticated_device_id,
                    target_package=data.get("target_package", ""),
                    allowed_profile=data.get("allowed_profile", "mario"),
                    mode=data.get("mode", "interaction"),
                    expires_at=time.time() + data.get("duration_seconds", 900)
                )
                manager.set_active_session(session)
                log_audit(str(uuid.uuid4()), authenticated_device_id, session.allowed_profile, "SESSION_START", "STARTED", f"Package: {session.target_package}")
                await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "session_started_ack", "session_id": session.session_id}))

            elif msg_type == "session_end":
                if not authenticated_device_id:
                    continue
                sid = data.get("session_id")
                manager.end_active_session(authenticated_device_id, sid)
                log_audit(str(uuid.uuid4()), authenticated_device_id, "", "SESSION_END", "STOPPED", data.get("reason", "user_cancelled"))

            elif msg_type == "result":
                res = MobileCommandResult(**data)
                manager.resolve_command_result(res)

            elif msg_type == "ping":
                await websocket.send_text(json.dumps({"protocol": "mobile-control/1", "type": "pong"}))

    except WebSocketDisconnect:
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id)
    except Exception as e:
        logger.error(f"WebSocket exception for device {authenticated_device_id}: {e}")
        if authenticated_device_id:
            manager.unregister_connection(authenticated_device_id)

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

@app.post("/mcp")
@app.post("/mcp/v1/stream")
async def mcp_stream_endpoint(
    request: Request,
    x_hermes_profile: Optional[str] = Header(None)
):
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON-RPC request")

    method = body.get("method")
    req_id = body.get("id", 1)
    params = body.get("params", {})

    # Extract caller profile (from header or fallback to mario for pilot)
    profile = (x_hermes_profile or "mario").lower()

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

        # 1. mobile_control_status does not require an active session
        if tool_name == "mobile_control_status":
            session = manager.get_session_for_profile(profile)
            if session:
                remaining = int(session.expires_at - time.time())
                return format_mcp_response(req_id, f"Session active trouvée sur le téléphone.\nApplication : {session.target_package}\nMode : {session.mode}\nTemps restant : {remaining // 60}m {remaining % 60}s")
            else:
                return format_mcp_response(req_id, f"Aucune session de contrôle mobile n'est actuellement active pour le profil '{profile}'. L'utilisateur doit démarrer une session sur son application Hermes Android.")

        # 2. All other tools require an active validated session for this profile
        session = manager.get_session_for_profile(profile)
        if not session:
            return format_mcp_error(req_id, f"SESSION_REQUIRED: Aucune session active pour le profil '{profile}'. Demandez à l'utilisateur de lancer une session dans l'app Hermes.")

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
            # Format friendly MCP textual view of screen data
            if result.data and result.data.elements:
                elements_summary = []
                for el in result.data.elements:
                    attrs = []
                    if el.clickable: attrs.append("clickable")
                    if el.editable: attrs.append("editable")
                    if el.scrollable: attrs.append("scrollable")
                    attr_str = f" [{', '.join(attrs)}]" if attrs else ""
                    text_display = f"\"{el.text}\"" if el.text else (f"desc=\"{el.content_desc}\"" if el.content_desc else el.className)
                    elements_summary.append(f"- [{el.element_ref}] {el.className}: {text_display}{attr_str}")

                content = (
                    f"Observation de {result.data.package_name} (Révision: {result.data.screenRevision if hasattr(result.data, 'screenRevision') else result.data.screen_revision}):\n" +
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

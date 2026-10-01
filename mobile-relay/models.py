from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any

class MobileElementInfo(BaseModel):
    element_ref: str
    class_name: Optional[str] = None
    text: Optional[str] = None
    content_desc: Optional[str] = None
    clickable: bool = False
    editable: bool = False
    scrollable: bool = False
    bounds: Optional[str] = None

class MobileScreenData(BaseModel):
    screen_revision: str
    package_name: str
    title: Optional[str] = None
    elements: List[MobileElementInfo] = Field(default_factory=list)

class MobileCommandArguments(BaseModel):
    element_ref: Optional[str] = None
    text: Optional[str] = None
    direction: Optional[str] = None

class MobileCommand(BaseModel):
    protocol: str = "mobile-control/1"
    type: str = "command"
    command_id: str
    session_id: str
    device_id: Optional[str] = None
    operation: str
    target_package: str
    screen_revision: Optional[str] = None
    expires_at: Optional[int] = None
    arguments: Optional[MobileCommandArguments] = None

class MobileCommandResult(BaseModel):
    protocol: str = "mobile-control/1"
    type: str = "result"
    command_id: str
    status: str # "success", "rejected", "error"
    error_code: Optional[str] = None
    executed_at: Optional[int] = None
    message: Optional[str] = None
    data: Optional[MobileScreenData] = None

class MobileSessionStartMsg(BaseModel):
    protocol: str = "mobile-control/1"
    type: str = "session_start"
    session_id: str
    target_package: str
    allowed_profile: str
    mode: str
    duration_seconds: int

class MobileSessionEndMsg(BaseModel):
    protocol: str = "mobile-control/1"
    type: str = "session_end"
    session_id: str
    reason: str = "user_cancelled"

class MobileAuthMsg(BaseModel):
    protocol: str = "mobile-control/1"
    type: str = "auth"
    device_id: str
    device_token: str

class PairingGenerateRequest(BaseModel):
    user_id: Optional[str] = "admin"

class PairingGenerateResponse(BaseModel):
    code: str
    expires_in_seconds: int = 300

class PairingVerifyRequest(BaseModel):
    code: str
    device_id: str
    device_name: Optional[str] = "Android Phone"
    allow_overwrite: Optional[bool] = False

class PairingVerifyResponse(BaseModel):
    ok: bool
    device_token: Optional[str] = None
    error: Optional[str] = None

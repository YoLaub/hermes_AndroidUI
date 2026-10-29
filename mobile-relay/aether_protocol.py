"""AETHER-specific wire contract; Android mobile-control/1 is unchanged."""

from datetime import datetime, timezone
from typing import Literal
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Move(StrictModel):
    device_id: str = Field(min_length=1, max_length=128)
    screen_x: float = Field(ge=0, le=1, allow_inf_nan=False)
    screen_y: float = Field(ge=0, le=1, allow_inf_nan=False)
    posture: Literal["walk", "run", "sneak", "cautious"] = "walk"
    ttl_seconds: int = Field(default=20, ge=1, le=30)


class Gesture(StrictModel):
    device_id: str = Field(min_length=1, max_length=128)
    gesture: Literal["wave"]
    ttl_seconds: int = Field(default=15, ge=1, le=30)


class DesktopOpen(StrictModel):
    device_id: str = Field(min_length=1, max_length=128)
    action: Literal["app", "url", "youtube_search", "spotify_search"]
    target: str = Field(min_length=1, max_length=2048)
    ttl_seconds: int = Field(default=15, ge=1, le=30)


class Capabilities(StrictModel):
    capabilities: list[Literal["avatar.gesture", "desktop.open"]] = Field(max_length=2)


class Avatar(StrictModel):
    screen_x: float = Field(ge=0, le=1, allow_inf_nan=False)
    screen_y: float = Field(ge=0, le=1, allow_inf_nan=False)
    posture: Literal["walk", "run", "sneak", "cautious"]
    moving: bool


class Status(StrictModel):
    availability: Literal["ready", "busy", "unavailable"]
    avatar: Avatar


class Hello(StrictModel):
    device_id: str = Field(min_length=1, max_length=128)
    client_version: str = Field(min_length=1, max_length=64)
    capabilities: list[Literal["telemetry.status", "avatar.move_to"]] = Field(max_length=2)


class Result(StrictModel):
    command_id: str = Field(min_length=1, max_length=128)
    avatar: Avatar | None = None
    outcome: Literal["opened"] | None = None
    code: (
        Literal[
            "invalid_payload",
            "unsupported_capability",
            "avatar_unavailable",
            "command_expired",
            "command_rejected",
            "execution_failed",
            "session_lost",
            "unauthorized",
            "protocol_error",
        ]
        | None
    ) = None


class ClaimStart(StrictModel):
    device_id: str = Field(min_length=1, max_length=128)
    device_name: str = Field(min_length=1, max_length=64)


class ClaimPoll(StrictModel):
    poll_token: str = Field(min_length=1, max_length=128)


class Claim(StrictModel):
    code: str = Field(min_length=1, max_length=32)


class Envelope(StrictModel):
    version: Literal["aether.v1"]
    type: Literal[
        "client.hello",
        "client.capabilities",
        "telemetry.status",
        "command.accepted",
        "command.completed",
        "command.error",
        "heartbeat",
    ]
    message_id: str = Field(min_length=1, max_length=128)
    sent_at: str = Field(max_length=40)
    payload: dict


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def envelope(kind, payload):
    return {"version": "aether.v1", "type": kind, "message_id": uuid4().hex, "sent_at": utc_now(), "payload": payload}


TOOLS = [
    {
        "name": "aether_desktop_open",
        "description": "On explicit user request, open an approved macOS application or YouTube/Spotify content on an owned AETHER device. action app targets: safari, chrome, firefox, spotify, music, notes, calendar, calculator, maps. action url accepts HTTPS YouTube watch/video links or open.spotify.com track/album/artist/playlist links (no tracking query). youtube_search and spotify_search open search results, not a chosen result. Completion means OS accepted opening, never proves page load or playback. No shell, arbitrary paths, automatic retries or unsolicited launches.",
        "inputSchema": DesktopOpen.model_json_schema(),
    },
    {
        "name": "aether_avatar_gesture",
        "description": "Make an owned AETHER avatar wave (coucou). Requires negotiated gesture support. Waits for the final rendered pose; movement interrupts it. Never retries.",
        "inputSchema": Gesture.model_json_schema(),
    },
    {
        "name": "aether_get_status",
        "description": "List the connected AETHER avatars owned by your profile. No phone or screen data.",
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "aether_avatar_move_to",
        "description": "Move one connected AETHER avatar owned by your profile; waits for completion or an error. Never retries.",
        "inputSchema": Move.model_json_schema(),
    },
    {
        "name": "aether_claim_avatar",
        "description": "Pair a new AETHER avatar to your profile with the one-time code it displays. Only use a code the user gave you.",
        "inputSchema": Claim.model_json_schema(),
    },
]

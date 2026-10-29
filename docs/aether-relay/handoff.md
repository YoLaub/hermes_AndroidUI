---
type: Handoff
title: AETHER relay integration for the next developer
description: Implemented server-side AETHER pairing and MCP routing, per-profile avatars, wire contract and remaining client work.
timestamp: 2026-10-26
tags: [aether, relay, mcp, handoff]
---
# Latest handoff: explicit AETHER screen observation (2026-10-29)

The owner explicitly authorized this intervention on `dev`, with tests and this note. Ask again before a new intervention. No push, Docker build, deployment, Compose change or WebUI source change was performed; unrelated local files remain untouched.

## Contract and deployment acceptance

Existing `AETHER_HERMES_ORIGIN` and `AETHER_HERMES_PASSWORD` configuration is reused. No new user-facing token or configuration is required. Deploy this relay revision through the normal workflow before testing in production. AETHER's updated Brain requests `X-Aether-Features: screen-observation-v1`; only that header adds `screen_observation: <chat-configured>` to GET `/api/aether/chat/status`. Legacy clients still receive exactly `{available: ...}`. Capability indicates implementation/configuration, not upstream health or model vision support.

POST `/api/aether/chat/send` accepts one optional `observation: {mime_type: "image/jpeg", data_base64: "..."}`. Images are limited to 512 KiB and JPEG headers declaring at most 1280×720. Validation is structural, not a full JPEG decode. A path-specific ASGI middleware bounds all send bodies to 750,000 bytes before JSON parsing and limits body receive time to five seconds, including chunked requests. Errors do not echo submitted pixels. No client-supplied attachment paths, profile, session or URL are accepted.

The existing avatar credential/profile selects the session. The adapter logs into WebUI, creates the bound session if needed, rechecks authorization, uploads multipart `session_id` / `file` to `/api/upload`, validates the result, rechecks authorization and sends server-returned metadata in `/api/chat/start` attachments. UUID filenames prevent overwriting earlier screen attachments. Request IDs are persisted before any upload; retries return the existing state without uploading again. Interrupted upload/start stays uncertain. No automatic replay is introduced.

Consent detection and the one-use ticket are local AETHER responsibilities: explicit typed “regarde mon écran”, visible indicator, cancellation and provider shutdown before send. The relay authenticates the submitting avatar; it cannot independently attest physical user consent. Relay storage contains no image file or base64. WebUI uploads/session history may persist images, including an upload whose later chat start failed or was cancelled. Cancellation cannot guarantee deletion. The selected Hermes model must support vision for useful answers.

Validation: 309 relay tests pass; changed relay modules pass Ruff. An isolated composed test in AETHER `tests/test_observation_integration.py` passes through the actual native panel, authenticated Brain and real paired relay, with a local Hermes simulator and synthetic JPEG only: exactly one upload and one turn. Revocation before upload, malformed uploads, invalid/oversized images, chunked limits, non-reflecting errors, legacy status and duplicate IDs are covered. No production John message or desktop pixels were sent. After deployment, the owner should test “regarde mon écran”, denial/cancellation and ordinary chat with a vision-capable profile.

# Previous handoff: native AETHER conversation (2026-10-26)

The owner explicitly authorized this new intervention on `dev`, with a developer note. Ask again before any later intervention. No push, Docker build or deployment was performed. Existing unrelated deletions, Compose changes and untracked projects were preserved.

## Activation for the deployment agent

The relay gains a server-to-server adapter to the existing Hermes WebUI API. Configure **on the relay service only**:

* `AETHER_HERMES_ORIGIN`: the WebUI origin. Prefer the existing private Compose service `http://hermes-webui:8787` when reachable; public service origins must use HTTPS. No path, query or embedded credentials.
* `AETHER_HERMES_PASSWORD`: the existing WebUI password, injected through Coolify's secret mechanism. Do not paste it into AETHER, source files or chat. Reuse the existing secret rather than creating a user-facing setup step.

Do not edit or replace the owner's untracked Compose file blindly. Confirm relay-to-WebUI network reachability in the deployment environment; this session only checked local simulated upstream HTTP. Both settings must exist or conversation remains disabled. `/api/aether/chat/status` reports configuration availability, not an upstream login/agent health guarantee. The existing avatar pairing credential is reused; no new pairing or permanent token transfer to the UI is needed. Movement/gesture WebSocket negotiation is unchanged.

The adapter logs in through `/api/auth/login`, creates a dedicated `/api/session/new` session with the device's stored profile, starts `/api/chat/start`, and consumes `/api/chat/stream`. This uses the WebUI's existing non-browser service-client authentication path, without changing WebUI security or bypassing cookie authentication. No WebUI source was changed.

## Implemented HTTP contract

All routes require `Authorization: Bearer <paired-avatar-token>` and `X-Aether-Device: <device-id>`. Android credentials are rejected. Authentication/revocation checks and profile binding use existing database functions; clients cannot choose a profile, session or upstream URL.

* `GET /api/aether/chat/status` → `{available: boolean}`.
* `POST /api/aether/chat/send` with exactly `{request_id: canonical-UUID, message: text}` → `{request_id, state, text}`. Text is nonblank, at most 4,000 characters. Repeating an ID returns its stored outcome and never sends again, even if the supplied text differs.
* `GET /api/aether/chat/turn/{request_id}` → same snapshot, scoped to the device. AETHER polls; WebUI SSE remains internal to the relay.
* `POST /api/aether/chat/cancel/{request_id}` requests best-effort upstream cancellation; an interrupted outcome remains `uncertain`, not fake success.
* `POST /api/aether/chat/new` → `{reset: true}`. Explicitly starts a separate conversation after the active task stops; marks uncertain outcomes abandoned but retains request IDs. It does not undo actions or delete WebUI conversations.

States: `pending`, `running`, `completed`, `needs_action`, `uncertain`, `abandoned`. SQLite stores device/profile/session binding and request IDs. One active or uncertain turn blocks another until completion or explicit new discussion. Restart marks interrupted work uncertain; no replay. Up to 10,000 retained IDs per device; only the latest turn retains response text (64 KiB maximum). This is not a conversation-history API; Hermes retains session history. Native UI keeps a bounded in-memory recent transcript. Relay stays single-worker, as its existing connection/task routing already requires.

The adapter has a 180-second turn deadline, bounded JSON/SSE buffers, and excludes reasoning/tool payloads and raw provider errors. `approval` or `clarify` stops this client's turn, requests cancellation and tells the user to continue in WebUI; no approval is answered automatically. Cancellation/revocation cannot undo already executed tools. A network error after submission is intentionally uncertain and requires checking Hermes before beginning a separate discussion.

## AETHER implementation and acceptance

AETHER adds bearer-protected local `/relay/chat/*` routes. Only Brain holds the permanent relay credential. AppKit uses the launcher's owner-only local session-token file and contacts only the fixed loopback Brain origin. The separate native panel accepts keyboard input, follows the avatar within visible screen bounds, renders text without HTML and hides its launcher button while open. Closing the panel does not disconnect John or automatically cancel an agent turn. Packaged engine was rebuilt.

Validation: 299 relay tests pass; AETHER has 102 Python/native tests passing (one isolated Keychain opt-in skip), plus passing Rust tests. Tests cover per-device isolation, profile binding, persistent deduplication, one active turn, cancellation/restart uncertainty, response bounds and plain-text native input/focus. The composed local check exercised native panel → authenticated real Brain → actual paired relay → a synthetic Hermes HTTP/SSE server → native answer, with exactly one upstream turn. **No real John message was sent.** After deployment, restart AETHER, click “Parler à John” and send a harmless greeting; verify the response and continued avatar control. Upstream model availability, real tool approvals, server network access and production latency remain deployment acceptance items.

# Latest handoff: explicit avatar gesture (2026-10-26)

The owner explicitly authorized this intervention after being asked because the repository was previously read-only. Authorization covers this gesture feature; ask again before a later intervention in this repository. Keep the work on the existing `dev` branch. No push, image build or deployment was performed. Existing unrelated files/deletions were preserved.

The user approved the current AETHER wave and requires it to be an agent tool, never an automatic idle gesture. Added `aether_avatar_gesture` to the MCP tools with strict arguments `{device_id, gesture: "wave", ttl_seconds?: 15}`; TTL range is 1–30 seconds. Other gestures are rejected. Ownership, authorization/revocation, one active command per avatar, correlation, timeout and no replay use the same command path as movement. A gesture completion still requires the client to report a stationary avatar. The renderer is responsible for confirming the final rendered pose, not just elapsed time or command receipt.

## Backwards-compatible negotiation

The AETHER client reports `client_version: "0.2.0"` but preserves the old two-item hello capability list, so old relays still accept it. Only for that explicitly supported client version, this relay adds `optional_capabilities: ["avatar.gesture"]` to `server.ready`. The updated client then sends `client.capabilities` with `{capabilities: ["avatar.gesture"]}`. Until that message arrives, `can_gesture` is false and the tool returns `unsupported_capability`. Old clients receive the original ready payload; a new client connected to an old relay keeps movement working and does not negotiate gestures. When changing client versions, update this explicit negotiation gate or introduce a separately agreed extension-version scheme.

The gesture command is `command.avatar_gesture`, payload `{command_id, expires_at, gesture: "wave"}`. Result messages remain `command.accepted`, `command.completed` with stationary avatar, or `command.error` using existing codes. Acceptance is not completion. Movement interruption fails the greeting. Expiry/disconnection stop tracking and never cause retries; an already-started physical animation may finish locally after tracking ends.

## AETHER side and verification

AETHER now implements the full renderer/Objective-C/Rust/Python path. Its bounded native mailbox reports queued/playing/completed/failed; only the promise resolved after a final rendered neutral frame can succeed. A local eight-second guard fails stalled gestures. This is unrelated to Android control, chat or capture.

Tests: 288 relay tests pass, including authenticated MCP/WebSocket gesture routing, legacy-client compatibility, owner boundaries, capability gating and shared move/gesture exclusion. AETHER frontend, Rust and native tests also pass, including a real rendered wave and movement interruption. The test environments use temporary SQLite databases and synthetic credentials, never the production store. Deploy the changed relay through the owner's usual process and refresh/reconnect AETHER before asking John to use the tool. No new pairing or manual token configuration is required for existing credentials.

# Historical handoff (2026-10-24; superseded where noted above)

# Message for the development agent

The owner authorized extending the existing relay to connect their AETHER avatars to Hermes on the VPS. AETHER targets every platform; priority: finish and test macOS with Hermes before Windows. Each Hermes profile can own its own avatars (owner decision, 2026-10-24). The user pastes a text shown by AETHER to the agent of the chosen profile, with no terminal and no client-side configuration. No push, Docker build or deployment has been done. The owner explicitly asked to keep the commits on the current `dev` branch, despite the `main` mention in AGENTS.md. No production secret was read or changed.

The server-side relay is implemented and tested with a simulated WebSocket client. The AETHER connector (macOS first) and its pairing screen remain to be built in `/Users/yoann/Developer/project-aether`. Do not announce a working real Hermes–AETHER integration yet.

# Layout and compatibility

- `aether_protocol.py`: strict schemas and the definitions of the four MCP tools.
- `aether_relay.py`: connections, commands, timeouts and correlation, with no Android operation.
- `aether_routes.py`: the `/ws/aether` WebSocket, its authentication and validation, plus the `/api/aether/pair/start` and `/api/aether/pair/poll` pairing routes.
- `server.py`: short hooks into the existing MCP endpoint; profile authentication is reused.
- `database.py`: additive migrations — `device_kind` in `paired_devices` and `pairing_codes` (legacy value `android`), and `profile` in `paired_devices` (owner profile of an avatar, `NULL` for Android) — plus the `aether_claims` table. `init_db` can be re-run. Codes, tokens and identity replacements never cross the `android` / `aether` kinds.

The Android `mobile-control/1` protocol, its error codes and its sessions are unchanged. Android control stays restricted to `john`. The AETHER tools are listed for every authenticated profile, but each profile only sees and drives the avatars it owns. Another profile's avatar answers `avatar_unavailable`, exactly like a missing one; an avatar without an owner is reachable by nobody. What gets paired is an avatar, not a machine: one machine can host several avatars owned by different profiles. Every move names its `device_id` explicitly.

# Agent-driven pairing

AETHER ships with the relay address as its default (`https://mobile-relay-hermes.john-world.store`, editable in its settings). No admin token is involved.

1. AETHER calls `POST /api/aether/pair/start` with `{"device_id":"<new stable avatar id>","device_name":"<display name>"}` (unknown fields rejected). Response: `claim_code` (format `XXXX-XXXX`), `poll_token`, `expires_in_seconds:300` and `agent_prompt`, a ready-to-paste text naming the tool and the code.
2. AETHER shows `agent_prompt`. The user pastes it to the Hermes agent of the chosen profile, which calls the `aether_claim_avatar` MCP tool with `{"code":"..."}`. The relay binds the avatar to the caller's authenticated profile; the answer contains `device_id` and `device_name`, never a token.
3. AETHER polls `POST /api/aether/pair/poll` with `{"poll_token":"..."}` every few seconds: `{"status":"pending"}` until the code is claimed, then exactly once `{"status":"paired","device_token":"...","device_id":"...","profile":"..."}`. After that, 404, as for an unknown or expired request.
4. AETHER stores `device_token` in the system credential store (Keychain on macOS), never in a URL, Git, logs or LLM context. `poll_token` stays private to AETHER and never appears in `agent_prompt`.

Rules: single-use code, stored hashed, valid 300 s; 5 starts per source over 5 minutes and at most 10 pending requests; an already paired `device_id` gets 409 (AETHER must generate a new id to re-pair); restarting `start` for the same `device_id` replaces the previous request. `/api/pair/*` is Android-only again and rejects `device_kind:"aether"` (422). Behind Traefik without `FORWARDED_ALLOW_IPS`, every source looks the same, so the 5-start limit becomes global.

`agent_prompt` is in French on purpose: it is shown to the owner, who reads French.

# Implemented contract

This contract is the reference for this slice. It differs from the VPS audit proposal: `/ws/aether` endpoint, `aether_avatar_move_to` tool, multi-device status, simple terminal answers without `accepted_at`/`completed_at` fields. Do not implement the old examples blindly: unknown fields are rejected.

Outbound AETHER connection: `wss://<relay-origin>/ws/aether`, subprotocol `aether.v1`, header `Authorization: Bearer <device_token>`. The relay keeps its existing `/mcp` endpoint and profile authentication; no second MCP configuration is needed if Hermes already uses this relay, but it must refresh its tool discovery.

Every JSON frame contains exactly:

```json
{"version":"aether.v1","type":"client.hello","message_id":"unique-id","sent_at":"2026-10-23T12:00:00Z","payload":{"device_id":"avatar-id","client_version":"0.1.0","capabilities":["telemetry.status","avatar.move_to"]}}
```

`client.hello` must arrive within 5 seconds. `server.ready` provides `session_id`, `heartbeat_interval_seconds:20` and `max_command_ttl_seconds:30`. The client sends a `heartbeat` frame with `payload:{}` every 20 seconds; the relay answers it. After 40 seconds without an incoming message, the relay closes the connection. Application messages are limited to 16 KiB; this check happens after the transport has received the message, whose global limit stays Uvicorn's so Android screenshots still fit.

`telemetry.status` accepts only:

```json
{"availability":"ready","avatar":{"screen_x":0.4,"screen_y":0.7,"posture":"walk","moving":false}}
```

`availability`: `ready`, `busy` or `unavailable`. Normalized coordinates in `[0,1]`; postures `walk`, `run`, `sneak`, `cautious`. No screenshot, window name, clipboard, battery or other personal data. The cached status expires after 40 seconds without fresh telemetry (heartbeats do not refresh it).

Outbound command `command.avatar_move_to`, payload:

```json
{"command_id":"unique-id","expires_at":"2026-10-23T12:00:20Z","screen_x":0.4,"screen_y":0.7,"posture":"walk"}
```

Client answers:

- `command.accepted`: `{"command_id":"unique-id"}`; does not end the MCP call.
- `command.completed`: `{"command_id":"unique-id","avatar":{"screen_x":0.4,"screen_y":0.7,"posture":"walk","moving":false}}`. The relay checks the avatar stopped, the posture, and a 0.01 tolerance per axis around the target. This remains a claim by the client, not an independent measurement.
- `command.error`: `{"command_id":"unique-id","code":"execution_failed"}`. AETHER codes only: `invalid_payload`, `unsupported_capability`, `avatar_unavailable`, `command_expired`, `command_rejected`, `execution_failed`, `session_lost`, `unauthorized`, `protocol_error`.

# MCP tools and command lifecycle

- `aether_get_status`, arguments `{}`: `devices` list of the caller profile's connected avatars, with `device_id`, connection and last allowed status or `null`.
- `aether_claim_avatar`, argument `code`: binds a pending avatar to the caller profile (see pairing).
- `aether_avatar_move_to` (caller profile's avatars only), arguments `device_id`, `screen_x`, `screen_y`, optional `posture` (`walk`), optional `ttl_seconds` (20, range 1–30). One move in flight per device. The MCP call waits for a terminal result and returns JSON in its text block, in the relay's existing format.

A disconnect or connection replacement ends pending calls with `session_lost`. No move is queued or resent on reconnect. Answers from an old connection, another device or an expired command never validate the new command. Token authority is re-checked before every move and on every incoming message; rotating the token makes the old connection unable to receive new moves. This slice adds no revocation screen or API.

IMPORTANT: expiry and session loss mean the result is unknown, not that the physical move stopped. There is no cancel command here. The client must reject commands received after `expires_at`, never replay a command id, and never report "completed" on mere local acceptance. The stop-on-disconnect policy is still to be agreed with the AETHER connector. Never automatically retry a move whose result is unknown.

# Deployment and next steps

1. Review these changes and back up the database before the migration. Keep a single relay worker/process: connections and futures live in memory, as for Android. No multi-worker load balancing.
2. No new environment variable. Keep the existing TLS termination, WebSocket proxy and proxy-trust configuration (`FORWARDED_ALLOW_IPS`). No new port is required.
3. Deploy only on the owner's explicit instruction; refresh the MCP tools of the profiles involved.
4. In AETHER: `start`/`poll` pairing showing the text for the agent, store the token in the system credential store, open outbound WSS, implement validation/deduplication/expiry, reconnection and telemetry. AETHER's local API stays a private REST API; it has not become an MCP server.
5. Test the real Hermes → relay → native macOS avatar path with two distinct profiles, then network loss, token rotation and reconnection without replay. None of these tests have been run here.

Do not touch the pre-existing changes: deletion of `compose-hermes-openbao.yaml`, untracked `AGENTS.md`, `compose-hermes-openbao-mobile-relay.yaml`, `dashboard-web/`, `design&refonte/`, `feature/`.

# TDD validation

Baseline before changes: 258 relay tests passing. First slice: 273 passing (15 new). Multi-profile slice of 2026-10-24: 286 passing, OKF documentation compliant and `git diff --check` clean. The AETHER tests were written failing before the implementation (device kinds, migration, routing, revocation, per-profile ownership, agent-driven pairing and terminal result). Targeted tests: tokens refused in both directions, other profile's avatar unreachable, ownerless avatar unreachable, single-use and expiring claim codes, device token delivered once and never to the agent, private payload rejected, correlated result, acceptance distinct from completion, wrong final position, disconnect/replacement, expiry and legacy SQLite schema.

Command used (no Docker or Android build):

```sh
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=mobile-relay /Users/yoann/Developer/project-aether/aether-brain/.venv/bin/python -m pytest -q mobile-relay/tests
```

This environment satisfies the relay's dependencies; on another machine, use the repository's Python environment with `mobile-relay/requirements.txt`. Existing warning: Starlette/httpx TestClient deprecation.

## Combined local acceptance (2026-10-26)

An isolated actual Uvicorn relay, temporary pairing/SQLite state, real authenticated MCP call, AETHER WebSocket session and native engine were exercised together: `aether_avatar_gesture` returned completed only after the visible VRM wave finished. This used synthetic credentials and local processes, not John or the deployed VPS. The relay process used an environment containing its declared WebSocket dependency (`websockets`); the AETHER-only environment does not include a WebSocket server backend.

---
type: Handoff
title: AETHER relay integration for the next developer
description: Implemented server-side AETHER pairing and MCP routing, per-profile avatars, wire contract and remaining client work.
timestamp: 2026-10-24
tags: [aether, relay, mcp, handoff]
---
# Message for the development agent

The owner authorized extending the existing relay to connect their AETHER avatars to Hermes on the VPS. AETHER targets every platform; priority: finish and test macOS with Hermes before Windows. Each Hermes profile can own its own avatars (owner decision, 2026-10-24). The user pastes a text shown by AETHER to the agent of the chosen profile, with no terminal and no client-side configuration. No push, Docker build or deployment has been done. The owner explicitly asked to keep the commits on the current `dev` branch, despite the `main` mention in AGENTS.md. No production secret was read or changed.

The server-side relay is implemented and tested with a simulated WebSocket client. The AETHER connector (macOS first) and its pairing screen remain to be built in `/Users/yoann/Developer/project-aether`. Do not announce a working real Hermes–AETHER integration yet.

# Layout and compatibility

- `aether_protocol.py`: strict schemas and the definitions of the three MCP tools.
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

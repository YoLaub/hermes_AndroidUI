# Attaching an avatar to a WebUI profile: interfaces and endpoints

Scope: Hermes WebUI (`webui-backend/`) and its Android client (`android-app/`).
Everything in sections 1 to 4 was read from the code at commit `bdf8e80`. Section 5 is a
**proposal**: none of it exists yet.

## 1. Bottom line

- **The WebUI has no avatar feature.** No profile field, no upload route and no serving
  route is named or built for avatars. `grep -i avatar` finds nothing in `api/` or `server.py`.
- Today's "avatar" is **a letter**: the first letter of a name, in a coloured circle.
- A profile is described by a fixed set of fields (section 3.1) that has no image field.
- There is **no HTTP route that writes a file into a profile's directory**. Reading one back
  is possible through `/api/media` (section 3.5); writing one needs filesystem access.

## 2. Where an avatar is rendered today

| Client | File | What is drawn | Keyed on |
|---|---|---|---|
| WebUI, assistant message header | `static/ui.js:4820` (`_assistantRoleHtml`) | `<div class="role-icon assistant">` containing the first letter of the bot name | `window._botName` (global setting, **not per profile**) |
| WebUI, sidebar logo and tab title | `static/boot.js:1383` (`applyBotName`) | `.sidebar-header .logo` letter, `document.title` | `S.activeProfile` capitalised, else `window._botName` |
| WebUI, login page | `api/routes.py:2579` | `.logo` letter | `bot_name` setting |
| Android, assistant bubble | `features/chat/components/MessageBubble.kt:50` | fixed `SmartToy` icon in a 32 dp circle | nothing (same for every profile) |
| Android, user bubble | `MessageBubble.kt:176` | fixed circle | nothing |
| Android, profile picker | `features/profiles/ProfileDrawer.kt:138` | `profile.name.take(2).uppercase()` in a 36 dp circle | `ProfileInfo.name` |

The Android app has **no image-loading library** (no Coil or Glide in `app/build.gradle.kts`).

## 3. Existing endpoints that matter

All paths are relative to the WebUI base URL. When a password is set, `check_auth`
(`api/auth.py:424`, called from `server.py:250` and `:276`) requires the session cookie on every
route below except `/static/*`, which is public. `/api/media` also checks it itself.

### 3.1 Profile identity and listing

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/profiles` | none | `{"profiles":[{name, path, is_default, is_active, gateway_running, model, provider, has_env, skill_count}], "active": "<name>"}` |
| GET | `/api/profile/active` | none | `{"name", "path"}` |
| POST | `/api/profile/switch` | `{"name"}` | switch result + `Set-Cookie: hermes_profile=<name>` |
| POST | `/api/profile/create` | `{"name", "clone_from?", "clone_config?", "base_url?", "api_key?", "default_model?", "model_provider?"}` | `{"ok": true, "profile": {...}}` |
| POST | `/api/profile/delete` | `{"name"}` | delete result |
| GET / POST | `/api/profile/env` and `/api/profile/env/delete` | profile `.env` variables | not avatar related |

- Profile names match `^[a-z0-9][a-z0-9_-]{0,63}$` (`api/profiles.py:25`).
- `path` is the absolute server-side directory of the profile, normally
  `$HERMES_HOME/profiles/<name>`.
- Per-request profile selection: cookie `hermes_profile=<name>` (also the `X-Hermes-Profile`
  header). Sessions carry their own `profile` field.
- Android mirror of the payload: `ProfileInfo` in `core/model/Models.kt:37`. It has no avatar field.

### 3.2 Global settings

| Method | Path | Note |
|---|---|---|
| GET / POST | `/api/settings` | `bot_name` is a single global string (default `Hermes`, env `HERMES_WEBUI_BOT_NAME`). It is **not per profile**. |

### 3.3 Upload

| Method | Path | Behaviour |
|---|---|---|
| POST | `/api/upload` | multipart, fields `session_id` and `file`. Requires an existing session. Stores the file in that session's attachment directory and returns `{filename, path, size, mime, is_image}`. Size capped by `MAX_UPLOAD_BYTES`. |
| POST | `/api/upload/extract` | same, for an archive |

These uploads are **per session**, not per profile.

### 3.4 Workspace files

`GET /api/file`, `GET /api/file/raw` (`session_id` required), `POST /api/file/save|create|rename|delete`.
They are scoped to the session's workspace or its attachment directory, not to a profile directory.

### 3.5 Media by absolute path

`GET /api/media?path=<absolute path>[&inline=1]`

- Allowed roots: `$HERMES_HOME`, `~/.hermes`, `/tmp`, the last workspace, and any directory
  in `MEDIA_ALLOWED_ROOTS`.
- PNG, JPEG, GIF, WebP, ICO and BMP are served **inline**. SVG is always a download.
- Any other file under an allowed root is served as a download.
- Because a profile directory lives under `$HERMES_HOME`, an image stored in it is already
  readable through this route, using the `path` returned by `GET /api/profiles`.

> **Security note (from reading the code, not exercised).** The same rule makes other files
> in a profile directory, such as `.env` or `auth.json`, downloadable through `?path=` by
> anyone who passes authentication. If no password is set, that is anyone who can reach the
> server. Relying on `/api/media` for avatars keeps that exposure. Section 5 avoids it.

### 3.6 Static files

`GET /static/*` serves the repository's `static/` directory only, read-only, with
`Cache-Control: no-store`. A per-profile image cannot live there without redeploying.

## 4. What can be done today, without changing the backend

1. Copy an image to `<profile path>/avatar.png` on the server (manual, filesystem access).
2. Display it with `GET /api/media?path=<profile path>/avatar.png`.
3. Client code must build the URL itself: nothing tells a client that the file exists, so it
   has to try the URL and fall back to the letter on a 404.

Limits: no upload route, no listing field, no removal route, and the security note above.

## 5. Proposal (not implemented): a first-class profile avatar

### 5.1 Backend

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/profile/avatar?name=<profile>` | Serve the avatar, or 404. |
| POST | `/api/profile/avatar` | multipart `name` and `file`. Replace the avatar. |
| POST | `/api/profile/avatar/delete` | `{"name"}`. Remove it. |
| (change) | `/api/profiles` | add `avatar_url` (string or null) to each profile item. |

Rules:
- Validate `name` against `_PROFILE_ID_RE`, or accept the literal `default`. Do not reuse
  `_validate_profile_name` as is: it rejects `default`. Resolve the destination through
  `_resolve_named_profile_home`, so path traversal is impossible.
- Store as `<profile home>/avatar.<ext>`, one file per profile, replaced atomically.
- Accept PNG, JPEG and WebP only. Reject SVG. Check the magic bytes, not just the extension.
  Cap the size (for example 1 MB) and, if feasible, the pixel dimensions.
- Same authentication as the other routes. Serve with the image MIME type,
  `X-Content-Type-Options: nosniff` and an `ETag`.
- `avatar_url` should include a version (file mtime) so clients refresh after a change.
- This keeps `.env` and `auth.json` out of reach: the route serves exactly one known file name.

### 5.2 WebUI

- `_assistantRoleHtml` (`static/ui.js:4820`): when the active session's profile has an
  `avatar_url`, render an `<img class="role-icon assistant">`; otherwise keep the letter.
  Key it on the session's profile, not on `window._botName`.
- `applyBotName` (`static/boot.js:1383`): same for `.sidebar-header .logo`.
- A control in the profile panel (`static/panels.js`) calling the upload and delete routes.

### 5.3 Android

- `ProfileInfo`: add `@SerialName("avatar_url") val avatarUrl: String? = null`.
- Add an image loader (Coil is the usual choice); it must send the `hermes_profile` and
  session cookies, which `core/network/AuthInterceptor.kt` already handles for OkHttp.
- `ProfileDrawer.kt:138` and `MessageBubble.kt:50`: draw the image when `avatarUrl` is set,
  otherwise the current initials or icon.
- The user-side avatar in `MessageBubble.kt:176` is separate and out of scope here.

### 5.4 Tests to write first

- Upload accepts PNG, JPEG, WebP and rejects SVG, an oversized file, a wrong magic number, an
  invalid profile name and `..` in the name.
- `GET /api/profile/avatar` returns 404 without a file and the bytes with one.
- `/api/profiles` exposes `avatar_url` only for profiles that have a file, and the URL changes
  after a replacement.
- A profile cannot read or overwrite another profile's avatar through a crafted `name`.
- Android: `ProfileInfo` decodes with and without `avatar_url`.

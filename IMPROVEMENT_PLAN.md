# Improvement plan (audit of 2026-10-04)

Ledger of the 19 findings of the global audit, grouped into work packages (WP). Each WP is done
test-first and ticked here only when its **Done when** line is true. Nothing is pushed or merged
to `main` without an explicit request.

## 1. Method (applies to every WP)

1. **Branch and worktree.** One branch per WP from `dev`, in its own git worktree, so concurrent work
   on this repository (another agent is on the avatar feature) never shares a working tree:
   `git worktree add ../hermes-android-<wp> -b fix/<wp>-<slug> dev`.
2. **Red first.** Write the failing test, watch it fail for the right reason, then the minimal change.
3. **Real artefact.** A WP is not done on source review alone. Name the artefact exercised
   (local relay process, rebuilt APK, WebUI server on an isolated `HERMES_HOME`) and the observed result.
4. **Suites.** Relay: `cd mobile-relay && python3 -m pytest tests`. Android:
   `cd android-app && JAVA_HOME=<jdk 17 or 21> ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`.
   WebUI MCP: `webui-backend/tests` under `mcp 1.26` with the real agent and under `mcp 2.0`.
5. **Gates.** `code-review` on each branch diff; `security-review` on WP2, WP3, WP4; `simplify` on WP10.
6. **Commits.** One logical change per commit, English message, local. Merge into `dev` with `--no-ff`.
   `dev` to `main` and every push only on request.
7. **Docs.** Update `MOBILE_CONTROL_SPEC.md` / `README.md` in the same branch when a contract changes.

## 2. Work packages, in execution order

| WP | Findings | Area | Needs the phone | Needs a deploy |
|---|---|---|---|---|
| WP1 | 1 | Android lifecycle and foreground service | yes (rotation, screen off) | no |
| WP2 | 2 | Android secrets at rest and backup | yes (migration) | no |
| WP3 | 4, 18 | CI running the test suites, scoped lint | no | no |
| WP4 | 3 | WebUI `/api/media` sensitive files | no | yes (WebUI image) |
| WP5 | 11, 13, 14 | Relay hardening (CORS, auth rate limit, audit purge) | no | yes (relay image) |
| WP6 | 12 | Android cleartext policy | yes | no |
| WP7 | 5 | Session grace period on reconnect | yes | yes |
| WP8 | 6 | Phone-side check order | no | no |
| WP9 | 7, 8 | Observe limits, tolerant result parsing | yes | yes |
| WP10 | 9 | Accessibility service scope | yes | no |
| WP11 | 19 | MCP registry name collision | no | yes (WebUI image) |
| WP12 | 15 | Split oversized files | no | no |
| WP13 | 16, 17 | Release build, README and docs | no | no |

Finding 10 (`john` hard-coded in app, relay and spec) is **accepted as designed**; it is recorded in
the spec and not changed unless asked.

## 3. Work package details

### WP1: survive recreation, keep the control channel alive (finding 1)
- **Problem.** `Navigation.kt:30-45` builds every ViewModel and `MobileControlManager` with `remember {}`;
  no `viewModel()`, `onCleared`, `configChanges` or foreground service. Rotation recreates the activity:
  chat state and the session are lost, and the old manager and its WebSocket are never closed.
- **Tests first.** A process-scoped container returns the same manager across calls; ViewModels obtained
  through a `ViewModelStore` survive a simulated recreation; closing the container closes the WebSocket
  and cancels the manager scope (fake client).
- **Change.** `Application`-owned container, ViewModels via a factory, `MobileControlManager.close()`,
  and a foreground service (declared type, notification reuse) active only during a session.
- **Done when.** Unit tests green and, on the phone: rotate during a session keeps it; screen off for
  60 s keeps it. Verified on a rebuilt APK.
- **Outcome (2026-10-04).** Done, confirmed on the phone by the user. No `MobileControlManager.close()`
  was needed: the manager is process-scoped, so nothing leaks. Added after review: `ForegroundSync`
  (delayed stop, retry of a refused start) and a receiver independent of the UI.
- **Decision (made).** `specialUse`, running only while a session is pending or active.

### WP2: secrets at rest (finding 2)
- **Problem.** `HermesPreferences` stores password, session cookie, OpenBao token and device token in
  plain DataStore, with `allowBackup="true"` and template backup rules.
- **Tests first.** Round trip of a `SecretCipher` with an injectable key; migration reads old plaintext,
  writes encrypted, removes plaintext; a test parsing the manifest asserts `allowBackup="false"` and that
  the rules exclude the preferences file.
- **Change.** Keystore-backed AES-GCM for the four secrets, one-time migration, backup disabled.
- **Done when.** Tests green; on the phone an upgrade install keeps the pairing and the stored file no
  longer contains the token (checked with `adb shell run-as`, debug build).
- **Outcome (2026-10-04).** Done, verified on a Pixel 7a by `adb`: the installed build has no
  `ALLOW_BACKUP` flag, the DataStore holds 4 sealed values (`enc:v1:`) and no readable secret, and the
  app still authenticates to the relay after the in-place upgrade (`auth_ok`). Review fixes: startup
  crash on a DataStore error, keystore work off the main thread, pairing reports a storage failure,
  unknown sealed formats are never returned as plaintext. Not cleaned on purpose: undecryptable
  ciphertext (a transient keystore failure is indistinguishable from a lost key).

### WP3: tests in CI, scoped lint (findings 4, 18)
- **Change.** A `tests` workflow: relay pytest (Python 3.11), Android unit tests (JDK 17), WebUI MCP
  tests (venv with the pinned agent and `mcp`). A `ruff` config limited to `mobile-relay/` and the
  files written in this project, excluding vendored upstream code.
- **Done when.** Commands run clean from a fresh checkout and venv locally; the workflow is green once
  pushed (needs your push).

### WP4: `/api/media` and sensitive files (finding 3)
- **Coordinate first.** The avatar work touches the same route; agree on one design before editing.
- **Tests first.** `GET /api/media?path=<profile>/.env` and `auth.json` return 403; a profile image
  still returns 200 inline; path traversal and symlinks still refused.
- **Change.** Deny sensitive names and dotfiles under profile directories, or serve only image types there.
- **Done when.** Tests green and the isolated-server probe that returned 200 for `.env` now returns 403.

### WP5: relay hardening (findings 11, 13, 14)
- **Tests first.** No wildcard CORS with credentials; N failed `/mcp` token checks or WebSocket auth
  attempts from one IP are throttled (429 / close); `audit_logs` older than the retention are purged.
- **Done when.** Relay suite green and a local relay process shows throttling and purge in its logs.

### WP6: cleartext policy (finding 12)
- **Constraint.** Android's network security config cannot whitelist IP ranges, only host names or exact IPs.
- **Change.** HTTPS by default; cleartext only for `localhost` and `10.0.2.2`; an explicit in-app opt-in
  with a warning for any other `http://` server.
- **Decision.** Whether LAN `http://` servers must stay supported.

### WP7: session grace period (finding 5)
- **Problem.** The relay deletes a session the moment its socket closes; the phone then ends its own
  (built in the previous round). A brief network change kills the session.
- **Design.** Relay keeps the session for a grace window after a disconnect and resumes it on a
  re-authentication from the same device; the phone marks the session suspended instead of ended and
  resumes on `session_state active=true` for the same id, ending only if the relay says inactive.
- **Tests first.** Relay: resume within grace, expiry after grace, commands answered `DEVICE_OFFLINE`
  during grace. Phone: pure transitions suspended, resumed, ended.
- **Decision.** Grace length (proposal: 20 s).

### WP8: phone-side check order (finding 6)
- **Change.** Session checks run before accessibility and lock checks, through one pure ordered function.
- **Tests first.** No session and locked device gives `SESSION_NOT_ON_PHONE`, not `DEVICE_LOCKED`.

### WP9: observe limits and tolerant parsing (findings 7, 8)
- **Tests first.** A snapshot over the element or text cap is truncated with an explicit marker;
  the relay caps its text summary; a malformed `result` message gets an error reply and keeps the socket.
- **Decision.** Caps (proposal: 200 elements, 300 characters per text).

### WP10: accessibility scope (finding 9)
- **Change.** Drop or narrow `typeWindowContentChanged`, and restrict event delivery to allowed packages.
- **Done when.** On the phone, observe and click still work on the target app after the change.
  (Risk: revision tracking must not depend on the removed events; confirm by reading `AccessibilityNodeHelper`.)

### WP11: MCP registry name collision (finding 19)
- **Tests first.** Servers `a-b`/tool `c` and `a`/tool `b_c` in one profile no longer overwrite each other.
- **Change.** Detect the clash at registration and disambiguate deterministically.

### WP12: oversized files (finding 15)
- **Method.** Characterization tests on the pure logic first, then extract behind them with `simplify`.
  `ChatViewModel.kt` has no tests today: no move before they exist.

### WP13: release build and docs (findings 16, 17)
- **Change.** R8 with keep rules for kotlinx.serialization (checked with `assembleRelease`), signing from
  environment variables (nothing committed), `README.md` covering mobile control, Kanban, workspace and OpenBao.
- **Decisions.** Renaming `applicationId` (a new id means reinstall and re-pairing); docs language policy.

## 4. Decisions still open
WP1 service type; WP6 LAN `http://`; WP7 grace length; WP9 caps; WP13 `applicationId` and docs language.
They are asked when their WP starts, not now.

## 5. Ledger
- [x] WP1  - [x] WP2  - [ ] WP3  - [ ] WP4  - [ ] WP5  - [ ] WP6  - [ ] WP7
- [ ] WP8  - [ ] WP9  - [ ] WP10 - [ ] WP11 - [ ] WP12 - [ ] WP13

package com.example.hermes.core.mobilecontrol

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.hermes.core.accessibility.HermesAccessibilityService
import com.example.hermes.core.data.HermesPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class MobileControlManager(
    private val context: Context,
    private val preferences: HermesPreferences
) {
    companion object {
        private const val TAG = "MobileControlManager"
        private const val MAX_LOGS = 50
        private const val CONFIRMATION_TIMEOUT_MS = 10_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val commandMutex = Mutex()
    private var sessionTimerJob: Job? = null
    private var confirmationTimeoutJob: Job? = null

    val wsClient = MobileControlWebSocketClient()
    private val notificationHelper = MobileControlNotificationHelper(context)

    /** A start was requested but the relay has not confirmed it: NOT an active session. */
    private val _pendingSession = MutableStateFlow<MobileControlSession?>(null)
    val pendingSession: StateFlow<MobileControlSession?> = _pendingSession.asStateFlow()

    /** Active only after the relay confirmed it (session_started_ack / session_state). */
    private val _activeSession = MutableStateFlow<MobileControlSession?>(null)
    val activeSession: StateFlow<MobileControlSession?> = _activeSession.asStateFlow()

    data class SessionNotice(val text: String, val isError: Boolean)

    private val _notices = MutableSharedFlow<SessionNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<SessionNotice> = _notices.asSharedFlow()

    private val _allowedApps = MutableStateFlow<List<AllowedApp>>(
        listOf(
            AllowedApp("com.linkedin.android", "LinkedIn", isEnabled = true)
        )
    )
    val allowedApps: StateFlow<List<AllowedApp>> = _allowedApps.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val processedCommandIds = mutableSetOf<String>()

    init {
        // Wire incoming WebSocket commands
        wsClient.onCommandReceived = { cmd, sendResult ->
            scope.launch {
                val result = executeCommand(cmd)
                sendResult(result)
            }
        }

        wsClient.onSessionEndReceived = { sid ->
            if (_activeSession.value?.id == sid) {
                stopSession("server_requested")
            }
        }

        // The relay decides: nothing becomes active without its confirmation.
        wsClient.onSessionAck = { ack ->
            scope.launch { apply(SessionConfirmation.onAck(currentView(), ack, System.currentTimeMillis())) }
        }
        wsClient.onSessionError = { err ->
            scope.launch { apply(SessionConfirmation.onError(currentView(), err), refused = true) }
        }
        wsClient.onSessionState = { state ->
            scope.launch { apply(SessionConfirmation.onServerState(currentView(), state, System.currentTimeMillis())) }
        }

        // Keep the process alive and visible while a session is pending or active.
        scope.launch {
            ForegroundSync(
                views = combine(_pendingSession, _activeSession) { pending, active -> SessionView(pending, active) },
                start = { MobileControlService.start(context) },
                stop = { MobileControlService.stop(context) }
            ).run()
        }

        // The relay drops a device's session when its socket closes: do not keep showing it.
        scope.launch {
            wsClient.isAuthenticated.collect { authenticated ->
                if (!authenticated && _activeSession.value != null) {
                    Log.w(TAG, "event=auth_lost_with_active_session session_id=${_activeSession.value?.id}")
                    apply(
                        SessionConfirmation.onServerState(currentView(), MobileSessionState(active = false), System.currentTimeMillis()),
                        lostConnection = true
                    )
                }
            }
        }
    }

    private fun currentView() = SessionView(pending = _pendingSession.value, active = _activeSession.value)

    /** Applies a pure transition and runs the side effects of what actually changed. */
    private fun apply(t: SessionTransition, refused: Boolean = false, lostConnection: Boolean = false) {
        val before = currentView()
        _pendingSession.value = t.view.pending
        _activeSession.value = t.view.active

        if (before.pending != null && t.view.pending == null) {
            confirmationTimeoutJob?.cancel()
            confirmationTimeoutJob = null
            if (t.view.active == null) {
                logAudit("SESSION_START", before.pending.targetPackage,
                    if (refused) "REFUSED" else "NOT_CONFIRMED", t.notice, before.pending.allowedProfile)
            }
        }

        val newlyActive = t.view.active
        if (newlyActive != null && newlyActive.id != before.active?.id) {
            Log.i(TAG, "event=session_confirmed session_id=${newlyActive.id} profile=${newlyActive.allowedProfile}")
            processedCommandIds.clear()
            notificationHelper.showActiveSessionNotification(newlyActive)
            logAudit("SESSION_START", newlyActive.targetPackage, "CONFIRMED",
                "Confirmée par le relais (profil ${newlyActive.allowedProfile})", newlyActive.allowedProfile)
            startExpiryMonitor()
            if (before.pending != null) {
                _notices.tryEmit(SessionNotice("Session active : confirmée par le relais.", isError = false))
            }
        }

        val gone = t.endedLocally ?: if (before.active != null && newlyActive == null) before.active else null
        if (gone != null) {
            sessionTimerJob?.cancel()
            sessionTimerJob = null
            notificationHelper.cancelSessionNotification()
            logAudit("SESSION_END", gone.targetPackage,
                if (lostConnection) "CONNECTION_LOST" else "SYNC_ENDED", t.notice, gone.allowedProfile)
        }

        t.notice?.let {
            _notices.tryEmit(SessionNotice(it, isError = newlyActive == null || lostConnection))
        }
    }

    private fun startExpiryMonitor() {
        sessionTimerJob?.cancel()
        sessionTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _activeSession.value ?: break
                if (current.isExpired) {
                    Log.i(TAG, "Session expired monotonically: ${current.id}")
                    stopSession("session_timeout")
                    break
                }
            }
        }
    }

    // ── Session Management ──────────────────────────────────────────────────

    fun startSession(
        targetPackage: String,
        targetAppName: String,
        allowedProfile: String,
        mode: MobileControlMode,
        durationSeconds: Int
    ): Result<MobileControlSession> {
        val normalizedProfile = allowedProfile.trim().lowercase()
        if (normalizedProfile != "john") {
            return Result.failure(IllegalArgumentException("Seul le profil 'john' est autorisé pour le contrôle mobile (reçu: '$allowedProfile')."))
        }

        val app = _allowedApps.value.find { it.packageName == targetPackage && it.isEnabled }
        if (app == null) {
            return Result.failure(IllegalArgumentException("L'application $targetPackage n'est pas autorisée localement."))
        }

        if (!HermesAccessibilityService.isRunning()) {
            return Result.failure(IllegalStateException("Le service d'accessibilité Hermes n'est pas activé dans les paramètres Android."))
        }

        val now = System.currentTimeMillis()
        val duration = when (durationSeconds) {
            300, 900, 1800 -> durationSeconds
            else -> 900 // Default 15 minutes
        }
        val expiresAt = now + (duration * 1000L)

        val session = MobileControlSession(
            id = "ses_" + UUID.randomUUID().toString().take(12),
            targetPackage = targetPackage,
            targetAppName = targetAppName,
            allowedProfile = allowedProfile.lowercase(),
            mode = mode,
            startedAt = now,
            durationSeconds = duration,
            expiresAt = expiresAt
        )

        // Not active yet: the relay must confirm. Ask it, and never claim more than that.
        if (!wsClient.sendSessionStart(session)) {
            val reason = if (wsClient.isConnected.value) {
                "Le relais n'a pas authentifié cet appareil : appairez le téléphone avec le relais. Aucune session n'est active."
            } else {
                "Relais injoignable : aucune session n'a pu être demandée."
            }
            Log.w(TAG, "event=session_start_blocked session_id=${session.id} socket_open=${wsClient.isConnected.value}")
            logAudit("SESSION_START", targetPackage, "NOT_SENT", reason, allowedProfile)
            return Result.failure(IllegalStateException(reason))
        }

        _pendingSession.value = session
        logAudit(
            operation = "SESSION_START",
            targetPackage = targetPackage,
            status = "REQUESTED",
            details = "Mode: ${mode.name}, Durée: ${duration / 60} min, en attente du relais",
            profile = allowedProfile
        )

        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = scope.launch {
            delay(CONFIRMATION_TIMEOUT_MS)
            Log.w(TAG, "event=session_confirmation_timeout session_id=${session.id}")
            apply(SessionConfirmation.onTimeout(currentView(), session.id))
        }

        return Result.success(session)
    }

    fun stopSession(reason: String = "user_cancelled") {
        _pendingSession.value?.let { pending ->
            // Stopping before the relay answered: withdraw the request too.
            _pendingSession.value = null
            confirmationTimeoutJob?.cancel()
            confirmationTimeoutJob = null
            wsClient.sendSessionEnd(pending.id, reason)
        }
        val current = _activeSession.value
        if (current != null) {
            _activeSession.value = null
            sessionTimerJob?.cancel()
            sessionTimerJob = null

            notificationHelper.cancelSessionNotification()
            wsClient.sendSessionEnd(current.id, reason)

            logAudit(
                operation = "SESSION_END",
                targetPackage = current.targetPackage,
                status = "STOPPED",
                details = "Raison: $reason",
                profile = current.allowedProfile
            )
        }
    }

    // ── Command Execution & Validation Engine ───────────────────────────────

    suspend fun executeCommand(cmd: MobileCommand): MobileCommandResult = commandMutex.withLock {
        val now = System.currentTimeMillis()

        // 1. Deduplication check
        if (processedCommandIds.contains(cmd.commandId)) {
            return reject(cmd, "DUPLICATE_COMMAND", "Commande déjà exécutée.")
        }
        processedCommandIds.add(cmd.commandId)

        // 2. Command Expiration check
        if (cmd.expiresAt != null && now > cmd.expiresAt) {
            return reject(cmd, "COMMAND_EXPIRED", "Délai de validité de la commande dépassé.")
        }

        // 3. Accessibility Service active?
        val service = HermesAccessibilityService.getInstance()
        if (service == null || !HermesAccessibilityService.isRunning()) {
            return reject(cmd, "ACCESSIBILITY_DISABLED", "Le service d'accessibilité Hermes n'est pas activé sur le téléphone.")
        }

        // 4. Device Locked?
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isKeyguardLocked == true) {
            return reject(cmd, "DEVICE_LOCKED", "Le téléphone est actuellement verrouillé.")
        }

        // 5-7. Session, package and mode checks (pure, see CommandSessionGuard).
        val session = _activeSession.value
        Log.i(
            TAG,
            "event=command_received command_id=${cmd.commandId} operation=${cmd.operation} " +
                "cmd_session=${cmd.sessionId} local_session=${session?.id ?: "-"} " +
                "pending=${_pendingSession.value?.id ?: "-"} ws_authenticated=${wsClient.isAuthenticated.value}"
        )
        val rejection = CommandSessionGuard.validate(session, _pendingSession.value?.id, cmd, now)
        if (rejection != null) {
            Log.w(TAG, "event=command_rejected command_id=${cmd.commandId} code=${rejection.code}")
            if (rejection.code == "SESSION_EXPIRED") stopSession("session_timeout")
            return reject(cmd, rejection.code, rejection.message)
        }
        session!!

        // 8. Foreground check (except for launch_app)
        val fgPackage = service.getForegroundPackage()
        if (cmd.operation != "launch_app" && fgPackage.isNotBlank() && fgPackage != session.targetPackage) {
            // Check if fgPackage is a system dialog or keyboard
            if (!fgPackage.startsWith("com.android.") && !fgPackage.contains("inputmethod")) {
                return reject(cmd, "APP_NOT_FOREGROUND", "L'application cible (${session.targetPackage}) n'est pas au premier plan (Actuel: $fgPackage).")
            }
        }

        // 9. Dispatch Operation
        return try {
            when (cmd.operation) {
                "observe" -> {
                    val screenData = service.observeScreen()
                    logAudit("OBSERVE", session.targetPackage, "SUCCESS", "${screenData.elements.size} éléments", session.allowedProfile)
                    MobileCommandResult(
                        commandId = cmd.commandId,
                        status = MobileCommandStatus.SUCCESS,
                        executedAt = System.currentTimeMillis(),
                        message = "Observation réussie (${screenData.elements.size} éléments).",
                        data = screenData
                    )
                }

                "launch_app" -> {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(session.targetPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        delay(600) // Brief delay for window to attach
                        val screenData = service.observeScreen()
                        logAudit("LAUNCH_APP", session.targetPackage, "SUCCESS", null, session.allowedProfile)
                        MobileCommandResult(
                            commandId = cmd.commandId,
                            status = MobileCommandStatus.SUCCESS,
                            executedAt = System.currentTimeMillis(),
                            message = "Application ${session.targetAppName} lancée.",
                            data = screenData
                        )
                    } else {
                        reject(cmd, "ACTION_FAILED", "Impossible de lancer le package ${session.targetPackage}.")
                    }
                }

                "click_element" -> {
                    val ref = cmd.arguments?.elementRef
                    if (ref.isNullOrBlank()) {
                        return reject(cmd, "UNSUPPORTED_UI", "element_ref manquant.")
                    }
                    if (!service.isScreenRevisionValid(cmd.screenRevision)) {
                        return reject(cmd, "STALE_SCREEN", "Interface modifiée : nouvelle observation requise.")
                    }
                    val clicked = service.clickElement(ref, cmd.screenRevision)
                    if (clicked) {
                        delay(300) // Brief delay for UI to settle
                        val nextScreen = service.observeScreen()
                        logAudit("CLICK", session.targetPackage, "SUCCESS", "Element: $ref", session.allowedProfile)
                        MobileCommandResult(
                            commandId = cmd.commandId,
                            status = MobileCommandStatus.SUCCESS,
                            executedAt = System.currentTimeMillis(),
                            message = "Clic effectué sur $ref.",
                            data = nextScreen
                        )
                    } else {
                        reject(cmd, "ACTION_FAILED", "Échec du clic sur l'élément $ref.")
                    }
                }

                "set_text" -> {
                    val ref = cmd.arguments?.elementRef
                    val textToSet = cmd.arguments?.text
                    if (ref.isNullOrBlank() || textToSet == null) {
                        return reject(cmd, "UNSUPPORTED_UI", "element_ref ou texte manquant.")
                    }
                    if (!service.isScreenRevisionValid(cmd.screenRevision)) {
                        return reject(cmd, "STALE_SCREEN", "Interface modifiée : nouvelle observation requise.")
                    }
                    val textSet = service.setText(ref, textToSet, cmd.screenRevision)
                    if (textSet) {
                        delay(200)
                        val nextScreen = service.observeScreen()
                        logAudit("SET_TEXT", session.targetPackage, "SUCCESS", "Element: $ref (longueur: ${textToSet.length})", session.allowedProfile)
                        MobileCommandResult(
                            commandId = cmd.commandId,
                            status = MobileCommandStatus.SUCCESS,
                            executedAt = System.currentTimeMillis(),
                            message = "Texte saisi avec succès.",
                            data = nextScreen
                        )
                    } else {
                        reject(cmd, "ACTION_FAILED", "Échec de la saisie de texte sur $ref.")
                    }
                }

                "scroll" -> {
                    val dir = cmd.arguments?.direction ?: "down"
                    val scrolled = service.scroll(dir)
                    if (scrolled) {
                        delay(400)
                        val nextScreen = service.observeScreen()
                        logAudit("SCROLL", session.targetPackage, "SUCCESS", "Direction: $dir", session.allowedProfile)
                        MobileCommandResult(
                            commandId = cmd.commandId,
                            status = MobileCommandStatus.SUCCESS,
                            executedAt = System.currentTimeMillis(),
                            message = "Défilement $dir effectué.",
                            data = nextScreen
                        )
                    } else {
                        reject(cmd, "ACTION_FAILED", "Échec du défilement $dir.")
                    }
                }

                "back" -> {
                    val backDone = service.performBack()
                    delay(300)
                    val nextScreen = service.observeScreen()
                    logAudit("BACK", session.targetPackage, "SUCCESS", null, session.allowedProfile)
                    MobileCommandResult(
                        commandId = cmd.commandId,
                        status = MobileCommandStatus.SUCCESS,
                        executedAt = System.currentTimeMillis(),
                        message = "Retour arrière effectué.",
                        data = nextScreen
                    )
                }

                "end_session" -> {
                    stopSession("agent_requested")
                    MobileCommandResult(
                        commandId = cmd.commandId,
                        status = MobileCommandStatus.SUCCESS,
                        executedAt = System.currentTimeMillis(),
                        message = "Session terminée à la demande de l'agent."
                    )
                }

                else -> {
                    reject(cmd, "UNSUPPORTED_UI", "Opération inconnue : ${cmd.operation}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error executing command ${cmd.operation}", e)
            reject(cmd, "ACTION_FAILED", "Erreur inattendue : ${e.localizedMessage}")
        }
    }

    private fun reject(cmd: MobileCommand, errorCode: String, message: String): MobileCommandResult {
        logAudit(
            operation = cmd.operation,
            targetPackage = cmd.targetPackage,
            status = "REJECTED ($errorCode)",
            details = message,
            profile = _activeSession.value?.allowedProfile
        )
        return MobileCommandResult(
            commandId = cmd.commandId,
            status = MobileCommandStatus.REJECTED,
            errorCode = errorCode,
            executedAt = System.currentTimeMillis(),
            message = message
        )
    }

    private fun logAudit(
        operation: String,
        targetPackage: String,
        status: String,
        details: String?,
        profile: String?
    ) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            operation = operation,
            targetPackage = targetPackage,
            status = status,
            details = details,
            profile = profile
        )
        val updated = listOf(entry) + _auditLogs.value.take(MAX_LOGS - 1)
        _auditLogs.value = updated
    }

    fun addAllowedApp(packageName: String, appName: String) {
        val current = _allowedApps.value.toMutableList()
        if (current.none { it.packageName == packageName }) {
            current.add(AllowedApp(packageName, appName, isEnabled = true))
            _allowedApps.value = current
        }
    }

    fun toggleAllowedApp(packageName: String) {
        _allowedApps.value = _allowedApps.value.map {
            if (it.packageName == packageName) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun clearAuditLogs() {
        _auditLogs.value = emptyList()
    }
}

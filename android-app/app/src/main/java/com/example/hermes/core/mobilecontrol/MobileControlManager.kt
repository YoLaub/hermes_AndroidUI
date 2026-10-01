package com.example.hermes.core.mobilecontrol

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.hermes.core.accessibility.HermesAccessibilityService
import com.example.hermes.core.data.HermesPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val commandMutex = Mutex()
    private var sessionTimerJob: Job? = null

    val wsClient = MobileControlWebSocketClient()
    private val notificationHelper = MobileControlNotificationHelper(context)

    private val _activeSession = MutableStateFlow<MobileControlSession?>(null)
    val activeSession: StateFlow<MobileControlSession?> = _activeSession.asStateFlow()

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
        // Wire notification stop receiver callback
        MobileControlStopReceiver.onStopRequested = {
            stopSession("user_stopped_via_notification")
        }

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
    }

    // ── Session Management ──────────────────────────────────────────────────

    fun startSession(
        targetPackage: String,
        targetAppName: String,
        allowedProfile: String,
        mode: MobileControlMode,
        durationSeconds: Int
    ): Result<MobileControlSession> {
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

        _activeSession.value = session
        processedCommandIds.clear()

        // Show ongoing notification with quick kill-switch
        notificationHelper.showActiveSessionNotification(session)

        // Notify Relay via WebSocket
        wsClient.sendSessionStart(session)

        logAudit(
            operation = "SESSION_START",
            targetPackage = targetPackage,
            status = "STARTED",
            details = "Mode: ${mode.name}, Durée: ${duration / 60} min",
            profile = allowedProfile
        )

        // Start monotonic expiration monitor
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

        return Result.success(session)
    }

    fun stopSession(reason: String = "user_cancelled") {
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

        // 5. Active Session validation
        val session = _activeSession.value
        if (session == null) {
            return reject(cmd, "SESSION_REQUIRED", "Aucune session de contrôle mobile n'est active sur le téléphone.")
        }

        if (session.id != cmd.sessionId) {
            return reject(cmd, "SESSION_REQUIRED", "Identifiant de session invalide ou expiré.")
        }

        if (session.isExpired) {
            stopSession("session_timeout")
            return reject(cmd, "SESSION_EXPIRED", "La session a expiré.")
        }

        // 6. Target Package allowed?
        if (cmd.targetPackage != session.targetPackage) {
            return reject(cmd, "APP_NOT_ALLOWED", "Le package demandé (${cmd.targetPackage}) ne correspond pas à la session (${session.targetPackage}).")
        }

        // 7. Mode check (observation vs interaction)
        if (session.mode == MobileControlMode.OBSERVATION) {
            if (cmd.operation != "observe" && cmd.operation != "end_session") {
                return reject(cmd, "MODE_DENIED", "La session est en mode observation seule. Clics et saisies refusés.")
            }
        }

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

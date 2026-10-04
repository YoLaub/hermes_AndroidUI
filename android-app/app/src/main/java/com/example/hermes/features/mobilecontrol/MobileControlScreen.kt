package com.example.hermes.features.mobilecontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.mobilecontrol.AllowedApp
import com.example.hermes.core.mobilecontrol.AuditLogEntry
import com.example.hermes.core.mobilecontrol.MobileControlMode
import com.example.hermes.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileControlScreen(
    viewModel: MobileControlViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showStartSessionDialog by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showAddAppDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.successMessage) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = HermesPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Contrôle par Hermes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HermesTextPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = HermesTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnyxDarkSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = OnyxDarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Active Session Emergency Banner ─────────────────────────────
            item {
                if (state.activeSession != null) {
                    val session = state.activeSession!!
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurfaceVariant),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(HermesError)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(HermesError)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "SESSION ACTIVE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = HermesError
                                    )
                                }
                                Text(
                                    "Temps restant : ${session.remainingSeconds / 60}m ${session.remainingSeconds % 60}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = HermesTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Application : ${session.targetAppName} (${session.targetPackage})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = HermesTextPrimary
                            )
                            Text(
                                text = "Profil autorisé : John (${session.allowedProfile}) • Mode : ${session.mode.name.lowercase()}" +
                                    if (session.allowScreenshots) " • Captures autorisées" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesTextSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.stopSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = HermesError),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.StopCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ARRÊTER LE CONTRÔLE", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── System Status & Permissions Card ────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "État du système",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = HermesTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Accessibility Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Service d'accessibilité Hermes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HermesTextPrimary
                                )
                                Text(
                                    if (state.isAccessibilityEnabled) "Actif et prêt" else "Inactif (requis pour le contrôle)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isAccessibilityEnabled) HermesSuccess else HermesError
                                )
                            }
                            if (!state.isAccessibilityEnabled) {
                                OutlinedButton(
                                    onClick = { viewModel.openAccessibilitySettings() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Activer", fontSize = 12.sp)
                                }
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HermesSuccess)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = OnyxBorder)

                        // Relay Connection Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Relais VPS (WebSocket)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HermesTextPrimary
                                )
                                Text(
                                    if (state.isRelayConnected) "Connecté (${state.relayUrl})" else "Déconnecté (${state.relayUrl})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isRelayConnected) HermesSuccess else HermesTextMuted
                                )
                            }
                            IconButton(onClick = { showPairingDialog = true }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Réglages appairage",
                                    tint = HermesPrimary
                                )
                            }
                        }
                    }
                }
            }

            // ── Session Trigger Card (When no active session) ────────────────
            item {
                if (state.activeSession == null) {
                    val isJohnAvailable = state.availableProfiles.contains("john")
                    val isStartEnabled = state.isAccessibilityEnabled && isJohnAvailable

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showStartSessionDialog = true },
                            enabled = isStartEnabled,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Démarrer une session de contrôle",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        if (!isJohnAvailable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Profil John ('john') indisponible : démarrage du contrôle mobile désactivé (aucun fallback autorisé).",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesError
                            )
                        }
                    }
                }
            }

            // ── Allowed Applications Section ────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Applications autorisées",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = HermesTextPrimary
                    )
                    TextButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter", fontSize = 12.sp)
                    }
                }
            }

            items(state.allowedApps) { app ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = HermesTextPrimary
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = HermesTextSecondary
                            )
                        }
                        Switch(
                            checked = app.isEnabled,
                            onCheckedChange = { viewModel.toggleApp(app.packageName) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HermesPrimary,
                                checkedTrackColor = HermesPrimary.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // ── Activity & Audit Log Section ────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Journal d'activité et audits",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = HermesTextPrimary
                    )
                    if (state.auditLogs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAuditLogs() }) {
                            Text("Effacer", fontSize = 12.sp, color = HermesTextMuted)
                        }
                    }
                }
            }

            if (state.auditLogs.isEmpty()) {
                item {
                    Text(
                        "Aucune action enregistrée pour le moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.auditLogs) { log ->
                    AuditLogRow(log = log)
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    if (showStartSessionDialog) {
        StartSessionDialog(
            allowedApps = state.allowedApps.filter { it.isEnabled },
            availableProfiles = state.availableProfiles,
            activeProfile = state.activeProfile,
            onDismiss = { showStartSessionDialog = false },
            onConfirm = { app, profile, mode, duration, allowScreenshots ->
                showStartSessionDialog = false
                viewModel.startSession(
                    targetPackage = app.packageName,
                    targetAppName = app.appName,
                    profile = profile,
                    mode = mode,
                    durationMinutes = duration,
                    allowScreenshots = allowScreenshots
                )
            }
        )
    }

    if (showPairingDialog) {
        PairingDialog(
            currentUrl = state.relayUrl,
            deviceId = state.deviceId,
            isPaired = state.isPaired,
            onDismiss = { showPairingDialog = false },
            onUpdateUrl = { viewModel.updateRelayUrl(it) },
            onPair = { code -> viewModel.pairDevice(code) },
            onUnpair = { viewModel.unpairDevice() }
        )
    }

    if (showAddAppDialog) {
        AddAppDialog(
            onDismiss = { showAddAppDialog = false },
            onAdd = { pkg, name ->
                showAddAppDialog = false
                viewModel.addAllowedApp(pkg, name)
            }
        )
    }
}

@Composable
private fun AuditLogRow(log: AuditLogEntry) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
    val isSuccess = log.status == "SUCCESS" || log.status == "STARTED"

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.operation,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = HermesTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSuccess) HermesSuccess else HermesError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                log.details?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = HermesTextMuted
            )
        }
    }
}

@Composable
private fun StartSessionDialog(
    allowedApps: List<AllowedApp>,
    availableProfiles: List<String>,
    activeProfile: String,
    onDismiss: () -> Unit,
    onConfirm: (AllowedApp, String, MobileControlMode, Int, Boolean) -> Unit
) {
    var selectedApp by remember { mutableStateOf(allowedApps.firstOrNull()) }
    // Always off when the dialog opens: the consent is given again, on purpose, for each session.
    var allowScreenshots by remember { mutableStateOf(false) }
    val isJohnAvailable = availableProfiles.contains("john")
    var selectedMode by remember { mutableStateOf(MobileControlMode.INTERACTION) }
    var selectedDuration by remember { mutableStateOf(15) } // minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Démarrer le contrôle mobile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Application cible :", style = MaterialTheme.typography.labelMedium)
                allowedApps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedApp == app) HermesPrimary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedApp = app }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedApp == app, onClick = { selectedApp = app })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text("Profil Hermes autorisé :", style = MaterialTheme.typography.labelMedium)
                if (isJohnAvailable) {
                    FilterChip(
                        selected = true,
                        onClick = { /* John est le seul profil autorisé pour le contrôle mobile */ },
                        label = { Text("John (john)") },
                        leadingIcon = {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                } else {
                    Text(
                        "Le profil John ('john') est introuvable. Démarrage impossible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesError
                    )
                }

                Text("Mode de contrôle :", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == MobileControlMode.INTERACTION,
                        onClick = { selectedMode = MobileControlMode.INTERACTION },
                        label = { Text("Interaction") }
                    )
                    FilterChip(
                        selected = selectedMode == MobileControlMode.OBSERVATION,
                        onClick = { selectedMode = MobileControlMode.OBSERVATION },
                        label = { Text("Observation seule") }
                    )
                }

                Text("Durée maximale :", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { dur ->
                        FilterChip(
                            selected = selectedDuration == dur,
                            onClick = { selectedDuration = dur },
                            label = { Text("$dur min") }
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autoriser les captures d'écran", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Pendant cette session seulement, l'agent peut demander une image de la fenêtre de " +
                                "l'application cible (Android 14 ou plus récent). Elle est envoyée au modèle de John " +
                                "(le fournisseur que ce profil utilise) et peut montrer messages, noms ou photos. " +
                                "Les champs de mot de passe cachés sont masqués ; un mot de passe affiché en clair ne " +
                                "peut pas l'être. Les fenêtres protégées ne sont jamais capturées.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HermesTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = allowScreenshots, onCheckedChange = { allowScreenshots = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val app = selectedApp
                    if (app != null && isJohnAvailable) {
                        onConfirm(app, "john", selectedMode, selectedDuration, allowScreenshots)
                    }
                },
                enabled = selectedApp != null && isJohnAvailable
            ) {
                Text("Démarrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun PairingDialog(
    currentUrl: String,
    deviceId: String,
    isPaired: Boolean,
    onDismiss: () -> Unit,
    onUpdateUrl: (String) -> Unit,
    onPair: (String) -> Unit,
    onUnpair: () -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var codeText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuration du Relais") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("URL du Relais VPS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Identifiant de l'appareil : $deviceId",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = HermesTextSecondary
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (isPaired) {
                    Text("Cet appareil est appairé avec le serveur.", color = HermesSuccess)
                    Button(
                        onClick = {
                            onUnpair()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesError)
                    ) {
                        Text("Dissocier l'appareil")
                    }
                } else {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        label = { Text("Code d'appairage à usage unique") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdateUrl(urlText)
                if (codeText.isNotBlank()) {
                    onPair(codeText)
                }
                onDismiss()
            }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}

@Composable
private fun AddAppDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var pkgText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autoriser une application") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Nom d'affichage (ex: LinkedIn)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkgText,
                    onValueChange = { pkgText = it },
                    label = { Text("Nom du package (ex: com.linkedin.android)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(pkgText.trim(), nameText.trim()) },
                enabled = pkgText.isNotBlank() && nameText.isNotBlank()
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

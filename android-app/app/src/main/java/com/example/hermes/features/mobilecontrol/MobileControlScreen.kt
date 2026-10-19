package com.example.hermes.features.mobilecontrol

import com.example.hermes.R
import androidx.compose.ui.res.stringResource
import com.example.hermes.ui.components.StopControlDialog
import com.example.hermes.ui.components.LabeledSwitch
import com.example.hermes.core.mobilecontrol.ConsentLevel
import com.example.hermes.core.mobilecontrol.consentLevel

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.hermes.core.mobilecontrol.Consent
import com.example.hermes.core.mobilecontrol.MobileControlMode
import com.example.hermes.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileControlScreen(
    viewModel: MobileControlViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenRelaySettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var showStartSessionDialog by remember { mutableStateOf(false) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }

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
                            stringResource(R.string.control_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HermesTextPrimary
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = HermesTextPrimary
                            )
                        }
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
                                        stringResource(R.string.control_session_active),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = HermesError
                                    )
                                }
                                Text(
                                    stringResource(R.string.control_temps_restant_m_s, session.remainingSeconds / 60, session.remainingSeconds % 60),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = HermesTextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.control_application, session.targetAppName, session.targetPackage),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = HermesTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.control_profil_autorise_john_mode, session.allowedProfile, session.mode.name.lowercase()) +
                                    (if (session.allowScreenshots) stringResource(R.string.control_captures_autorisees) else "") +
                                    (if (session.allowCalendar) stringResource(R.string.control_calendrier_autorise) else "") +
                                    (if (session.allows(Consent.SMS_READ)) stringResource(R.string.control_sms_autorises) else "") +
                                    (if (session.allows(Consent.CALL_LOG_READ)) stringResource(R.string.control_journal_d_appels_autorise) else "") +
                                    (if (session.allows(Consent.SMS_SEND)) stringResource(R.string.control_envoi_de_sms_autorise) else "") +
                                    (if (session.allows(Consent.CALL_PLACE)) stringResource(R.string.control_appels_autorises) else "") +
                                    (if (session.allows(Consent.CALENDAR_WRITE)) stringResource(R.string.control_ecriture_du_calendrier_autorisee) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesTextSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showStopDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = HermesError),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.StopCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.control_stop), fontWeight = FontWeight.Bold, color = Color.White)
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
                            stringResource(R.string.control_etat_du_systeme),
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
                                    stringResource(R.string.control_a11y),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HermesTextPrimary
                                )
                                Text(
                                    stringResource(if (state.isAccessibilityEnabled) R.string.control_a11y_on else R.string.control_a11y_off),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isAccessibilityEnabled) HermesSuccess else HermesError
                                )
                            }
                            if (!state.isAccessibilityEnabled) {
                                OutlinedButton(
                                    onClick = { viewModel.openAccessibilitySettings() },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.yolo_confirm), fontSize = 12.sp)
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
                                    stringResource(R.string.relay_short),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HermesTextPrimary
                                )
                                Text(
                                    if (state.isRelayConnected) stringResource(R.string.control_connecte, state.relayUrl) else stringResource(R.string.control_deconnecte, state.relayUrl),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isRelayConnected) HermesSuccess else HermesTextMuted
                                )
                                // The socket's own message, on demand ("détail technique").
                                if (!state.isRelayConnected && state.relayErrorDetail != null) {
                                    var showDetail by remember { mutableStateOf(false) }
                                    TextButton(onClick = { showDetail = !showDetail }) {
                                        Text(stringResource(if (showDetail) R.string.control_hide_detail else R.string.control_show_detail))
                                    }
                                    if (showDetail) {
                                        Text(state.relayErrorDetail!!, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = HermesTextSecondary)
                                    }
                                }
                            }
                            // Address and pairing live in Réglages › Relais mobile.
                            IconButton(onClick = onOpenRelaySettings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.control_relais_mobile_et_appairage),
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
                                stringResource(R.string.control_demarrer_une_session_de_controle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        if (!isJohnAvailable) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.control_profil_john_john_indisponible_demarrage),
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
                        stringResource(R.string.control_applications_autorisees),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = HermesTextPrimary
                    )
                    TextButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.workspaces_add_confirm), fontSize = 12.sp)
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
                        LabeledSwitch(
                            checked = app.isEnabled,
                            onCheckedChange = { viewModel.toggleApp(app.packageName) },
                            label = app.appName
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
                        stringResource(R.string.control_journal),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = HermesTextPrimary
                    )
                    if (state.auditLogs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAuditLogs() }) {
                            Text(stringResource(R.string.auth_clear), fontSize = 12.sp, color = HermesTextMuted)
                        }
                    }
                }
            }

            if (state.auditLogs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.control_aucune_action_enregistree_pour_le),
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

    if (showStopDialog) {
        StopControlDialog(
            onConfirm = {
                showStopDialog = false
                viewModel.stopSession()
            },
            onDismiss = { showStopDialog = false }
        )
    }

    if (showStartSessionDialog) {
        StartSessionDialog(
            allowedApps = state.allowedApps.filter { it.isEnabled },
            availableProfiles = state.availableProfiles,
            activeProfile = state.activeProfile,
            onDismiss = { showStartSessionDialog = false },
            onConfirm = { app, profile, mode, duration, consents ->
                showStartSessionDialog = false
                viewModel.startSession(
                    targetPackage = app.packageName,
                    targetAppName = app.appName,
                    profile = profile,
                    mode = mode,
                    durationMinutes = duration,
                    consents = consents
                )
            }
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
                        text = auditStatusLabel(log.status),
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
                        fontSize = 12.sp
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

private data class ConsentRow(val consent: String, val title: String, val description: String)

// Every optional capability, each its own switch, each saying plainly where the data goes.
private val CONSENT_ROWS = listOf(
    ConsentRow(
        Consent.SCREENSHOTS, "Autoriser les captures d'écran",
        "Pendant cette session seulement, l'agent peut demander une image de la fenêtre de l'application cible " +
            "(Android 14 ou plus récent). Elle est envoyée au modèle de John ou, s'il n'en lit pas les images, à son " +
            "modèle de vision auxiliaire (le fournisseur configuré pour ce profil) et peut montrer messages, noms ou " +
            "photos. Les champs de mot de passe cachés sont masqués ; un mot de passe affiché en clair ne peut pas " +
            "l'être. Les fenêtres protégées ne sont jamais capturées."
    ),
    ConsentRow(
        Consent.CALENDAR, "Autoriser la lecture du calendrier",
        "Pendant cette session seulement, l'agent peut lire vos 7 prochains jours : titre, début, fin et lieu de " +
            "chaque événement (50 au plus), jamais les participants ni les notes. Ces informations sont envoyées au " +
            "modèle de John (le fournisseur configuré pour ce profil). Android vous demandera l'accès au calendrier."
    ),
    ConsentRow(
        Consent.CALENDAR_WRITE, "Autoriser l'écriture du calendrier (avec confirmation)",
        "Pendant cette session seulement, l'agent peut PROPOSER de créer, modifier ou supprimer un événement. Rien ne " +
            "change sans votre appui sur le bouton d'une notification qui montre l'événement exact (60 s, sinon refus). " +
            "Les événements créés n'ont pas d'invités ; les événements récurrents ou avec invités ne sont jamais " +
            "modifiés ni supprimés. Android vous demandera l'accès au calendrier."
    ),
    ConsentRow(
        Consent.SMS_READ, "Autoriser la lecture des SMS",
        "Pendant cette session seulement, l'agent peut lire vos 20 derniers SMS des dernières 24 h : expéditeur, " +
            "heure et texte. Les codes de 4 à 8 chiffres sont remplacés par [code] avant l'envoi. Ces messages sont " +
            "envoyés au modèle de John (le fournisseur configuré pour ce profil). Android vous demandera l'accès aux " +
            "SMS et aux contacts (pour les noms)."
    ),
    ConsentRow(
        Consent.CALL_LOG_READ, "Autoriser la lecture du journal d'appels",
        "Pendant cette session seulement, l'agent peut lire vos 20 derniers appels des dernières 24 h : numéro ou " +
            "nom, sens, heure et durée. Ces informations sont envoyées au modèle de John (le fournisseur configuré " +
            "pour ce profil). Android vous demandera l'accès au journal d'appels et aux contacts."
    ),
    ConsentRow(
        Consent.SMS_SEND, "Autoriser l'envoi de SMS (avec confirmation)",
        "Pendant cette session seulement, l'agent peut PROPOSER un SMS à l'un de vos contacts. Rien n'est envoyé sans " +
            "votre appui sur « Envoyer » dans une notification qui montre le destinataire et le texte exacts (60 s, " +
            "sinon refus). Un envoi peut coûter de l'argent et ne s'annule pas. Android vous demandera l'accès à " +
            "l'envoi de SMS et aux contacts."
    ),
    ConsentRow(
        Consent.CALL_PLACE, "Autoriser les appels (avec confirmation)",
        "Pendant cette session seulement, l'agent peut PROPOSER d'appeler l'un de vos contacts. Aucun appel n'est " +
            "lancé sans votre appui sur « Appeler » dans une notification qui montre le contact (60 s, sinon refus). " +
            "Android vous demandera l'accès aux appels et aux contacts."
    )
)

@Composable
private fun ConsentGroupHeader(level: ConsentLevel) {
    val (number, title, subtitle) = when (level) {
        ConsentLevel.Read -> Triple("1", "Lecture", "Consulter, sans rien modifier")
        ConsentLevel.ConfirmedWrite -> Triple("2", "Écriture avec confirmation", "Chaque action vous est demandée")
        ConsentLevel.CallsAndSms -> Triple("3", "Appels & SMS", "Confirmation + permission Android")
    }
    val (container, content) = when (level) {
        ConsentLevel.Read -> HermesColors.PrimaryContainer to HermesColors.OnPrimaryContainer
        ConsentLevel.ConfirmedWrite -> HermesColors.WarningContainer to HermesColors.OnWarningContainer
        ConsentLevel.CallsAndSms -> HermesColors.DangerContainer to HermesColors.OnDangerContainer
    }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = content, style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = HermesTextSecondary)
        }
    }
}

@Composable
private fun StartSessionDialog(
    allowedApps: List<AllowedApp>,
    availableProfiles: List<String>,
    activeProfile: String,
    onDismiss: () -> Unit,
    onConfirm: (AllowedApp, String, MobileControlMode, Int, Set<String>) -> Unit
) {
    var selectedApp by remember { mutableStateOf(allowedApps.firstOrNull()) }
    // Always off when the dialog opens: each consent is given again, on purpose, for each session.
    var consents by remember { mutableStateOf(setOf<String>()) }
    // The Android permissions of a consent are asked only when its switch is turned on; refused means off.
    var askingFor by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val consent = askingFor
        askingFor = null
        if (consent != null && Consent.requiredPermissions(consent, android.os.Build.VERSION.SDK_INT).all { granted[it] == true }) {
            consents = consents + consent
        }
    }
    fun toggle(consent: String, on: Boolean) {
        if (!on) {
            consents = consents - consent
            return
        }
        val required = Consent.requiredPermissions(consent, android.os.Build.VERSION.SDK_INT)
        if (required.isEmpty()) {
            consents = consents + consent
        } else {
            askingFor = consent
            permissionLauncher.launch((required + Consent.OPTIONAL_PERMISSIONS[consent].orEmpty()).toTypedArray())
        }
    }
    val isJohnAvailable = availableProfiles.contains("john")
    var selectedMode by remember { mutableStateOf(MobileControlMode.INTERACTION) }
    var selectedDuration by remember { mutableStateOf(15) } // minutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.control_demarrer_le_controle_mobile)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.control_application_cible), style = MaterialTheme.typography.labelMedium)
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

                Text(stringResource(R.string.control_profil_hermes_autorise), style = MaterialTheme.typography.labelMedium)
                if (isJohnAvailable) {
                    FilterChip(
                        selected = true,
                        onClick = { /* John est le seul profil autorisé pour le contrôle mobile */ },
                        label = { Text(stringResource(R.string.control_john_john)) },
                        leadingIcon = {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                } else {
                    Text(
                        stringResource(R.string.control_le_profil_john_john_est),
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesError
                    )
                }

                Text(stringResource(R.string.control_mode_de_controle), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == MobileControlMode.INTERACTION,
                        onClick = { selectedMode = MobileControlMode.INTERACTION },
                        label = { Text(stringResource(R.string.control_interaction)) }
                    )
                    FilterChip(
                        selected = selectedMode == MobileControlMode.OBSERVATION,
                        onClick = { selectedMode = MobileControlMode.OBSERVATION },
                        label = { Text(stringResource(R.string.control_observation_seule)) }
                    )
                }

                Text(stringResource(R.string.control_duree_maximale), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30).forEach { dur ->
                        FilterChip(
                            selected = selectedDuration == dur,
                            onClick = { selectedDuration = dur },
                            label = { Text(stringResource(R.string.control_min, dur)) }
                        )
                    }
                }

                // Grouped by sensitivity; each consent is still given for this session only.
                ConsentLevel.entries.forEach { level ->
                    ConsentGroupHeader(level)
                    CONSENT_ROWS.filter { consentLevel(it.consent) == level }.forEach { row ->
                        val on = row.consent in consents
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.title, style = MaterialTheme.typography.labelLarge)
                                // State as icon + text, not colour alone.
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        if (on) Icons.Default.Check else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (on) HermesColors.OnSuccessContainer else HermesTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        if (on) stringResource(R.string.control_autorise_pour_cette_session) else stringResource(R.string.control_refuse),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (on) HermesColors.OnSuccessContainer else HermesTextSecondary
                                    )
                                }
                                Text(row.description, style = MaterialTheme.typography.bodySmall, color = HermesTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LabeledSwitch(checked = on, onCheckedChange = { toggle(row.consent, it) }, label = row.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val app = selectedApp
                    if (app != null && isJohnAvailable) {
                        onConfirm(app, "john", selectedMode, selectedDuration, consents)
                    }
                },
                enabled = selectedApp != null && isJohnAvailable
            ) {
                Text(stringResource(R.string.control_demarrer))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        title = { Text(stringResource(R.string.control_autoriser_une_application)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.control_nom_d_affichage_ex_linkedin)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkgText,
                    onValueChange = { pkgText = it },
                    label = { Text(stringResource(R.string.control_nom_du_package_ex_com)) },
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
                Text(stringResource(R.string.workspaces_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

package com.example.hermes.features.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.core.model.OpenBaoHealth
import com.example.hermes.core.model.OpenBaoSecretItem
import com.example.hermes.theme.*
import androidx.compose.ui.res.stringResource
import com.example.hermes.R
import com.example.hermes.core.data.HermesPreferences.Companion.DEFAULT_OPENBAO_MOUNT
import com.example.hermes.core.data.HermesPreferences.Companion.DEFAULT_OPENBAO_URL

private val SUGGESTED_SECRET_KEYS = listOf(
    "CRM_API_KEY",
    "TELEGRAM_BOT_TOKEN",
    "DISCORD_BOT_TOKEN",
    "SLACK_BOT_TOKEN",
    "SERPAPI_API_KEY",
    "TAVILY_API_KEY",
    "GITHUB_TOKEN",
    "DATABASE_URL",
    "STRIPE_API_KEY",
    "AWS_ACCESS_KEY_ID",
    "AWS_SECRET_ACCESS_KEY"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenBaoVaultScreen(
    activeProfile: String,
    openbaoUrl: String,
    openbaoToken: String,
    openbaoMount: String,
    health: OpenBaoHealth?,
    secrets: List<OpenBaoSecretItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onSaveConfig: (url: String, token: String, mount: String, onFinished: (Boolean, String?) -> Unit) -> Unit,
    onSaveSecret: (key: String, value: String, onFinished: (Boolean, String?) -> Unit) -> Unit,
    onDeleteSecret: (key: String, onFinished: (Boolean, String?) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onRestartGateway: (() -> Unit)? = null,
    isRestartingGateway: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    var isConfigExpanded by remember { mutableStateOf(openbaoUrl.isBlank() || openbaoToken.isBlank()) }
    var configUrlInput by remember(openbaoUrl) { mutableStateOf(openbaoUrl) }
    var configTokenInput by remember(openbaoToken) { mutableStateOf(openbaoToken) }
    var configMountInput by remember(openbaoMount) { mutableStateOf(openbaoMount) }
    var showTokenPassword by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var revealedKeys by remember { mutableStateOf(setOf<String>()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }

    var secretToDelete by remember { mutableStateOf<String?>(null) }

    val filteredSecrets = remember(secrets, searchQuery) {
        if (searchQuery.isBlank()) {
            secrets
        } else {
            secrets.filter { it.key.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    // A pushed screen (Réglages › Coffre OpenBao); system back pops it.
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = OnyxDarkBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = HermesTextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(HermesSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = HermesSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.settings_openbao),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.openbao_runtime_path, openbaoMount.ifBlank { DEFAULT_OPENBAO_MOUNT }, activeProfile.lowercase()),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = HermesSecondary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                                tint = HermesTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Status Banner ──
                val statusColor = when {
                    openbaoUrl.isBlank() -> HermesTextMuted
                    health == null -> HermesError
                    health.sealed -> HermesWarning
                    health.initialized -> HermesSecondary
                    else -> HermesWarning
                }

                val statusText = when {
                    openbaoUrl.isBlank() -> "Non configuré (cliquez sur Paramètres)"
                    health == null -> "Injoignable ou en attente de test"
                    health.sealed -> "Scellé (Sealed) - Veuillez le déverrouiller"
                    health.initialized -> "Déverrouillé & Prêt (OpenBao ${health.version})"
                    else -> "Non initialisé"
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = HermesTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (openbaoUrl.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val webUrl = if (openbaoUrl.endsWith("/")) "${openbaoUrl}ui" else "$openbaoUrl/ui"
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Impossible d'ouvrir l'URL", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = HermesPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.openbao_web_ui), style = MaterialTheme.typography.labelSmall, color = HermesPrimary)
                            }
                        }
                    }
                }

                // ── Gateway Runtime Info Banner ──
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = HermesPrimaryContainer.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HermesPrimary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = HermesPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.openbao_les_agents_chargent_leurs_secrets),
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (onRestartGateway != null) {
                            OutlinedButton(
                                onClick = onRestartGateway,
                                enabled = !isRestartingGateway,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = HermesPrimaryContainer.copy(alpha = 0.35f),
                                    contentColor = HermesPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HermesPrimary.copy(alpha = 0.5f))
                            ) {
                                if (isRestartingGateway) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = HermesPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.openbao_redemarrage_en_cours), fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = HermesPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.openbao_redemarrer_la_passerelle, activeProfile.replaceFirstChar { it.uppercase() }), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Collapsible Configuration Card ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = OnyxDarkSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OnyxBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isConfigExpanded = !isConfigExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = HermesTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.openbao_connexion_openbao),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HermesTextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (isConfigExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = HermesTextSecondary
                            )
                        }

                        AnimatedVisibility(visible = isConfigExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = configUrlInput,
                                    onValueChange = { configUrlInput = it },
                                    label = { Text(stringResource(R.string.openbao_url_du_serveur_openbao)) },
                                    placeholder = { Text(DEFAULT_OPENBAO_URL) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = HermesPrimary,
                                        unfocusedBorderColor = OnyxBorder
                                    )
                                )

                                OutlinedTextField(
                                    value = configTokenInput,
                                    onValueChange = { configTokenInput = it },
                                    label = { Text(stringResource(R.string.openbao_token_utilisateur_dedie_droits_restreints)) },
                                    placeholder = { Text(stringResource(R.string.openbao_hvs_xxxxxxxx_ne_pas_utiliser)) },
                                    supportingText = {
                                        Text(
                                            stringResource(R.string.openbao_token_scope_hint, configMountInput.ifBlank { DEFAULT_OPENBAO_MOUNT }, activeProfile.lowercase()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HermesTextMuted
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (showTokenPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                                            Icon(
                                                imageVector = if (showTokenPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = HermesTextSecondary
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = HermesPrimary,
                                        unfocusedBorderColor = OnyxBorder
                                    )
                                )

                                OutlinedTextField(
                                    value = configMountInput,
                                    onValueChange = { configMountInput = it },
                                    label = { Text(stringResource(R.string.openbao_point_de_montage_kv_v2)) },
                                    placeholder = { Text(DEFAULT_OPENBAO_MOUNT) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = HermesPrimary,
                                        unfocusedBorderColor = OnyxBorder
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            onSaveConfig(configUrlInput, configTokenInput, configMountInput) { success, err ->
                                                if (success) {
                                                    Toast.makeText(context, "OpenBao connecté !", Toast.LENGTH_SHORT).show()
                                                    isConfigExpanded = false
                                                } else {
                                                    Toast.makeText(context, err ?: "Erreur de configuration", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.openbao_enregistrer_tester), color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Search & Add Secret Bar ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.openbao_filtrer_les_secrets), style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = HermesTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.auth_clear),
                                        tint = HermesTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Button(
                        onClick = {
                            editingKey = null
                            editingValue = ""
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HermesSecondaryContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = HermesSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.workspaces_add_confirm), color = HermesSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Error Message if any ──
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesError,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // ── Secrets List ──
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HermesSecondary)
                    }
                } else if (filteredSecrets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = HermesTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isBlank()) "Aucun secret enregistré dans OpenBao" else "Aucun secret correspondant",
                                style = MaterialTheme.typography.titleMedium,
                                color = HermesTextSecondary
                            )
                            Text(
                                text = stringResource(R.string.openbao_secure_path, openbaoMount.ifBlank { DEFAULT_OPENBAO_MOUNT }, activeProfile.lowercase()),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = HermesTextMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSecrets, key = { it.key }) { item ->
                            val isRevealed = revealedKeys.contains(item.key)

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = OnyxDarkSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, OnyxBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.key,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = HermesTextPrimary
                                        )

                                        Text(
                                            text = if (isRevealed) item.value else "••••••••••••••••",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = if (isRevealed) HermesSecondary else HermesTextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Reveal Toggle
                                        IconButton(
                                            onClick = {
                                                revealedKeys = if (isRevealed) {
                                                    revealedKeys - item.key
                                                } else {
                                                    revealedKeys + item.key
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = HermesTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Copy
                                        IconButton(
                                            onClick = {
                                                clipboardManager?.setPrimaryClip(
                                                    ClipData.newPlainText(item.key, item.value)
                                                )
                                                Toast.makeText(context, "${item.key} copié !", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.openbao_copier),
                                                tint = HermesTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Edit
                                        IconButton(
                                            onClick = {
                                                editingKey = item.key
                                                editingValue = item.value
                                                showAddEditDialog = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.openbao_modifier),
                                                tint = HermesPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Delete (protected for system marker TEST_CONNECTION)
                                        if (item.key != "TEST_CONNECTION") {
                                            IconButton(
                                                onClick = { secretToDelete = item.key }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.action_delete),
                                                    tint = HermesError,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    Toast.makeText(context, "TEST_CONNECTION=ok est requis pour le démarrage de la passerelle", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = stringResource(R.string.openbao_systeme_requis),
                                                    tint = HermesTextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add/Edit Secret Sub-Dialog ──
    if (showAddEditDialog) {
        var keyInput by remember { mutableStateOf(editingKey ?: "") }
        var valueInput by remember { mutableStateOf(editingValue) }
        var showSecretValue by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showAddEditDialog = false },
            title = {
                Text(
                    text = if (editingKey != null) "Modifier le secret" else "Nouveau secret",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = HermesTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.openbao_les_cles_sont_automatiquement_converties),
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextSecondary
                    )

                    // Suggested chips for quick picking
                    if (editingKey == null) {
                        Text(
                            text = stringResource(R.string.openbao_suggestions_courantes),
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SUGGESTED_SECRET_KEYS.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { keyInput = suggestion },
                                    label = {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.uppercase() },
                        label = { Text(stringResource(R.string.openbao_nom_de_la_variable_cle)) },
                        placeholder = { Text(stringResource(R.string.openbao_exemple_crm_api_key)) },
                        enabled = editingKey == null && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text(stringResource(R.string.openbao_valeur_du_secret_token)) },
                        placeholder = { Text(stringResource(R.string.openbao_sk_xxxx_ou_token)) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showSecretValue) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecretValue = !showSecretValue }) {
                                Icon(
                                    imageVector = if (showSecretValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = HermesTextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HermesSecondary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalKey = keyInput.trim().uppercase()
                        if (finalKey.isBlank()) {
                            Toast.makeText(context, "Le nom de la variable est requis", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        onSaveSecret(finalKey, valueInput) { success, err ->
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Secret enregistré !", Toast.LENGTH_SHORT).show()
                                showAddEditDialog = false
                            } else {
                                Toast.makeText(context, err ?: "Erreur d'enregistrement", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = HermesSecondary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddEditDialog = false },
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.action_cancel), color = HermesTextSecondary)
                }
            },
            containerColor = OnyxDarkSurface
        )
    }

    // ── Delete Confirmation Dialog ──
    secretToDelete?.let { keyToDelete ->
        AlertDialog(
            onDismissRequest = { secretToDelete = null },
            title = { Text(stringResource(R.string.openbao_supprimer_le_secret), color = HermesTextPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.openbao_etes_vous_sur_de_vouloir, keyToDelete),
                    color = HermesTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSecret(keyToDelete) { success, err ->
                            if (success) {
                                Toast.makeText(context, "Secret supprimé", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, err ?: "Erreur", Toast.LENGTH_LONG).show()
                            }
                            secretToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HermesError)
                ) {
                    Text(stringResource(R.string.action_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { secretToDelete = null }) {
                    Text(stringResource(R.string.action_cancel), color = HermesTextSecondary)
                }
            },
            containerColor = OnyxDarkSurface
        )
    }
}

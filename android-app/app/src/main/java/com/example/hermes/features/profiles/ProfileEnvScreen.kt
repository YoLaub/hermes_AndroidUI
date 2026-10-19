package com.example.hermes.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ProfileEnvEntry
import com.example.hermes.core.model.ProviderInfo
import com.example.hermes.theme.*
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.ScreenHeader
import androidx.compose.ui.res.stringResource
import com.example.hermes.R

private val SUGGESTED_ENV_VARS = listOf(
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "GEMINI_API_KEY",
    "GROQ_API_KEY",
    "OPENROUTER_API_KEY",
    "MISTRAL_API_KEY",
    "DEEPSEEK_API_KEY",
    "TOGETHER_API_KEY",
    "PERPLEXITY_API_KEY",
    "XAI_API_KEY"
)

private data class PendingEnvDelete(val key: String, val isProvider: Boolean, val label: String? = null)

@Composable
fun ProfileEnvScreen(
    activeProfile: String,
    envEntries: List<ProfileEnvEntry>,
    providers: List<ProviderInfo>,
    isLoading: Boolean,
    isEnvFallback: Boolean = false,
    onSaveEnvVar: (key: String, value: String, onFinished: (Boolean, String?) -> Unit) -> Unit,
    onDeleteEnvVar: (key: String, onFinished: ((Boolean, String?) -> Unit)?) -> Unit,
    onSaveProviderKey: (providerId: String, apiKey: String, onFinished: (Boolean, String?) -> Unit) -> Unit,
    onDeleteProviderKey: (providerId: String, onFinished: ((Boolean, String?) -> Unit)?) -> Unit,
    onRefresh: () -> Unit,
    onOpenOpenbao: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    var selectedTab by remember(isEnvFallback) { mutableStateOf(if (isEnvFallback) 1 else 0) }
    var showAddVarDialog by remember { mutableStateOf(false) }
    var editingVarKey by remember { mutableStateOf<String?>(null) }
    var editingVarValue by remember { mutableStateOf("") }
    var selectedProviderForEdit by remember { mutableStateOf<ProviderInfo?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var revealedKeys by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<PendingEnvDelete?>(null) }

    // Fallback: If custom env endpoint is empty/unreachable, synthesize env entries from configured providers
    val effectiveEnvEntries = remember(envEntries, providers) {
        if (envEntries.isNotEmpty()) {
            envEntries
        } else {
            providers.filter { it.hasKey }.map { prov ->
                val envKey = when (prov.id.lowercase()) {
                    "openai" -> "OPENAI_API_KEY"
                    "anthropic" -> "ANTHROPIC_API_KEY"
                    "openrouter" -> "OPENROUTER_API_KEY"
                    "google", "gemini" -> "GEMINI_API_KEY"
                    "groq" -> "GROQ_API_KEY"
                    "mistral" -> "MISTRAL_API_KEY"
                    "deepseek" -> "DEEPSEEK_API_KEY"
                    "together" -> "TOGETHER_API_KEY"
                    "fireworks" -> "FIREWORKS_API_KEY"
                    "cohere" -> "COHERE_API_KEY"
                    "xai" -> "XAI_API_KEY"
                    "perplexity" -> "PERPLEXITY_API_KEY"
                    "nous" -> "NOUS_API_KEY"
                    else -> "${prov.id.uppercase()}_API_KEY"
                }
                ProfileEnvEntry(
                    key = envKey,
                    value = "Clé active (${prov.keySource})",
                    hasValue = true
                )
            }
        }
    }

    // A pushed screen (Agent › Variables & fournisseurs); system back pops it.
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Variables & fournisseurs (.env)",
            subtitle = stringResource(R.string.breadcrumb_agent_profile, activeProfile),
            onBack = onDismiss
        ) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        Box(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
            ) {
                // Info banner explaining profile isolation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OnyxDarkBackground,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = HermesSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.env_variables_isolees_dans_le_env, activeProfile),
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenOpenbao != null) {
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onOpenOpenbao()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = HermesSecondary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.env_openbao), style = MaterialTheme.typography.labelSmall, color = HermesSecondary)
                            }
                        }
                    }
                }

                // Tabs: All .env vars vs AI Providers
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OnyxDarkBackground,
                    contentColor = HermesPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = stringResource(R.string.env_variables_env, effectiveEnvEntries.size),
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = stringResource(R.string.env_providers_ia, providers.count { it.hasKey }),
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                if (isLoading && effectiveEnvEntries.isEmpty() && providers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HermesPrimary)
                    }
                } else if (selectedTab == 0) {
                    // ── Tab 0: Generic .env Variables ──
                    Column(modifier = Modifier.weight(1f)) {
                        if (isEnvFallback) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2E2205),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEAB308))
                                ),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFEAB308),
                                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.env_mode_officiel_hermes_webui_le),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFEF08A),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                editingVarKey = null
                                editingVarValue = ""
                                showAddVarDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.env_ajouter_une_variable_env), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        if (effectiveEnvEntries.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyOff,
                                    contentDescription = null,
                                    tint = HermesTextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.env_aucune_variable_dans_le_env),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HermesTextMuted
                                )
                                Text(
                                    text = stringResource(R.string.env_cliquez_sur_ajouter_une_variable),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HermesTextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(effectiveEnvEntries, key = { it.key }) { entry ->
                                    val isRevealed = revealedKeys.contains(entry.key)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = OnyxDarkBackground),
                                        shape = RoundedCornerShape(8.dp),
                                        border = CardDefaults.outlinedCardBorder().copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.key,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = HermesTextPrimary,
                                                    fontSize = 13.sp
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    val displayVal = if (isRevealed) {
                                                        entry.value.ifBlank { "(vide)" }
                                                    } else {
                                                        if (entry.value.length > 8) {
                                                            entry.value.take(4) + "••••••••" + entry.value.takeLast(4)
                                                        } else {
                                                            "••••••••"
                                                        }
                                                    }
                                                    Text(
                                                        text = displayVal,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        color = HermesTextSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = stringResource(R.string.env_toggle_visibilite),
                                                        tint = HermesTextMuted,
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable {
                                                                revealedKeys = if (isRevealed) {
                                                                    revealedKeys - entry.key
                                                                } else {
                                                                    revealedKeys + entry.key
                                                                }
                                                            }
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        editingVarKey = entry.key
                                                        editingVarValue = entry.value
                                                        showAddVarDialog = true
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = stringResource(R.string.openbao_modifier),
                                                        tint = HermesPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { pendingDelete = PendingEnvDelete(entry.key, isProvider = false) }
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.action_delete),
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
                } else {
                    // ── Tab 1: AI Providers ──
                    if (providers.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = HermesTextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.env_aucun_provider_detecte),
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesTextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(providers, key = { it.id }) { provider ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = OnyxDarkBackground),
                                    shape = RoundedCornerShape(10.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = provider.displayName.ifBlank { provider.id.replaceFirstChar { it.uppercase() } },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = HermesTextPrimary
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (provider.hasKey) Color(0xFF10B981) else Color(0xFF6B7280))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (provider.hasKey) stringResource(R.string.env_cle_active, provider.keySource) else stringResource(R.string.env_non_configure),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (provider.hasKey) Color(0xFF10B981) else HermesTextMuted
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (provider.hasKey) {
                                                IconButton(
                                                    onClick = { pendingDelete = PendingEnvDelete(provider.id, isProvider = true, label = provider.displayName.ifBlank { provider.id }) }
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.env_supprimer_cle),
                                                        tint = HermesTextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    selectedProviderForEdit = provider
                                                    apiKeyInput = ""
                                                    showPassword = false
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text(if (provider.hasKey) stringResource(R.string.openbao_modifier) else stringResource(R.string.workspaces_add_confirm), fontSize = 12.sp)
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

    pendingDelete?.let { del ->
        ConfirmDialog(
            title = if (del.isProvider) stringResource(R.string.env_supprimer_la_cle, del.label ?: del.key) else stringResource(R.string.env_supprimer, del.key),
            message = stringResource(R.string.env_la_valeur_est_retiree_du, activeProfile),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                if (del.isProvider) onDeleteProviderKey(del.key, null) else onDeleteEnvVar(del.key, null)
            },
            onDismiss = { pendingDelete = null }
        )
    }

    // ── Generic Add / Edit .env Variable Dialog ──
    if (showAddVarDialog) {
        var inputKey by remember(editingVarKey) { mutableStateOf(editingVarKey?.uppercase() ?: "") }
        var inputValue by remember(editingVarKey, editingVarValue) { mutableStateOf(editingVarValue) }
        var varShowPassword by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var submitError by remember { mutableStateOf<String?>(null) }

        val isEditMode = !editingVarKey.isNullOrBlank()

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) {
                    showAddVarDialog = false
                    editingVarKey = null
                    editingVarValue = ""
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = HermesPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditMode) stringResource(R.string.env_modifier, editingVarKey.orEmpty()) else stringResource(R.string.env_ajouter_une_variable_env),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.env_la_variable_sera_enregistree_dans, activeProfile),
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isEditMode) {
                        // Suggested Presets Chips
                        Text(
                            text = stringResource(R.string.env_suggestions_rapides),
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SUGGESTED_ENV_VARS.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        inputKey = suggestion.uppercase()
                                        submitError = null
                                    },
                                    label = { Text(suggestion, fontSize = 12.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = OnyxDarkBackground,
                                        labelColor = HermesPrimary
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = OnyxBorder
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Variable Name Field (Automatically UpperCase & Underscores)
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { raw ->
                            inputKey = sanitizeEnvKey(raw)
                            submitError = null
                        },
                        isError = inputKey.isNotEmpty() && envKeyError(inputKey) != null,
                        supportingText = {
                            when (envKeyError(inputKey)) {
                                EnvKeyError.StartsWithDigit -> Text(stringResource(R.string.env_le_nom_ne_peut_pas))
                                EnvKeyError.Invalid -> Text(stringResource(R.string.env_majuscules_chiffres_et_uniquement))
                                else -> Text(stringResource(R.string.env_en_majuscules_ex_openai_api))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        label = { Text(stringResource(R.string.env_nom_de_la_variable_majuscules)) },
                        placeholder = { Text(stringResource(R.string.env_ex_crm_api_key_telegram)) },
                        singleLine = true,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnyxDarkBackground,
                            unfocusedContainerColor = OnyxDarkBackground,
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Variable Value Field
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = {
                            inputValue = it
                            submitError = null
                        },
                        label = { Text(stringResource(R.string.env_valeur_cle_parametre)) },
                        placeholder = { Text(stringResource(R.string.env_sk_ou_https_ou_jeton)) },
                        singleLine = true,
                        enabled = !isSubmitting,
                        visualTransformation = if (varShowPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { varShowPassword = !varShowPassword }) {
                                Icon(
                                    imageVector = if (varShowPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = HermesTextMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnyxDarkBackground,
                            unfocusedContainerColor = OnyxDarkBackground,
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    // Inline Error Display (Never dismissed on failure so user can read it!)
                    if (submitError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF3F1313),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = submitError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalKey = inputKey.trim().uppercase()
                        val finalValue = inputValue.trim()
                        if (finalKey.isNotBlank() && finalValue.isNotBlank()) {
                            isSubmitting = true
                            submitError = null
                            onSaveEnvVar(finalKey, finalValue) { success, errorMsg ->
                                isSubmitting = false
                                if (success) {
                                    showAddVarDialog = false
                                    editingVarKey = null
                                    editingVarValue = ""
                                } else {
                                    submitError = errorMsg ?: "Échec de l'enregistrement de la variable."
                                }
                            }
                        }
                    },
                    enabled = envKeyError(inputKey) == null && inputValue.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.env_enregistrement), fontSize = 12.sp)
                    } else {
                        Text(stringResource(R.string.env_enregistrer_dans_env), fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddVarDialog = false
                        editingVarKey = null
                        editingVarValue = ""
                    },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = OnyxDarkSurface
        )
    }

    // ── Edit Provider Key Sub-dialog ──
    selectedProviderForEdit?.let { provider ->
        var isSubmittingKey by remember { mutableStateOf(false) }
        var providerError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingKey) {
                    selectedProviderForEdit = null
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.env_configurer, provider.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    color = HermesTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.env_cette_variable_sera_ecrite_dans, activeProfile),
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            providerError = null
                        },
                        label = { Text(stringResource(R.string.env_cle_d_api)) },
                        placeholder = { Text(stringResource(R.string.env_sk)) },
                        singleLine = true,
                        enabled = !isSubmittingKey,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = HermesTextMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnyxDarkBackground,
                            unfocusedContainerColor = OnyxDarkBackground,
                            focusedBorderColor = HermesPrimary,
                            unfocusedBorderColor = OnyxBorder
                        )
                    )

                    if (providerError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF3F1313),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEF4444))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = providerError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            isSubmittingKey = true
                            providerError = null
                            onSaveProviderKey(provider.id, apiKeyInput.trim()) { success, errorMsg ->
                                isSubmittingKey = false
                                if (success) {
                                    selectedProviderForEdit = null
                                } else {
                                    providerError = errorMsg ?: "Échec de l'enregistrement de la clé."
                                }
                            }
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && !isSubmittingKey,
                    colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                ) {
                    if (isSubmittingKey) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.env_enregistrement), fontSize = 12.sp)
                    } else {
                        Text(stringResource(R.string.env_enregistrer_dans_env), fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedProviderForEdit = null },
                    enabled = !isSubmittingKey
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = OnyxDarkSurface
        )
    }
}

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

private val SUGGESTED_ENV_VARS = listOf(
    "TELEGRAM_BOT_TOKEN",
    "DISCORD_TOKEN",
    "CRM_API_KEY",
    "SERPAPI_API_KEY",
    "TAVILY_API_KEY",
    "GITHUB_TOKEN",
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "GEMINI_API_KEY",
    "GROQ_API_KEY",
    "DATABASE_URL"
)

@Composable
fun ProfileEnvDialog(
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
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = All .env vars, 1 = AI Providers
    var showAddVarDialog by remember { mutableStateOf(false) }
    var editingVarKey by remember { mutableStateOf<String?>(null) }
    var editingVarValue by remember { mutableStateOf("") }
    var selectedProviderForEdit by remember { mutableStateOf<ProviderInfo?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var revealedKeys by remember { mutableStateOf(setOf<String>()) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Variables d'environnement (.env)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    Text(
                        text = "Profil actif : $activeProfile",
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesPrimary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = HermesTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
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
                            text = "Variables isolées dans le .env du profil « $activeProfile ».",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary
                        )
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
                                text = "Variables .env (${effectiveEnvEntries.size})",
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
                                text = "Providers IA (${providers.count { it.hasKey }})",
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
                                        text = "Serveur en mode restreint : le conteneur distant n'a pas encore le module /api/profile/env. Seules les clés IA configurées sont lues. Pour vos variables de services/MCP, montez webui-backend sur Docker.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFEF08A),
                                        fontSize = 11.sp
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
                            Text("Ajouter une variable (.env)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                                    text = "Aucune variable dans le .env de ce profil",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HermesTextMuted
                                )
                                Text(
                                    text = "Cliquez sur « Ajouter une variable » ci-dessus",
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
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "Toggle visibilité",
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
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Modifier",
                                                        tint = HermesPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteEnvVar(entry.key, null) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Supprimer",
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
                                text = "Aucun provider détecté",
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
                                                    text = if (provider.hasKey) "Clé active (${provider.keySource})" else "Non configuré",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (provider.hasKey) Color(0xFF10B981) else HermesTextMuted
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (provider.hasKey) {
                                                IconButton(
                                                    onClick = { onDeleteProviderKey(provider.id, null) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Supprimer clé",
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
                                                Text(if (provider.hasKey) "Modifier" else "Ajouter", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        },
        containerColor = OnyxDarkSurface
    )

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
                        text = if (isEditMode) "Modifier $editingVarKey" else "Ajouter une variable (.env)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "La variable sera enregistrée dans le .env du profil $activeProfile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HermesTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isEditMode) {
                        // Suggested Presets Chips
                        Text(
                            text = "Suggestions rapides :",
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
                                    label = { Text(suggestion, fontSize = 10.sp) },
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
                            val sanitized = raw.uppercase()
                                .replace(" ", "_")
                                .replace("-", "_")
                                .filter { it.isLetterOrDigit() || it == '_' }
                            inputKey = sanitized
                            submitError = null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        label = { Text("Nom de la variable (MAJUSCULES)") },
                        placeholder = { Text("EX: CRM_API_KEY, TELEGRAM_BOT_TOKEN") },
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
                        label = { Text("Valeur / Clé / Paramètre") },
                        placeholder = { Text("sk-... ou https://... ou jeton") },
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
                                    fontSize = 11.sp
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
                    enabled = inputKey.isNotBlank() && inputValue.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enregistrement...", fontSize = 12.sp)
                    } else {
                        Text("Enregistrer dans .env", fontSize = 12.sp)
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
                    Text("Annuler")
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
                    text = "Configurer ${provider.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = HermesTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Cette variable sera écrite dans le .env du profil $activeProfile.",
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
                        label = { Text("Clé d'API") },
                        placeholder = { Text("sk-...") },
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
                                    fontSize = 11.sp
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
                        Text("Enregistrement...", fontSize = 12.sp)
                    } else {
                        Text("Enregistrer dans .env", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedProviderForEdit = null },
                    enabled = !isSubmittingKey
                ) {
                    Text("Annuler")
                }
            },
            containerColor = OnyxDarkSurface
        )
    }
}

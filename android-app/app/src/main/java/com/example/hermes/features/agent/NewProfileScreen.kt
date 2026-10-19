package com.example.hermes.features.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.SectionCard
import com.example.hermes.ui.components.ServerErrorBox
import com.example.hermes.ui.components.personaOf

/** Nouveau profil: identity, starting point, model and key. The server's refusal shows as sent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProfileScreen(
    existing: List<ProfileInfo>,
    isSubmitting: Boolean,
    error: String?,
    onCreate: (name: String, cloneFrom: String?, model: String?, provider: String?, apiKey: String?) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var cloneFrom by rememberSaveable { mutableStateOf(defaultCloneSource(existing)) }
    var model by rememberSaveable { mutableStateOf("") }
    // Not saveable: a key is never written to the saved instance state.
    var apiKey by androidx.compose.runtime.remember { mutableStateOf("") }
    val nameError = profileNameError(name, existing)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(R.string.profiles_new), subtitle = stringResource(R.string.agent_change_profile), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            error?.let { ServerErrorBox(it) }

            SectionCard(stringResource(R.string.new_profile_identity)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().trim() },
                    label = { Text(stringResource(R.string.new_profile_name)) },
                    placeholder = { Text("redacteur") },
                    isError = name.isNotEmpty() && nameError != null,
                    supportingText = {
                        Text(
                            stringResource(
                                when (if (name.isEmpty()) null else nameError) {
                                    ProfileNameError.Format -> R.string.new_profile_name_format
                                    ProfileNameError.Taken -> R.string.new_profile_name_taken
                                    else -> R.string.new_profile_name_help
                                }
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                )
            }

            SectionCard(stringResource(R.string.new_profile_start)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.new_profile_start_help), style = MaterialTheme.typography.bodySmall, color = HermesColors.OnSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existing.distinctBy { it.name }.forEach { p ->
                            FilterChip(
                                selected = cloneFrom == p.name,
                                onClick = { cloneFrom = p.name },
                                label = { Text(personaOf(p).name + if (p.isDefault) " · " + stringResource(R.string.profiles_default_tag) else "") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HermesColors.PrimaryContainer,
                                    selectedLabelColor = HermesColors.OnPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.new_profile_model)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.new_profile_model_field)) },
                        placeholder = { Text("anthropic/claude-sonnet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.new_profile_key)) },
                        supportingText = { Text(stringResource(R.string.new_profile_key_help)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        AppButton(
            stringResource(R.string.new_profile_create),
            onClick = { onCreate(name, cloneFrom, model.trim().ifBlank { null }, null, apiKey.trim().ifBlank { null }) },
            enabled = nameError == null && !isSubmitting,
            loading = isSubmitting,
            icon = Icons.Outlined.Add,
            disabledReason = when (nameError) {
                ProfileNameError.Required -> stringResource(R.string.new_profile_name_required)
                ProfileNameError.Format -> stringResource(R.string.new_profile_name_format)
                ProfileNameError.Taken -> stringResource(R.string.new_profile_name_taken)
                null -> null
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

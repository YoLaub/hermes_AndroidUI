package com.example.hermes.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.SectionCard
import com.example.hermes.ui.components.StatusChip
import com.example.hermes.ui.components.StatusTone

/** Mobile relay address and this phone's pairing. Contrôle only shows the relay's state. */
@Composable
fun RelaySettingsScreen(
    relayUrl: String,
    deviceId: String,
    isPaired: Boolean,
    isConnected: Boolean,
    onSaveUrl: (String) -> Unit,
    onPair: (String) -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit,
) {
    var url by remember(relayUrl) { mutableStateOf(relayUrl) }
    var code by remember { mutableStateOf("") }
    var confirmUnpair by remember { mutableStateOf(false) }
    val urlError = relayUrlError(url)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.relay_title), subtitle = stringResource(R.string.agent_settings), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatusChip(
                stringResource(
                    when {
                        isConnected -> R.string.relay_connected
                        isPaired -> R.string.relay_paired_offline
                        else -> R.string.relay_not_paired
                    }
                ),
                tone = when {
                    isConnected -> StatusTone.Success
                    isPaired -> StatusTone.Warning
                    else -> StatusTone.Neutral
                },
            )

            SectionCard(stringResource(R.string.relay_address)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.relay_url)) },
                        isError = urlError != null,
                        supportingText = {
                            when (urlError) {
                                RelayUrlError.Required -> Text(stringResource(R.string.relay_url_required))
                                RelayUrlError.Scheme -> Text(stringResource(R.string.relay_url_scheme))
                                RelayUrlError.Host -> Text(stringResource(R.string.relay_url_host))
                                null -> {}
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        stringResource(R.string.action_save),
                        onClick = { onSaveUrl(url.trim()) },
                        variant = ButtonVariant.Secondary,
                        enabled = urlError == null && url.trim() != relayUrl,
                    )
                }
            }

            SectionCard(stringResource(R.string.relay_pairing)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.relay_device_id), style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
                    SelectionContainer { Text(deviceId, style = CodeStyle, color = HermesColors.Code) }
                    if (isPaired) {
                        Text(stringResource(R.string.relay_paired_text), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
                        AppButton(
                            stringResource(R.string.relay_unpair),
                            onClick = { confirmUnpair = true },
                            variant = ButtonVariant.DestructiveSecondary,
                        )
                    } else {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(stringResource(R.string.relay_code)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppButton(
                            stringResource(R.string.relay_pair),
                            onClick = {
                                pairingCodeOrNull(code)?.let(onPair)
                                code = ""
                            },
                            enabled = pairingCodeOrNull(code) != null && urlError == null,
                            disabledReason = stringResource(if (urlError != null) R.string.relay_url_required else R.string.relay_code_required),
                        )
                    }
                }
            }
        }
    }

    if (confirmUnpair) {
        ConfirmDialog(
            title = stringResource(R.string.relay_unpair_title),
            message = stringResource(R.string.relay_unpair_message),
            confirmLabel = stringResource(R.string.relay_unpair),
            destructive = true,
            onConfirm = {
                confirmUnpair = false
                onUnpair()
            },
            onDismiss = { confirmUnpair = false },
        )
    }
}

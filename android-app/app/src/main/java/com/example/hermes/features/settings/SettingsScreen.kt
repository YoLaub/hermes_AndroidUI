package com.example.hermes.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
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
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.ListRow
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.SectionCard

/** Réglages: connections and secrets that belong to the instance, not to a profile. */
@Composable
fun SettingsScreen(
    serverUrl: String,
    sessionId: String?,
    onBack: () -> Unit,
    onOpenOpenBao: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.agent_settings), subtitle = stringResource(R.string.nav_agent), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionCard(stringResource(R.string.settings_connections)) {
                ListRow(
                    stringResource(R.string.settings_server),
                    subtitle = listOfNotNull(
                        serverUrl.ifBlank { null },
                        sessionId?.let { stringResource(R.string.settings_session, it.take(12)) },
                    ).joinToString("\n"),
                    icon = Icons.Outlined.Dns,
                )
            }
            SectionCard(stringResource(R.string.settings_secrets)) {
                ListRow(stringResource(R.string.settings_openbao), subtitle = stringResource(R.string.settings_openbao_sub), icon = Icons.Outlined.Lock, onClick = onOpenOpenBao)
            }
            SectionCard(stringResource(R.string.settings_session_section)) {
                ListRow(
                    stringResource(R.string.settings_disconnect),
                    subtitle = stringResource(R.string.settings_disconnect_sub),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    onClick = { confirmDisconnect = true },
                )
            }
            SectionCard(stringResource(R.string.settings_about)) {
                ListRow(stringResource(R.string.settings_version), icon = Icons.Outlined.Info)
            }
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.settings_disconnect_title),
            message = stringResource(R.string.settings_disconnect_message),
            confirmLabel = stringResource(R.string.settings_disconnect_confirm),
            destructive = true,
            onConfirm = {
                confirmDisconnect = false
                onDisconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}


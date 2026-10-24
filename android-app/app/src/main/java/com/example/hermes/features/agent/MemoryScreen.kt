package com.example.hermes.features.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.MemoryResponse
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ButtonVariant
import com.example.hermes.ui.components.ScreenHeader

/** MEMORY.md, USER.md and SOUL.md of the active profile, one tab each, Enregistrer in the bar. */
@Composable
fun MemoryScreen(
    profileName: String,
    memory: MemoryResponse?,
    isLoading: Boolean,
    onSave: (section: MemorySection, content: String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MemorySection.Memory) }
    // Edits are kept per tab until saved; a reload replaces only the untouched ones.
    var edits by remember(memory) { mutableStateOf(mapOf<MemorySection, String>()) }
    val original = when (tab) {
        MemorySection.Memory -> memory?.memory
        MemorySection.User -> memory?.user
        MemorySection.Soul -> memory?.soul
    }
    val text = edits[tab] ?: original.orEmpty()
    val dirty = isDirty(original, text)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.agent_memory_soul),
            subtitle = stringResource(R.string.breadcrumb_agent_profile, profileName),
            onBack = onBack,
        ) {
            AppButton(
                stringResource(R.string.action_save),
                onClick = { onSave(tab, text) },
                variant = ButtonVariant.Text,
                enabled = dirty && !isLoading,
                loading = isLoading && memory != null,
            )
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = HermesColors.Background) {
            MemorySection.entries.forEach { s ->
                Tab(selected = tab == s, onClick = { tab = s }, text = { Text(s.fileName, style = MaterialTheme.typography.labelLarge) })
            }
        }
        if (isLoading && memory == null) {
            Loading()
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { edits = edits + (tab to it) },
                placeholder = {
                    Text(
                        stringResource(
                            when (tab) {
                                MemorySection.Memory -> R.string.memory_hint_memory
                                MemorySection.User -> R.string.memory_hint_user
                                MemorySection.Soul -> R.string.memory_hint_soul
                            }
                        ),
                        color = HermesColors.Meta,
                    )
                },
                textStyle = CodeStyle.copy(color = HermesColors.OnSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HermesColors.Background,
                    unfocusedContainerColor = HermesColors.Background,
                    unfocusedBorderColor = HermesColors.Outline,
                ),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
        }
    }
}

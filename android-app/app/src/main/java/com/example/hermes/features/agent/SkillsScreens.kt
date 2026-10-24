package com.example.hermes.features.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.SkillDetailResponse
import com.example.hermes.core.model.SkillSummary
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.EmptyState
import com.example.hermes.ui.components.ListRow
import com.example.hermes.ui.components.ScreenHeader
import com.example.hermes.ui.components.ServerErrorBox

@Composable
fun SkillsScreen(
    profileName: String,
    skills: List<SkillSummary>,
    isLoading: Boolean,
    onOpenSkill: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.agent_skills),
            subtitle = stringResource(R.string.breadcrumb_agent_profile, profileName),
            onBack = onBack,
        )
        when {
            isLoading && skills.isEmpty() -> Loading()
            skills.isEmpty() -> EmptyState(Icons.Outlined.Extension, stringResource(R.string.skills_empty))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(skills) { skill ->
                    ListRow(
                        skill.name,
                        subtitle = skill.description.ifBlank { null },
                        icon = Icons.Outlined.Extension,
                        onClick = { onOpenSkill(skill.name) },
                    )
                    HorizontalDivider(color = HermesColors.SurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SkillDetailScreen(
    name: String,
    detail: SkillDetailResponse?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(name, subtitle = stringResource(R.string.agent_skills), onBack = onBack) {
            IconButton(onClick = { confirmDelete = true }, enabled = detail != null) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.skills_delete), tint = HermesColors.Danger)
            }
        }
        if (error != null && detail == null) {
            ServerErrorBox(error, Modifier.padding(16.dp), onRetry = onRetry)
        } else if (isLoading || detail == null) {
            Loading()
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                detail.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
                }
                SelectionContainer {
                    Text(
                        detail.content.ifBlank { stringResource(R.string.skills_empty_file) },
                        style = CodeStyle,
                        color = HermesColors.OnSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HermesColors.Background, RoundedCornerShape(8.dp))
                            .border(1.dp, HermesColors.Outline, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.skills_delete_title, name),
            message = stringResource(R.string.skills_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

package com.example.hermes.features.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.example.hermes.core.model.WorkspaceInfo
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.EmptyState
import com.example.hermes.ui.components.ListRow
import com.example.hermes.ui.components.ScreenHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(
    profileName: String,
    workspaces: List<WorkspaceInfo>,
    activePath: String?,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onAdd: (path: String, name: String?) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<WorkspaceInfo?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(R.string.agent_workspaces),
            subtitle = stringResource(R.string.breadcrumb_agent_profile, profileName),
            onBack = onBack,
        ) {
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.workspaces_add))
            }
        }
        when {
            isLoading && workspaces.isEmpty() -> Loading()
            workspaces.isEmpty() -> EmptyState(
                Icons.Outlined.Folder,
                stringResource(R.string.workspaces_empty),
                actionLabel = stringResource(R.string.workspaces_add),
                onAction = { adding = true },
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(workspaces) { ws ->
                    val active = ws.path == activePath
                    ListRow(
                        ws.name,
                        subtitle = if (active) stringResource(R.string.workspaces_active, ws.path) else ws.path,
                        subtitleColor = if (active) HermesColors.OnSuccessContainer else HermesColors.OnSurfaceVariant,
                        icon = if (active) Icons.Filled.CheckCircle else Icons.Outlined.Folder,
                        onClick = { onSelect(ws.path) },
                        trailing = {
                            IconButton(onClick = { toRemove = ws }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.workspaces_remove, ws.name), tint = HermesColors.OnSurfaceVariant)
                            }
                        },
                    )
                    HorizontalDivider(color = HermesColors.SurfaceVariant)
                }
            }
        }
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = HermesColors.Surface) {
            AddWorkspaceForm(
                onAdd = { path, name ->
                    adding = false
                    onAdd(path, name)
                },
            )
        }
    }
    toRemove?.let { ws ->
        ConfirmDialog(
            title = stringResource(R.string.workspaces_remove_title, ws.name),
            message = stringResource(R.string.workspaces_remove_message),
            confirmLabel = stringResource(R.string.workspaces_remove_confirm),
            destructive = true,
            onConfirm = {
                toRemove = null
                onRemove(ws.path)
            },
            onDismiss = { toRemove = null },
        )
    }
}

@Composable
private fun AddWorkspaceForm(onAdd: (path: String, name: String?) -> Unit) {
    var path by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val error = workspacePathError(path)
    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.workspaces_add), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text(stringResource(R.string.workspaces_path)) },
            placeholder = { Text(stringResource(R.string.agent_workspace_mon_projet)) },
            isError = path.isNotEmpty() && error != null,
            supportingText = {
                if (path.isNotEmpty() && error == WorkspacePathError.NotAbsolute) Text(stringResource(R.string.workspaces_path_absolute))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.workspaces_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            stringResource(R.string.workspaces_add_confirm),
            onClick = { onAdd(path.trim(), name.trim().ifBlank { null }) },
            enabled = error == null,
            disabledReason = stringResource(
                if (error == WorkspacePathError.NotAbsolute) R.string.workspaces_path_absolute else R.string.workspaces_path_required
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

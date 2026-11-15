package com.example.hermes.features.workspace.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import kotlinx.coroutines.launch

private data class PendingFileAction(val target: DocumentLocation, val revision: String, val delete: Boolean)

@Composable
fun FilesScreen(viewModel: FilesViewModel, profiles: List<String>, initialProfile: String) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<PendingFileAction?>(null) }
    var name by remember { mutableStateOf("") }
    var download by remember { mutableStateOf<DocumentLocation?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val target = download
        download = null
        if (uri != null && target != null) viewModel.download(target) { context.contentResolver.openOutputStream(uri) }
    }
    LaunchedEffect(Unit) { if (state.profile.isBlank()) viewModel.openProfile(initialProfile) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextButton(enabled = !state.busy, onClick = { menu = true }) { Text(state.profile.ifBlank { initialProfile }) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                profiles.distinct().forEach { profile ->
                    DropdownMenuItem(text = { Text(profile) }, onClick = { menu = false; viewModel.openProfile(profile) })
                }
            }
        }
        Row {
            state.spaces.forEach { space ->
                FilterChip(selected = state.space == space.id, onClick = { viewModel.navigate(".", space.id) },
                    enabled = !state.busy, label = { Text(space.name) })
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(state.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row {
            TextButton(enabled = !state.busy && state.path != ".", onClick = {
                viewModel.navigate(state.path.substringBeforeLast('/', "."))
            }) { Text(stringResource(R.string.files_parent)) }
            TextButton(enabled = !state.busy && state.space.isNotBlank(), onClick = viewModel::reload) { Text(stringResource(R.string.files_refresh)) }
            TextButton(enabled = !state.busy && state.space.isNotBlank(), onClick = { name = ""; create = true }) { Text(stringResource(R.string.files_create)) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it) }
        if (!state.busy && state.spaces.isEmpty()) Text(stringResource(R.string.files_no_space))
        if (!state.busy && state.space.isNotBlank() && state.entries.isEmpty()) Text(stringResource(R.string.files_empty))
        LazyColumn(Modifier.weight(1f)) {
            items(state.entries, key = { it.name }) { file ->
                Column(Modifier.fillMaxWidth().testTag("document-row-${file.name}").padding(vertical = 8.dp)) {
                    Text(if (file.isDir) "📁 ${file.name}" else file.name, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(enabled = !state.busy) {
                            if (file.isDir) viewModel.navigate(viewModel.child(file.name)) else viewModel.edit(file.name)
                        })
                    if (!file.isDir) {
                        Row {
                            TextButton(enabled = !state.busy, onClick = {
                                download = viewModel.downloadLocation(file.name); saveFile.launch(file.name)
                            }) { Text(stringResource(R.string.files_download)) }
                            TextButton(enabled = !state.busy, onClick = {
                                scope.launch {
                                    try { val (target, revision) = viewModel.prepare(file.name); name = file.name; action = PendingFileAction(target, revision, false) }
                                    catch (e: Exception) { viewModel.reportError(e.localizedMessage.orEmpty()) }
                                }
                            }) { Text(stringResource(R.string.files_rename)) }
                            TextButton(enabled = !state.busy, onClick = {
                                scope.launch {
                                    try { val (target, revision) = viewModel.prepare(file.name); action = PendingFileAction(target, revision, true) }
                                    catch (e: Exception) { viewModel.reportError(e.localizedMessage.orEmpty()) }
                                }
                            }) { Text(stringResource(R.string.files_delete)) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (create || action != null) {
        val pending = action
        AlertDialog(onDismissRequest = { if (!state.busy) { create = false; action = null } },
            title = { Text(stringResource(if (pending?.delete == true) R.string.files_delete else if (create) R.string.files_create else R.string.files_rename)) },
            text = {
                if (pending?.delete == true) Text(stringResource(R.string.files_confirm_delete, pending.target.path))
                else OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.files_name)) })
            },
            confirmButton = { TextButton(enabled = !state.busy && (pending?.delete == true || name.isNotBlank()), onClick = {
                when { create -> viewModel.create(name)
                    pending?.delete == true -> viewModel.delete(pending.target, pending.revision)
                    pending != null -> viewModel.rename(pending.target, pending.revision, name) }
                create = false; action = null
            }) { Text(stringResource(R.string.files_confirm)) } },
            dismissButton = { TextButton(onClick = { create = false; action = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    state.editor?.let { editor ->
        var content by remember(editor) { mutableStateOf(editor.document.content) }
        AlertDialog(onDismissRequest = { if (!state.busy) viewModel.closeEditor() },
            title = { Text(editor.location.path) },
            text = {
                Column {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(value = content, onValueChange = { content = it }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 450.dp))
                }
            },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { viewModel.save(content) }) { Text(stringResource(R.string.files_save)) } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = viewModel::closeEditor) { Text(stringResource(R.string.action_cancel)) } })
    }
}

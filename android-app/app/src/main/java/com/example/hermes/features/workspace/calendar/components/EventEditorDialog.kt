package com.example.hermes.features.workspace.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hermes.R
import com.example.hermes.features.workspace.calendar.EventEditorState
import com.example.hermes.features.workspace.eventFormError
import com.example.hermes.theme.*

/** Writes one event of the phone's calendar: title, times of the selected day, place. */
@Composable
fun EventEditorDialog(
    editor: EventEditorState,
    onSave: (title: String, start: String, end: String, location: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(editor.draft.title) }
    var startTime by remember { mutableStateOf(editor.draft.start) }
    var endTime by remember { mutableStateOf(editor.draft.end) }
    var location by remember { mutableStateOf(editor.draft.location) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HermesPrimary,
        unfocusedBorderColor = OnyxBorder,
        focusedLabelColor = HermesPrimary
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxDarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(OnyxBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editor.eventId == null) stringResource(R.string.agenda_nouvel_evenement_rdv) else stringResource(R.string.agenda_modifier_le_creneau),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = HermesTextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = HermesTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.agenda_titre_de_l_evenement)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text(stringResource(R.string.agenda_debut_hh_mm)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text(stringResource(R.string.agenda_fin_hh_mm)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.agenda_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Off with the reason shown, never a silent no-op.
                val formError = eventFormError(title, startTime, endTime) ?: editor.error
                formError?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = HermesWarning, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = HermesTextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(title, startTime, endTime, location) },
                        enabled = eventFormError(title, startTime, endTime) == null,
                        colors = ButtonDefaults.buttonColors(containerColor = HermesPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save), color = OnyxDarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

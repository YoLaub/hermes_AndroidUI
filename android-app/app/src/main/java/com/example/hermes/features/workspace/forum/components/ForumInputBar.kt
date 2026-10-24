package com.example.hermes.features.workspace.forum.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.theme.*

/** The owner's message to the room; @ chips address one member, no mention addresses them all. */
@Composable
fun ForumInputBar(
    inputText: String,
    members: List<String>,
    onInputTextChanged: (String) -> Unit,
    onAppendMention: (String) -> Unit,
    onSendMessage: () -> Unit,
    speakingName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OnyxDarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (speakingName != null) {
            Row(modifier = Modifier.padding(bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = HermesTertiary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.forum_speaking, speakingName),
                    style = MaterialTheme.typography.labelSmall,
                    color = HermesTertiary,
                    fontSize = 12.sp
                )
            }
        }

        if (members.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                items(members) { name ->
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(HermesTertiaryContainer)
                            .clickable { onAppendMention(name) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "@$name", style = MaterialTheme.typography.labelSmall, color = HermesTertiary, fontSize = 12.sp)
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                placeholder = { Text(stringResource(R.string.forum_input_placeholder), fontSize = 13.sp) },
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder,
                    focusedContainerColor = HermesCodeBackground,
                    unfocusedContainerColor = HermesCodeBackground
                )
            )
            FloatingActionButton(
                onClick = { if (speakingName == null) onSendMessage() },
                modifier = Modifier.size(48.dp),
                containerColor = if (speakingName == null) HermesPrimary else OnyxBorder,
                contentColor = OnyxDarkBackground,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.forum_send),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

package com.example.hermes.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AgentAvatar
import com.example.hermes.ui.components.Persona

/** Empty conversation: the active profile's avatar and name, its model, and three starters. */
@Composable
fun ChatEmptyState(persona: Persona, skillCount: Int, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        Icons.Outlined.AccountTree to stringResource(R.string.chat_suggestion_architecture),
        Icons.Outlined.FolderOpen to stringResource(R.string.chat_suggestion_files),
        Icons.Outlined.Terminal to stringResource(R.string.chat_suggestion_git),
    )
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AgentAvatar(persona, 56.dp)
            Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(
                    persona.name,
                    persona.model,
                    skillCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.chat_skill_count, it, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = HermesColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { (icon, text) ->
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .background(HermesColors.Surface, shape)
                        .border(1.dp, HermesColors.Outline, shape)
                        .clickable { onSuggestion(text) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = HermesColors.Primary, modifier = Modifier.size(20.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

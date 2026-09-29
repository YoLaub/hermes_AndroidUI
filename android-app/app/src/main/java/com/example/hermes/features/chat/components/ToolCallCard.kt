package com.example.hermes.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ToolCall
import com.example.hermes.theme.*

@Composable
fun ToolCallCard(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        toolCall.name.contains("command") || toolCall.name.contains("terminal") -> Icons.Default.Terminal
        toolCall.name.contains("file") -> Icons.Default.Description
        toolCall.name.contains("search") || toolCall.name.contains("web") -> Icons.Default.Search
        toolCall.name.contains("git") -> Icons.Default.AccountTree
        else -> Icons.Default.Build
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = HermesToolBackground),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (toolCall.isError) HermesError.copy(alpha = 0.5f) else OnyxBorder
            )
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tool Icon badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HermesCodeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = HermesPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tool Name
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // Status & Duration
                if (toolCall.duration != null) {
                    Text(
                        text = "%.2fs".format(toolCall.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = HermesTextMuted,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                if (toolCall.isError) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = HermesError,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (toolCall.output != null || toolCall.duration != null) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = HermesSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = HermesPrimary
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = HermesTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Preview preview snippet
            toolCall.args?.toString()?.let { argsStr ->
                Text(
                    text = argsStr.take(120),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = HermesTextMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp, start = 36.dp)
                )
            }

            // Expanded Details (Args & Output)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HermesCodeBackground)
                        .padding(8.dp)
                ) {
                    toolCall.args?.let { args ->
                        Text(
                            text = "Arguments:",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary
                        )
                        Text(
                            text = args.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = HermesPrimary
                        )
                    }

                    toolCall.output?.let { output ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Output:",
                            style = MaterialTheme.typography.labelSmall,
                            color = HermesTextSecondary
                        )
                        Text(
                            text = output.take(500),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = if (toolCall.isError) HermesError else HermesTextPrimary
                        )
                    }
                }
            }
        }
    }
}

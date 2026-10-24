package com.example.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

/** A titled group of rows, as on the Agent and Réglages screens. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = HermesColors.OnSurfaceVariant)
        Column(
            Modifier
                .fillMaxWidth()
                .background(HermesColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, HermesColors.Outline, RoundedCornerShape(12.dp)),
            content = content,
        )
    }
}

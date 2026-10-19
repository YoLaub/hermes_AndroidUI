package com.example.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

enum class CardVariant { Default, Selected, Decision }

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: CardVariant = CardVariant.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val (background, border, width) = when (variant) {
        CardVariant.Default -> Triple(HermesColors.Surface, HermesColors.Outline, 1.dp)
        CardVariant.Selected -> Triple(HermesColors.PrimaryContainer, HermesColors.Primary, 1.5.dp)
        CardVariant.Decision -> Triple(HermesColors.Surface, HermesColors.Warning, 1.5.dp)
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(width, border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

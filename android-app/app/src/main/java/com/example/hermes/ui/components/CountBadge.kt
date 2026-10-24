package com.example.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

/** Text of a count badge: nothing at zero, capped at 99+. */
fun countBadgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

enum class BadgeTone {
    /** Red: something waits for a decision. */
    Decision,
    /** Grey: a plain tally (columns, tabs). */
    Tally,
}

/** Number badge. Callers set the spoken label on the element it decorates. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, tone: BadgeTone = BadgeTone.Decision) {
    val text = countBadgeText(count) ?: return
    val shape = RoundedCornerShape(10.dp)
    val base = modifier
        .height(20.dp)
        .defaultMinSize(minWidth = 20.dp)
    val styled = when (tone) {
        BadgeTone.Decision -> base.background(HermesColors.Danger, shape)
        BadgeTone.Tally -> base.background(HermesColors.SurfaceVariant, shape).border(1.dp, HermesColors.Outline, shape)
    }
    Box(styled.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (tone == BadgeTone.Decision) FontWeight.Bold else FontWeight.SemiBold,
            color = if (tone == BadgeTone.Decision) HermesColors.OnPrimary else HermesColors.OnSurface,
        )
    }
}

/** Small dot, e.g. "a control session is live". */
@Composable
fun DotBadge(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = HermesColors.Danger) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

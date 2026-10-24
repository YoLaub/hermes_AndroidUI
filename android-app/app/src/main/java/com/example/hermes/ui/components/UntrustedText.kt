package com.example.hermes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors

/**
 * Text the agent declared and nobody checked (an action's label or target). It is shown as plain
 * characters: a String in a Text, so no Markdown, no autolink, no click. Keep it that way.
 */
@Composable
fun UntrustedText(text: String, modifier: Modifier = Modifier, label: String = stringResource(R.string.untrusted_target_label)) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text,
            style = CodeStyle,
            color = HermesColors.OnSurface,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = HermesColors.OutlineStrong,
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    )
                }
                .padding(10.dp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = HermesColors.Meta)
    }
}

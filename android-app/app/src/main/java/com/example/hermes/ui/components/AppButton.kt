package com.example.hermes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.hermes.theme.HermesColors

enum class ButtonVariant { Primary, Secondary, Text, Destructive, DestructiveSecondary }

/**
 * The app's button. A disabled button says why under itself ([disabledReason]), so a greyed-out
 * action is never a dead end. [loading] keeps the label and shows a spinner.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    disabledReason: String? = null,
) {
    val clickable = enabled && !loading
    // The column takes the caller's width (e.g. fillMaxWidth) and the button fills it; by default both wrap the label.
    Column(modifier.width(IntrinsicSize.Max)) {
        val inner = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
        val padding = PaddingValues(horizontal = if (variant == ButtonVariant.Text) 16.dp else 20.dp)
        val content: @Composable () -> Unit = {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColorFor(variant))
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
        when (variant) {
            ButtonVariant.Primary -> Button(
                onClick = onClick, enabled = clickable, modifier = inner, contentPadding = padding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HermesColors.Primary, contentColor = HermesColors.OnPrimary,
                    disabledContainerColor = HermesColors.SurfaceVariant, disabledContentColor = HermesColors.Disabled,
                ),
            ) { content() }
            ButtonVariant.Destructive -> Button(
                onClick = onClick, enabled = clickable, modifier = inner, contentPadding = padding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HermesColors.DangerFill, contentColor = Color.White,
                    disabledContainerColor = HermesColors.SurfaceVariant, disabledContentColor = HermesColors.Disabled,
                ),
            ) { content() }
            ButtonVariant.Secondary -> OutlinedButton(
                onClick = onClick, enabled = clickable, modifier = inner, contentPadding = padding,
                border = BorderStroke(1.dp, if (clickable) HermesColors.OutlineStrong else HermesColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesColors.OnSurface, disabledContentColor = HermesColors.Disabled),
            ) { content() }
            ButtonVariant.DestructiveSecondary -> OutlinedButton(
                onClick = onClick, enabled = clickable, modifier = inner, contentPadding = padding,
                border = BorderStroke(1.dp, if (clickable) HermesColors.Danger else HermesColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HermesColors.OnDangerContainer, disabledContentColor = HermesColors.Disabled),
            ) { content() }
            ButtonVariant.Text -> TextButton(
                onClick = onClick, enabled = clickable, modifier = inner, contentPadding = padding,
                colors = ButtonDefaults.textButtonColors(contentColor = HermesColors.Primary, disabledContentColor = HermesColors.Disabled),
            ) { content() }
        }
        if (!enabled && disabledReason != null) {
            Text(
                disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = HermesColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun LocalContentColorFor(variant: ButtonVariant): Color = when (variant) {
    ButtonVariant.Primary -> HermesColors.OnPrimary
    ButtonVariant.Destructive -> Color.White
    ButtonVariant.Secondary -> HermesColors.OnSurface
    ButtonVariant.DestructiveSecondary -> HermesColors.OnDangerContainer
    ButtonVariant.Text -> HermesColors.Primary
}

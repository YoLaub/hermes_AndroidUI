package com.example.hermes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors

/**
 * Confirmation only. The action sits on the right; [destructive] paints it with `dangerFill`.
 * [content] holds a recap of what is about to happen, under [message].
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.action_cancel),
    content: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = if (message == null && content == null) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
                    content?.invoke()
                }
            }
        },
        dismissButton = { AppButton(dismissLabel, onClick = onDismiss, variant = ButtonVariant.Text) },
        confirmButton = {
            AppButton(
                confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
            )
        },
    )
}

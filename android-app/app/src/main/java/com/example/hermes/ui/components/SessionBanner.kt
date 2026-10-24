package com.example.hermes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import kotlinx.coroutines.delay

/** "HH:mm:ss" since the start; hours keep counting past a day. */
fun formatElapsed(elapsedMs: Long): String {
    val total = maxOf(0L, elapsedMs) / 1000
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/**
 * Global banner while the agent controls the phone: above every tab, warning colour, a running
 * clock and Arrêter. Tapping the text opens the Contrôle tab. Arrêter asks [StopControlDialog].
 */
@Composable
fun SessionBanner(startedAtMs: Long, onStop: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(HermesColors.Warning)
            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(HermesColors.Background, CircleShape))
        Text(
            stringResource(R.string.session_banner_text, formatElapsed(now - startedAtMs)),
            style = MaterialTheme.typography.labelLarge,
            color = HermesColors.Background,
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClickLabel = stringResource(R.string.session_banner_open), onClick = onOpen)
                .padding(vertical = 14.dp),
        )
        Button(
            onClick = onStop,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HermesColors.Background, contentColor = Color.White),
        ) { Text(stringResource(R.string.session_stop), fontWeight = FontWeight.Bold) }
    }
}

/** Short stop confirmation: Arrêter is the default action, two taps at most from anywhere. */
@Composable
fun StopControlDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.session_stop_title),
        message = stringResource(R.string.session_stop_message),
        confirmLabel = stringResource(R.string.session_stop),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

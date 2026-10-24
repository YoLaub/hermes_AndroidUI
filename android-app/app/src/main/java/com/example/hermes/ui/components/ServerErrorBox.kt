package com.example.hermes.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.CodeStyle
import com.example.hermes.theme.HermesColors

enum class ServerErrorKind(@StringRes val title: Int) {
    Network(R.string.error_network),
    Unauthorized(R.string.error_unauthorized),
    Forbidden(R.string.error_forbidden),
    NotFound(R.string.error_not_found),
    Rejected(R.string.error_rejected),
    Upstream(R.string.error_upstream),
    Server(R.string.error_server),
    Other(R.string.error_other),
}

fun serverErrorKind(status: Int?): ServerErrorKind = when (status) {
    null -> ServerErrorKind.Network
    401 -> ServerErrorKind.Unauthorized
    403 -> ServerErrorKind.Forbidden
    404 -> ServerErrorKind.NotFound
    400, 422 -> ServerErrorKind.Rejected
    502, 503, 504 -> ServerErrorKind.Upstream
    in 500..599 -> ServerErrorKind.Server
    else -> ServerErrorKind.Other
}

private val statusPrefix = Regex("""^\s*(?:HTTP\s+)?([1-5][0-9]{2})\b""")

/** HTTP status at the start of a raw error ("HTTP 422: …", "502 Bad Gateway"), if any. */
fun httpStatusOf(raw: String): Int? = statusPrefix.find(raw)?.groupValues?.get(1)?.toInt()

/**
 * A server error shown as it came: translated title, raw message in mono and selectable.
 * Nothing in [raw] is rewritten, so a 422 reads exactly as the server sent it.
 */
@Composable
fun ServerErrorBox(
    raw: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val kind = serverErrorKind(httpStatusOf(raw))
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(HermesColors.DangerContainer, shape)
            .border(1.dp, HermesColors.Danger.copy(alpha = 0.5f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = HermesColors.OnDangerContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(kind.title), style = MaterialTheme.typography.titleSmall, color = HermesColors.OnDangerContainer)
        }
        SelectionContainer {
            Text(
                raw,
                style = CodeStyle,
                color = HermesColors.OnSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HermesColors.Background.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onRetry != null) {
                AppButton(stringResource(R.string.action_retry), onClick = onRetry, variant = ButtonVariant.Secondary)
            }
            actions()
        }
    }
}

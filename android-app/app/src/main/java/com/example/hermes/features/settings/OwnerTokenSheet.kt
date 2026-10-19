package com.example.hermes.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.theme.HermesColors
import com.example.hermes.ui.components.AppButton

/**
 * Enters a new owner token. The stored one is never shown or prefilled: the field starts empty
 * and is masked, and the text is dropped once handed to [onSave].
 */
@Composable
fun OwnerTokenSheet(hasToken: Boolean, onSave: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (hasToken) R.string.owner_token_replace_title else R.string.owner_token_enter_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(R.string.owner_token_help), style = MaterialTheme.typography.bodyMedium, color = HermesColors.OnSurfaceVariant)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.owner_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AppButton(
            stringResource(R.string.action_save),
            onClick = {
                val value = token.trim()
                token = ""
                onSave(value)
            },
            enabled = token.isNotBlank(),
            disabledReason = stringResource(R.string.owner_token_required),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

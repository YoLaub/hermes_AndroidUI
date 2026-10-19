package com.example.hermes.features.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hermes.R
import com.example.hermes.core.model.MemoryProfile
import com.example.hermes.core.model.MemoryProfileCreated
import com.example.hermes.features.profiles.NewMandateCard
import com.example.hermes.features.profiles.NewProfileCard
import com.example.hermes.features.profiles.OneTimeTokenDialog
import com.example.hermes.ui.components.AlertBanner
import com.example.hermes.ui.components.AlertTone
import com.example.hermes.ui.components.ScreenHeader

/** New memory profile. Its token appears once, in a secure window, then the screen closes. */
@Composable
fun NewMemoryProfileScreen(
    isLoading: Boolean,
    error: String?,
    created: MemoryProfileCreated?,
    onCreate: (name: String, read: List<String>, write: List<String>) -> Unit,
    onTokenSeen: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.guard_new_profile), subtitle = stringResource(R.string.guard_title), onBack = onBack)
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { AlertBanner(it, tone = AlertTone.Blocking, title = stringResource(R.string.guard_error)) }
            NewProfileCard(enabled = !isLoading, onCreate = onCreate)
        }
    }
    created?.let { OneTimeTokenDialog(it, onDone = onTokenSeen) }
}

/** New mandate. A refusal keeps the form as typed and shows the server's reason above it. */
@Composable
fun NewMandateScreen(
    profiles: List<MemoryProfile>,
    isLoading: Boolean,
    error: String?,
    onCreate: (profile: String, label: String, hosts: List<String>, folders: List<String>, expiresAt: String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.guard_new_mandate), subtitle = stringResource(R.string.guard_title), onBack = onBack)
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { AlertBanner(it, tone = AlertTone.Blocking, title = stringResource(R.string.guard_error)) }
            NewMandateCard(profiles = profiles.filter { it.state == "active" }, enabled = !isLoading, onCreate = onCreate)
        }
    }
}

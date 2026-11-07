package com.example.hermes.lite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.hermes.R
import com.example.hermes.core.accessibility.HermesAccessibilityService
import com.example.hermes.core.mobilecontrol.AgentPhoneStatus
import com.example.hermes.core.mobilecontrol.agentPhoneStatus
import com.example.hermes.theme.HermesTheme

/** The whole app of the agent-phone edition: pair, enable accessibility, grant permissions, stop. No chat. */
class LiteActivity : ComponentActivity() {
    private val viewModel: LiteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermesTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LiteScreen(viewModel)
                }
            }
        }
    }
}

/** What the agent may need on the phone: calendar, SMS, call log, calls, contacts, notifications. */
private fun neededPermissions(): List<String> = buildList {
    add(Manifest.permission.READ_CALENDAR)
    add(Manifest.permission.WRITE_CALENDAR)
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
private fun LiteScreen(viewModel: LiteViewModel) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    val authenticated by viewModel.authenticated.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val stopped by viewModel.stopped.collectAsState()

    val permissions = remember { neededPermissions() }
    var accessibilityOn by remember { mutableStateOf(HermesAccessibilityService.isRunning()) }
    var granted by remember { mutableIntStateOf(0) }
    fun refresh() {
        accessibilityOn = HermesAccessibilityService.isRunning()
        granted = permissions.count { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    val status = agentPhoneStatus(ui.paired, stopped, authenticated, accessibilityOn)
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.lite_title), style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when (status) {
                            AgentPhoneStatus.NOT_PAIRED -> R.string.lite_status_not_paired
                            AgentPhoneStatus.STOPPED -> R.string.lite_status_stopped
                            AgentPhoneStatus.OFFLINE -> R.string.lite_status_offline
                            AgentPhoneStatus.ACCESSIBILITY_OFF -> R.string.lite_status_accessibility_off
                            AgentPhoneStatus.READY -> R.string.lite_status_ready
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                if (activeSession != null && status == AgentPhoneStatus.READY) {
                    Text(stringResource(R.string.lite_agent_active), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        if (status == AgentPhoneStatus.NOT_PAIRED) {
            OutlinedTextField(
                value = ui.relayUrl,
                onValueChange = viewModel::onRelayUrlChanged,
                label = { Text(stringResource(R.string.lite_relay_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.lite_pairing_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.pair(code) },
                enabled = code.isNotBlank() && !ui.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.lite_pair)) }
            Text(stringResource(R.string.lite_pairing_help), style = MaterialTheme.typography.bodySmall)
        } else {
            if (stopped) {
                Button(onClick = viewModel::resume, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text(stringResource(R.string.lite_resume))
                }
            } else {
                Button(
                    onClick = viewModel::stop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) { Text(stringResource(R.string.lite_stop)) }
            }

            if (!accessibilityOn) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.lite_enable_accessibility)) }
            }

            Text(stringResource(R.string.lite_permissions_count, granted, permissions.size), style = MaterialTheme.typography.bodyMedium)
            if (granted < permissions.size) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(permissions.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.lite_grant_permissions)) }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::unpair) { Text(stringResource(R.string.lite_unpair)) }
        }
    }
}

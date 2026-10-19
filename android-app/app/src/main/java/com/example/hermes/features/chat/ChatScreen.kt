package com.example.hermes.features.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.core.model.ChatMessage
import com.example.hermes.core.model.ProfileInfo
import com.example.hermes.R
import androidx.compose.ui.res.stringResource
import com.example.hermes.ui.components.AlertBanner
import com.example.hermes.ui.components.AlertTone
import com.example.hermes.ui.components.ConfirmDialog
import com.example.hermes.ui.components.personaOf
import com.example.hermes.features.chat.components.*
import com.example.hermes.features.sessions.SessionsDrawerContent
import com.example.hermes.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    tools: AgentToolsState,
    snackbarHostState: SnackbarHostState,
    onOpenAgent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sessionsDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // What to run once the user confirms enabling auto-approval (shield, or "YOLO" on an approval card).
    var pendingYoloEnable by remember { mutableStateOf<(() -> Unit)?>(null) }


    // Detect if user is currently near the bottom of the conversation
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf true
            val lastVisible = visibleItems.last()
            val totalItems = layoutInfo.totalItemsCount
            lastVisible.index >= totalItems - 2
        }
    }

    // Auto-scroll on new message added (e.g. user sends message or session changes)
    LaunchedEffect(state.messages.size) {
        try {
            val totalItems = state.messages.size + (if (state.isStreaming) 1 else 0)
            if (totalItems > 0) {
                listState.scrollToItem(totalItems - 1, scrollOffset = 100000)
            }
        } catch (_: Exception) {
            // Ignore transient scroll races
        }
    }

    // Continuously follow the bottom of the streaming message, allowing manual scroll-up
    LaunchedEffect(
        state.streamingTokens.length,
        state.streamingReasoning.length,
        state.streamingToolCalls.size,
        state.isStreaming
    ) {
        if (state.isStreaming && isNearBottom && !listState.isScrollInProgress) {
            try {
                val totalItems = state.messages.size + 1
                if (totalItems > 0) {
                    listState.scrollToItem(totalItems - 1, scrollOffset = 100000)
                }
            } catch (_: Exception) {
                // Ignore transient scroll races
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = sessionsDrawerState,
        drawerContent = {
            SessionsDrawerContent(
                sessions = state.sessions,
                currentSessionId = state.sessionId,
                onSelectSession = { sid ->
                    viewModel.selectSession(sid)
                    scope.launch { sessionsDrawerState.close() }
                },
                onNewSession = {
                    viewModel.createNewSession()
                    scope.launch { sessionsDrawerState.close() }
                },
                onDeleteSession = { sid -> viewModel.deleteSession(sid) },
                onDismiss = { scope.launch { sessionsDrawerState.close() } },
                showAllProfiles = state.showAllProfiles,
                otherProfileCount = state.otherProfileCount,
                onToggleAllProfiles = { viewModel.toggleShowAllProfiles() },
                searchResults = state.searchResults,
                isSearchingSessions = state.isSearchingSessions,
                onSearchServer = { query -> viewModel.searchSessionsOnServer(query) },
                onClearSearch = { viewModel.clearServerSearch() },
                isRepairingSessions = state.isRepairingSessions,
                onRepairSessions = {
                    viewModel.repairSessions { msg ->
                        scope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = chatSessionTitle(state.sessions, state.sessionId),
                    profileName = profileDisplayName(state.activeProfile),
                    isStreaming = state.isStreaming,
                    yoloEnabled = state.yoloEnabled,
                    canDeleteSession = state.sessionId != null,
                    onOpenDrawer = { scope.launch { sessionsDrawerState.open() } },
                    onOpenProfiles = { tools.profilesSheet = true },
                    onToggleYolo = {
                        when (yoloTap(state.yoloEnabled)) {
                            YoloTap.Disable -> viewModel.toggleYolo()
                            YoloTap.AskToEnable -> pendingYoloEnable = { viewModel.toggleYolo() }
                        }
                    },
                    onNewSession = { viewModel.createNewSession() },
                    onDeleteSession = { state.sessionId?.let { viewModel.deleteSession(it) } },
                    onOpenAgentSettings = onOpenAgent,
                )
            },
            bottomBar = {
                ChatInputField(
                    inputText = state.inputText,
                    profileName = profileDisplayName(state.activeProfile),
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    onSendMessage = { viewModel.sendMessage() },
                    onCancelStream = { viewModel.cancelStream() },
                    isStreaming = state.isStreaming,
                    pendingAttachments = state.pendingAttachments,
                    availableCommands = state.commands,
                    onRemoveAttachment = { viewModel.removeAttachment(it) },
                    onCommandSelected = { cmd ->
                        if (cmd.equals("/restart", ignoreCase = true)) {
                            viewModel.restartGateway()
                        } else {
                            viewModel.onInputTextChanged(cmd)
                        }
                    }
                )
            },
            containerColor = OnyxDarkBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Stays while auto-approval is on, with a one-tap way out.
                if (state.yoloEnabled) {
                    AlertBanner(
                        title = stringResource(R.string.yolo_banner_title),
                        text = stringResource(R.string.yolo_banner_text),
                        tone = AlertTone.Warning,
                        actionLabel = stringResource(R.string.yolo_disable),
                        onAction = { viewModel.toggleYolo() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Warning / Compressing inline notice
                AnimatedVisibility(visible = state.compressingNotice != null || state.warningNotice != null) {
                    val notice = state.compressingNotice ?: state.warningNotice ?: ""
                    Surface(
                        color = HermesWarningContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = HermesWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesWarning
                            )
                        }
                    }
                }

                // Error Banner
                AnimatedVisibility(visible = state.error != null) {
                    Surface(
                        color = HermesErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = HermesError,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesError,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Messages List
                if (state.messages.isEmpty() && !state.isStreaming && !state.isLoading) {
                    val activeInfo = state.profiles.firstOrNull { it.name == state.activeProfile }
                    ChatEmptyState(
                        persona = personaOf(activeInfo ?: ProfileInfo(name = state.activeProfile)),
                        skillCount = activeInfo?.skillCount ?: 0,
                        onSuggestion = { viewModel.onInputTextChanged(it) },
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                    ) {
                        items(state.messages) { msg ->
                            MessageBubble(message = msg)
                        }

                        // Live Streaming Message
                        if (state.isStreaming) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    val liveMsg = ChatMessage(
                                        role = "assistant",
                                        content = state.streamingTokens,
                                        timestamp = System.currentTimeMillis() / 1000.0,
                                        toolCalls = state.streamingToolCalls.ifEmpty { null },
                                        reasoning = state.streamingReasoning.ifBlank { null }
                                    )
                                    MessageBubble(message = liveMsg)

                                    StreamingIndicator(
                                        statusText = if (state.streamingToolCalls.any { it.output == null && !it.isError }) {
                                            "Executing tool..."
                                        } else if (state.streamingReasoning.isNotBlank() && state.streamingTokens.isBlank()) {
                                            "Reasoning..."
                                        } else {
                                            "Generating response..."
                                        }
                                    )
                                }
                            }
                        }

                        // Interactive Approval Card
                        state.pendingApproval?.let { appr ->
                            item {
                                ApprovalCard(
                                    approval = appr,
                                    onRespond = { approved -> viewModel.respondApproval(approved) },
                                    onApproveAndYolo = { pendingYoloEnable = { viewModel.enableYoloAndApprove(appr.id) } }
                                )
                            }
                        }

                        // Interactive Clarify Card
                        state.pendingClarify?.let { clar ->
                            item {
                                ClarifyCard(
                                    clarify = clar,
                                    onSelectOption = { answer -> viewModel.respondClarify(answer) }
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    pendingYoloEnable?.let { enable ->
        ConfirmDialog(
            title = stringResource(R.string.yolo_confirm_title),
            message = stringResource(R.string.yolo_confirm_message),
            confirmLabel = stringResource(R.string.yolo_confirm),
            destructive = true,
            onConfirm = {
                pendingYoloEnable = null
                enable()
            },
            onDismiss = { pendingYoloEnable = null }
        )
    }
}

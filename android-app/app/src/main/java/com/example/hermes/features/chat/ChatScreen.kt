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
    onNavigateToKanban: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    onNavigateToMobileControl: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sessionsDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)


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
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(onClick = { scope.launch { sessionsDrawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Conversations",
                                    tint = HermesTextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isStreaming) HermesWarning else HermesSecondary)
                            )
                        }
                    },
                    actions = {
                        // Profile Switcher Chip / Avatar
                        AssistChip(
                            onClick = { tools.profilesSheet = true },
                            label = {
                                Text(
                                    text = state.activeProfile.replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(HermesPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = state.activeProfile.take(1).uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = OnyxDarkSurfaceVariant,
                                labelColor = HermesTextPrimary
                            ),
                            border = BorderStroke(1.dp, OnyxBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // YOLO Mode Quick Toggle Pill
                        AssistChip(
                            onClick = { viewModel.toggleYolo() },
                            label = {
                                Text(
                                    text = "YOLO",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (state.yoloEnabled) Color.Black else HermesPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (state.yoloEnabled) HermesPrimary else OnyxDarkSurfaceVariant,
                                labelColor = if (state.yoloEnabled) Color.Black else HermesTextSecondary
                            ),
                            border = BorderStroke(1.dp, if (state.yoloEnabled) HermesPrimary else OnyxBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // New Chat
                        IconButton(
                            onClick = { viewModel.createNewSession() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddComment,
                                contentDescription = "New Chat",
                                tint = HermesPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Kanban Board
                        IconButton(
                            onClick = onNavigateToKanban,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewKanban,
                                contentDescription = "Tableau Kanban",
                                tint = HermesPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Zone Commune (Notes & Audio, Calendrier, Forum)
                        IconButton(
                            onClick = onNavigateToWorkspace,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Zone Commune (Notes, Audio, Forum)",
                                tint = HermesTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Contrôle d'applications Android par Hermes
                        IconButton(
                            onClick = onNavigateToMobileControl,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Contrôle par Hermes",
                                tint = HermesPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Settings / Server Config & Tools
                        IconButton(
                            onClick = { tools.settingsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Paramètres & Outils",
                                tint = HermesTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = OnyxDarkSurface
                    )
                )
            },
            bottomBar = {
                ChatInputField(
                    inputText = state.inputText,
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
                    // Empty State Hero
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(HermesPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = HermesPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "How can Hermes help you today?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Running as profile '${state.activeProfile}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = HermesSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            // Suggestion chips
                            val suggestions = listOf(
                                "Inspect project files and structure",
                                "Run git status and summarize changes",
                                "Explain the architecture of this server"
                            )
                            suggestions.forEach { prompt ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.onInputTextChanged(prompt)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = OnyxDarkSurfaceVariant),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(OnyxBorder)
                                    )
                                ) {
                                    Text(
                                        text = prompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HermesTextPrimary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
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
                                    onApproveAndYolo = { viewModel.enableYoloAndApprove(appr.id) }
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
}

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
import com.example.hermes.features.profiles.*
import com.example.hermes.features.sessions.SessionsDrawerContent
import com.example.hermes.features.settings.SettingsDialog
import com.example.hermes.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToKanban: () -> Unit = {},
    onNavigateToWorkspace: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sessionsDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showProfilesSheet by remember { mutableStateOf(false) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var showProfileEnvDialog by remember { mutableStateOf(false) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showWorkspaceDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to bottom on new message or when streaming begins/ends
    LaunchedEffect(state.messages.size, state.isStreaming) {
        val totalItems = state.messages.size + (if (state.isStreaming) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    // Fast non-animated scroll when streaming progresses, only if user is already near bottom
    LaunchedEffect(state.streamingTokens.length / 50) {
        if (state.isStreaming) {
            val totalItems = state.messages.size + 1
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= totalItems - 2) {
                listState.scrollToItem(totalItems - 1)
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.sessionTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HermesTextPrimary,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isStreaming) HermesWarning else HermesSecondary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (state.isStreaming) "Agent running" else "Connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HermesTextMuted
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { sessionsDrawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Conversations",
                                tint = HermesTextPrimary
                            )
                        }
                    },
                    actions = {
                        // Profile Switcher Chip / Avatar
                        AssistChip(
                            onClick = { showProfilesSheet = true },
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
                                        fontSize = 10.sp,
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
                                    fontSize = 11.sp
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

                        // Settings / Server Config & Tools
                        IconButton(
                            onClick = { showSettingsDialog = true },
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
                    onRemoveAttachment = { viewModel.removeAttachment(it) }
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

        // Profiles Bottom Sheet
        if (showProfilesSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProfilesSheet = false },
                containerColor = OnyxDarkSurface
            ) {
                ProfileDrawerContent(
                    profiles = state.profiles,
                    activeProfile = state.activeProfile,
                    onSelectProfile = { name -> viewModel.switchProfile(name) },
                    onCreateProfile = {
                        showProfilesSheet = false
                        showCreateProfileDialog = true
                    },
                    onDeleteProfile = { name ->
                        viewModel.deleteProfile(name) { success, err ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (success) "Profil \"$name\" supprimé"
                                    else "Erreur : ${err ?: "Impossible de supprimer"}"
                                )
                            }
                        }
                    },
                    onOpenSkills = {
                        showProfilesSheet = false
                        viewModel.loadSkills()
                        showSkillsDialog = true
                    },
                    onOpenMemory = {
                        showProfilesSheet = false
                        viewModel.loadMemory()
                        showMemoryDialog = true
                    },
                    onOpenWorkspaces = {
                        showProfilesSheet = false
                        viewModel.loadWorkspaces()
                        showWorkspaceDialog = true
                    },
                    onOpenEnv = {
                        showProfilesSheet = false
                        viewModel.loadProviders()
                        showProfileEnvDialog = true
                    },
                    onDismiss = { showProfilesSheet = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Create Profile Dialog
        if (showCreateProfileDialog) {
            CreateProfileDialog(
                existingProfiles = state.profiles,
                onDismiss = { showCreateProfileDialog = false },
                onCreate = { name, cloneFrom, model, provider, apiKey ->
                    viewModel.createProfile(name, cloneFrom, model, provider, apiKey) { success, err ->
                        if (success) {
                            showCreateProfileDialog = false
                            scope.launch { snackbarHostState.showSnackbar("Profil \"$name\" créé et activé !") }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Erreur : ${err ?: "Échec de création"}") }
                        }
                    }
                }
            )
        }

        // Profile Env Dialog (.env variables & API keys)
        if (showProfileEnvDialog) {
            ProfileEnvDialog(
                activeProfile = state.activeProfile,
                providers = state.providers,
                isLoading = state.isProvidersLoading,
                onSaveKey = { providerId, apiKey ->
                    viewModel.setProviderKey(providerId, apiKey) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Clé $providerId enregistrée dans le .env du profil ${state.activeProfile}"
                                else "Échec de l'enregistrement de la clé"
                            )
                        }
                    }
                },
                onDeleteKey = { providerId ->
                    viewModel.deleteProviderKey(providerId) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Clé $providerId supprimée du .env"
                                else "Échec de la suppression"
                            )
                        }
                    }
                },
                onRefresh = { viewModel.loadProviders() },
                onDismiss = { showProfileEnvDialog = false }
            )
        }

        // Skills Dialog
        if (showSkillsDialog) {
            SkillsDialog(
                profileName = state.activeProfile,
                skills = state.skills,
                isLoading = state.isSkillsLoading,
                selectedSkillDetail = state.selectedSkillDetail,
                onSelectSkill = { name -> viewModel.loadSkillDetail(name) },
                onBackToList = { viewModel.clearSkillDetail() },
                onDeleteSkill = { name -> viewModel.deleteSkill(name) },
                onDismiss = {
                    showSkillsDialog = false
                    viewModel.clearSkillDetail()
                }
            )
        }

        // Memory Dialog
        if (showMemoryDialog) {
            MemoryDialog(
                profileName = state.activeProfile,
                memoryData = state.memoryData,
                isLoading = state.isMemoryLoading,
                onSaveSection = { section, content -> viewModel.saveMemory(section, content) },
                onDismiss = { showMemoryDialog = false }
            )
        }

        // Workspaces Dialog
        if (showWorkspaceDialog) {
            WorkspaceDialog(
                workspaces = state.workspaces,
                activeWorkspace = state.activeWorkspace,
                isLoading = state.isWorkspacesLoading,
                onSelectWorkspace = { path ->
                    viewModel.selectWorkspace(path)
                    showWorkspaceDialog = false
                },
                onAddWorkspace = { path, name -> viewModel.addWorkspace(path, name) },
                onRemoveWorkspace = { path -> viewModel.removeWorkspace(path) },
                onDismiss = { showWorkspaceDialog = false }
            )
        }

        // Settings & Tools Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                serverUrl = state.serverUrl.ifBlank { "Hermes Server" },
                activeProfile = state.activeProfile,
                sessionId = state.sessionId,
                yoloEnabled = state.yoloEnabled,
                onToggleYolo = { viewModel.toggleYolo() },
                onOpenSkills = {
                    showSettingsDialog = false
                    viewModel.loadSkills()
                    showSkillsDialog = true
                },
                onOpenMemory = {
                    showSettingsDialog = false
                    viewModel.loadMemory()
                    showMemoryDialog = true
                },
                onOpenWorkspaces = {
                    showSettingsDialog = false
                    viewModel.loadWorkspaces()
                    showWorkspaceDialog = true
                },
                onOpenProfiles = {
                    showSettingsDialog = false
                    showProfilesSheet = true
                },
                onDisconnect = {
                    showSettingsDialog = false
                    onNavigateToSettings()
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

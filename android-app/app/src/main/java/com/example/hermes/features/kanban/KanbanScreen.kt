package com.example.hermes.features.kanban

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hermes.features.kanban.components.*
import com.example.hermes.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanbanScreen(
    viewModel: KanbanViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showBoardDropdown by remember { mutableStateOf(false) }

    // Display columns (ensure order: triage, todo, ready, running, blocked, done)
    val orderedColumnNames = listOf("triage", "todo", "ready", "running", "blocked", "done")
    val displayColumns = remember(state.columns) {
        val colMap = state.columns.associateBy { it.name.lowercase() }
        orderedColumnNames.map { name ->
            colMap[name] ?: com.example.hermes.core.model.KanbanColumn(name = name)
        }
    }

    val pagerState = rememberPagerState(initialPage = 1) { displayColumns.size }

    // Sync snackbar notifications
    LaunchedEffect(state.messageNotice, state.error) {
        state.messageNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
        state.error?.let {
            snackbarHostState.showSnackbar("Erreur : $it")
            viewModel.clearNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Kanban",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = HermesTextPrimary
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Board Switcher Chip
                        Box {
                            AssistChip(
                                onClick = { showBoardDropdown = true },
                                label = {
                                    Text(
                                        text = state.currentBoard.ifBlank { "default" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = OnyxDarkSurfaceVariant,
                                    labelColor = HermesPrimary
                                ),
                                border = BorderStroke(1.dp, OnyxBorder)
                            )

                            DropdownMenu(
                                expanded = showBoardDropdown,
                                onDismissRequest = { showBoardDropdown = false },
                                modifier = Modifier.background(OnyxDarkSurface)
                            ) {
                                if (state.boards.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("default", color = HermesTextPrimary) },
                                        onClick = {
                                            viewModel.switchBoard("default")
                                            showBoardDropdown = false
                                        }
                                    )
                                } else {
                                    state.boards.forEach { b ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = b.name ?: b.slug,
                                                        color = if (b.slug == state.currentBoard) HermesPrimary else HermesTextPrimary,
                                                        fontWeight = if (b.slug == state.currentBoard) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = "${b.total} tâches",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HermesTextMuted
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.switchBoard(b.slug)
                                                showBoardDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour au Chat",
                            tint = HermesTextPrimary
                        )
                    }
                },
                actions = {
                    // Dispatch agent action
                    FilledTonalButton(
                        onClick = { viewModel.dispatch() },
                        enabled = !state.isDispatching,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = HermesPrimary.copy(alpha = 0.2f),
                            contentColor = HermesPrimary
                        )
                    ) {
                        if (state.isDispatching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = HermesPrimary
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dispatch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Refresh
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Rafraîchir",
                            tint = HermesTextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OnyxDarkSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog(true) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nouvelle tâche", fontWeight = FontWeight.SemiBold) },
                containerColor = HermesPrimary,
                contentColor = OnyxDarkBackground,
                shape = RoundedCornerShape(16.dp)
            )
        },
        containerColor = OnyxDarkBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Filtrer les tâches...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = HermesTextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = HermesTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OnyxDarkSurface,
                    unfocusedContainerColor = OnyxDarkSurface,
                    focusedBorderColor = HermesPrimary,
                    unfocusedBorderColor = OnyxBorder
                )
            )

            // Scrollable Tab Row for Columns
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = OnyxDarkBackground,
                contentColor = HermesTextPrimary,
                divider = { HorizontalDivider(color = OnyxBorder) }
            ) {
                displayColumns.forEachIndexed { index, col ->
                    val isSelected = pagerState.currentPage == index
                    val colColor = kanbanStatusColor(col.name)
                    val count = col.tasks.size

                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(colColor)
                                )
                                Text(
                                    text = kanbanStatusLabel(col.name),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) HermesTextPrimary else HermesTextMuted
                                )
                                // Count badge
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) colColor.copy(alpha = 0.25f) else OnyxDarkSurfaceVariant
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (isSelected) colColor else HermesTextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            // Horizontal Pager between columns
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { page ->
                val col = displayColumns[page]
                val filteredTasks = remember(col.tasks, state.searchQuery) {
                    if (state.searchQuery.isBlank()) col.tasks
                    else col.tasks.filter {
                        it.title.contains(state.searchQuery, ignoreCase = true) ||
                        (it.body?.contains(state.searchQuery, ignoreCase = true) == true) ||
                        (it.assignee?.contains(state.searchQuery, ignoreCase = true) == true)
                    }
                }

                if (filteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ViewKanban,
                                contentDescription = null,
                                tint = HermesTextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aucune tâche dans \"${kanbanStatusLabel(col.name)}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HermesTextMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { viewModel.showCreateDialog(true) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ajouter ici")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            KanbanTaskCard(
                                task = task,
                                onClick = { viewModel.selectTask(task) },
                                onQuickMove = { nextStatus ->
                                    viewModel.moveTask(task.id, nextStatus)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Task details modal bottom sheet
    state.selectedTask?.let { task ->
        KanbanTaskDetailSheet(
            task = task,
            onDismiss = { viewModel.selectTask(null) },
            onSave = { title, body, status, priority, assignee ->
                viewModel.updateTask(task.id, title, body, status, priority, assignee)
            },
            onBlock = { reason ->
                viewModel.blockTask(task.id, reason)
            },
            onUnblock = {
                viewModel.unblockTask(task.id)
            },
            onArchive = {
                viewModel.archiveTask(task.id)
            }
        )
    }

    // Create task dialog
    if (state.showCreateTaskDialog) {
        val currentColName = displayColumns.getOrNull(pagerState.currentPage)?.name ?: "todo"
        CreateTaskDialog(
            initialStatus = if (currentColName in listOf("triage", "todo", "ready")) currentColName else "todo",
            onDismiss = { viewModel.showCreateDialog(false) },
            onCreate = { title, body, status, priority, assignee ->
                viewModel.createTask(title, body, status, priority, assignee)
            }
        )
    }
}

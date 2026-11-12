package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.*
import com.example.ui.components.DiffViewerModal
import com.example.ui.components.FileTreeDrawer
import com.example.ui.components.TokenCostHud
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onSetPermissionMode: (PermissionMode) -> Unit,
    onSelectModel: (String) -> Unit,
    onPerformLiveSearch: (String) -> Unit,
    onApproveDiff: () -> Unit,
    onRejectDiff: () -> Unit,
    onUndoTurn: () -> Unit,
    onRefreshWorkspaceFiles: () -> Unit,
    onIndexWorkspaceFts: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onOpenKeySettings: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showPermissionMenu by remember { mutableStateOf(false) }
    var showExportModal by remember { mutableStateOf(false) }
    var showSearchDrawer by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto scroll to bottom when messages update
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FileTreeDrawer(
                fileList = uiState.workspaceFiles,
                modifiedPaths = uiState.modifiedPaths,
                onSelectFile = { selectedPath ->
                    onSendMessage("Read and inspect '$selectedPath'")
                },
                onRefreshTree = onRefreshWorkspaceFiles,
                onIndexTree = onIndexWorkspaceFts,
                onClose = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = ClaudeAccentClay.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = ClaudeAccentClay,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = uiState.activeWorkspace?.name ?: "Select Workspace",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.apiKey.isNull_or_blank()) "Key missing" else "Gemini Engine Connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.apiKey.isNull_or_blank()) MaterialTheme.colorScheme.error else DiffGreenText
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Tree Drawer")
                        }
                    },
                    actions = {
                        // FTS Search Icon Button
                        IconButton(onClick = { showSearchDrawer = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "FTS Code Search")
                        }

                        // Permission Mode Selector Pill
                        Box {
                            Surface(
                                onClick = { showPermissionMenu = true },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (uiState.permissionMode) {
                                            PermissionMode.ASK -> Icons.Default.Shield
                                            PermissionMode.AUTO -> Icons.Default.FlashOn
                                            PermissionMode.READ_ONLY -> Icons.Default.Visibility
                                        },
                                        contentDescription = null,
                                        tint = ClaudeAccentClay,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = uiState.permissionMode.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showPermissionMenu,
                                onDismissRequest = { showPermissionMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ask Mode (Visual Diff Gate)") },
                                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                    onClick = {
                                        onSetPermissionMode(PermissionMode.ASK)
                                        showPermissionMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Auto Mode (Snapshot Rollback)") },
                                    leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                                    onClick = {
                                        onSetPermissionMode(PermissionMode.AUTO)
                                        showPermissionMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Read-Only Inspection Mode") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                    onClick = {
                                        onSetPermissionMode(PermissionMode.READ_ONLY)
                                        showPermissionMenu = false
                                    }
                                )
                            }
                        }

                        // Workspace Picker Button
                        IconButton(onClick = onOpenWorkspacePicker, modifier = Modifier.testTag("workspace_nav_button")) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Workspace")
                        }

                        // API Key Settings Button
                        IconButton(onClick = onOpenKeySettings, modifier = Modifier.testTag("key_nav_button")) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = "API Key")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Token, Cost & Latency HUD
                    TokenCostHud(
                        selectedModel = uiState.selectedModel,
                        onSelectModel = onSelectModel,
                        gitBranch = uiState.gitBranch,
                        estimatedTokens = uiState.estimatedTokens,
                        lastLatencyMs = uiState.lastLatencyMs,
                        indexedFilesCount = uiState.indexedFilesCount
                    )

                    // Status Toast
                    uiState.statusMessage?.let { status ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Chat Messages List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(message)
                        }
                    }

                    // Floating Action Trace HUD
                    com.example.ui.components.FloatingActionTraceHud(
                        actionTrace = uiState.actionTrace,
                        isGenerating = uiState.isGenerating
                    )

                    // Quick Shortcut Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showSearchDrawer = true },
                            label = { Text("FTS Search") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { onSendMessage("Check git status and branch summary") },
                            label = { Text("Git Status") },
                            leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { showExportModal = true },
                            label = { Text("Export Log") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        if (uiState.canUndoTurn) {
                            AssistChip(
                                onClick = onUndoTurn,
                                label = { Text("Undo last turn") },
                                leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = ClaudeAccentClay)
                            )
                        }
                    }

                    // Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask Gemini to search, edit, or run shell...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !uiState.isGenerating
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText.trim())
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank() && !uiState.isGenerating,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !uiState.isGenerating) ClaudeAccentClay else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank() && !uiState.isGenerating) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Pending Diff Modal Approval Curtain
                uiState.pendingDiff?.let { pending ->
                    DiffViewerModal(
                        diffProposal = pending.diffProposal,
                        onApprove = onApproveDiff,
                        onReject = onRejectDiff
                    )
                }

                // Export Session Modal
                if (showExportModal) {
                    com.example.ui.components.ExportSessionModal(
                        messages = uiState.messages,
                        workspaceName = uiState.activeWorkspace?.name,
                        onDismiss = { showExportModal = false }
                    )
                }

                // FTS Search Drawer Modal Sheet
                if (showSearchDrawer) {
                    com.example.ui.components.FtsSearchDrawer(
                        searchResults = uiState.searchResults,
                        onQueryChange = onPerformLiveSearch,
                        onSelectSnippet = { snippet ->
                            onSendMessage("Read and inspect '${snippet.relativePath}' around line ${snippet.lineNumber}")
                        },
                        onDismiss = { showSearchDrawer = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        // Message Card
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) ClaudeAccentClay else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Render Tool Execution Cards
        if (message.toolCalls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                message.toolCalls.forEach { toolCard ->
                    ToolCallItemCard(toolCard)
                }
            }
        }
    }
}

@Composable
fun ToolCallItemCard(card: ToolCallCard) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (card.status) {
        ToolStatus.COMPLETED -> DiffGreenText
        ToolStatus.PENDING_APPROVAL -> ClaudeAccentClay
        ToolStatus.REJECTED -> DiffRedText
        ToolStatus.ERROR -> MaterialTheme.colorScheme.error
        ToolStatus.EXECUTING -> ClaudeTextMuted
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (card.status) {
                            ToolStatus.COMPLETED -> Icons.Default.CheckCircle
                            ToolStatus.PENDING_APPROVAL -> Icons.Default.Pending
                            ToolStatus.REJECTED -> Icons.Default.Cancel
                            ToolStatus.ERROR -> Icons.Default.Error
                            ToolStatus.EXECUTING -> Icons.Default.HourglassEmpty
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = card.summary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    card.resultText?.let { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean {
    return this == null || this.isBlank() || this == "MY_GEMINI_API_KEY"
}

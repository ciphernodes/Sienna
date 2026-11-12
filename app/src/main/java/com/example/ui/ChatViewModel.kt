package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class PermissionMode {
    ASK,      // Require diff approval before write/edit
    AUTO,     // Auto-apply edits with turn rollback snapshot
    READ_ONLY // Reject write/edit operations
}

enum class ToolStatus {
    EXECUTING,
    PENDING_APPROVAL,
    COMPLETED,
    REJECTED,
    ERROR
}

data class ToolCallCard(
    val id: String,
    val name: String,
    val summary: String,
    val status: ToolStatus,
    val resultText: String? = null,
    val diffProposal: FileDiffProposal? = null
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val toolCalls: List<ToolCallCard> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingDiffState(
    val callId: String,
    val diffProposal: FileDiffProposal
)

data class ChatUiState(
    val apiKey: String? = null,
    val activeWorkspace: WorkspaceInfo? = null,
    val permissionMode: PermissionMode = PermissionMode.ASK,
    val selectedModel: String = "gemini-2.5-flash",
    val gitBranch: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val pendingDiff: PendingDiffState? = null,
    val actionTrace: List<String> = emptyList(),
    val statusMessage: String? = null,
    val canUndoTurn: Boolean = false,
    val estimatedTokens: Int = 0,
    val lastLatencyMs: Long = 0,
    val indexedFilesCount: Int = 0,
    val workspaceFiles: List<FileItemInfo> = emptyList(),
    val modifiedPaths: Set<String> = emptySet(),
    val searchResults: List<SearchResultSnippet> = emptyList()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val keyManager = KeyManager(application)
    private val workspaceManager = WorkspaceManager(application)
    private val localIndexer = LocalIndexer(application)
    private val indexingWorker = WorkspaceIndexingWorker(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationHistory = mutableListOf<JSONObject>()
    private var activeTurnAffectedPaths = mutableSetOf<String>()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val key = keyManager.getApiKey()
        val ws = workspaceManager.getActiveWorkspace()
        val files = if (ws != null) workspaceManager.listDirectory("") else emptyList()

        _uiState.update {
            it.copy(
                apiKey = key,
                activeWorkspace = ws,
                workspaceFiles = files,
                messages = listOf(
                    ChatMessage(
                        isUser = false,
                        text = "Hello! I am Sienna AI. Connect a workspace folder to inspect files, search code, and review floating live action traces."
                    )
                )
            )
        }

        if (ws != null) {
            indexWorkspaceFts()
        }
    }

    fun handleSharedText(sharedText: String) {
        if (sharedText.isNotBlank()) {
            val formattedMsg = "Shared context from Android system:\n$sharedText"
            sendMessage(formattedMsg)
            addTrace("📥 Imported shared text via System Share Sheet")
        }
    }

    fun refreshWorkspaceFiles() {
        if (_uiState.value.activeWorkspace != null) {
            val files = workspaceManager.listDirectory("")
            _uiState.update { it.copy(workspaceFiles = files) }
            addTrace("📂 Refreshed workspace file tree (${files.size} items)")
        }
    }

    fun performLiveFtsSearch(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
            } else {
                val results = localIndexer.searchIndex(query)
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }

    fun indexWorkspaceFts() {
        addTrace("⚡ Triggering background Room FTS indexer worker...")
        indexingWorker.triggerAutoIndexing(workspaceManager) { count ->
            _uiState.update {
                it.copy(
                    indexedFilesCount = count,
                    statusMessage = "Room FTS indexed $count files"
                )
            }
            addTrace("✓ Room FTS indexed $count workspace text files")
        }
    }

    fun setSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        addTrace("🤖 Gemini model switched to: $model")
    }

    fun checkGitStatus() {
        viewModelScope.launch {
            val status = GitManager.getGitStatus(getApplication(), _uiState.value.activeWorkspace?.rawPath)
            if (status.isGitRepo) {
                _uiState.update { it.copy(gitBranch = status.branch) }
                addTrace("🌿 Git Branch: ${status.branch} (${status.modifiedFiles.size} modified, ${status.untrackedFiles.size} untracked)")
            }
        }
    }

    fun saveApiKey(newKey: String) {
        keyManager.saveApiKey(newKey)
        _uiState.update { it.copy(apiKey = newKey, statusMessage = "API Key saved successfully!") }
    }

    fun setWorkspaceUri(uri: Uri, displayName: String) {
        workspaceManager.setWorkspaceUri(uri, displayName)
        val ws = workspaceManager.getActiveWorkspace()
        refreshWorkspaceFiles()
        _uiState.update {
            it.copy(
                activeWorkspace = ws,
                statusMessage = "Opened workspace: $displayName"
            )
        }
        addTrace("📂 Switched to workspace SAF: $displayName")
        indexWorkspaceFts()
        checkGitStatus()
    }

    fun setWorkspacePath(path: String) {
        workspaceManager.setWorkspacePath(path)
        val ws = workspaceManager.getActiveWorkspace()
        refreshWorkspaceFiles()
        _uiState.update {
            it.copy(
                activeWorkspace = ws,
                statusMessage = "Opened workspace path: $path"
            )
        }
        addTrace("📂 Switched to raw path: $path")
        indexWorkspaceFts()
        checkGitStatus()
    }

    fun setPermissionMode(mode: PermissionMode) {
        _uiState.update { it.copy(permissionMode = mode) }
        addTrace("🛡️ Permission mode set to: ${mode.name}")
    }

    fun sendMessage(userText: String) {
        val key = _uiState.value.apiKey
        if (key.isNull_or_blank_key()) {
            _uiState.update { it.copy(statusMessage = "Please set your Gemini API key in settings first!") }
            return
        }
        if (_uiState.value.activeWorkspace == null) {
            _uiState.update { it.copy(statusMessage = "Please select a workspace folder first!") }
            return
        }

        val userMsg = ChatMessage(isUser = true, text = userText)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isGenerating = true,
                statusMessage = null
            )
        }

        // Append user turn to history
        val userTurnJson = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", userText)))
        }
        conversationHistory.add(userTurnJson)
        activeTurnAffectedPaths.clear()

        // Token Estimation update
        val approxTokens = conversationHistory.sumOf { it.toString().length / 4 }
        _uiState.update { it.copy(estimatedTokens = approxTokens) }

        addTrace("💬 Request sent to ${_uiState.value.selectedModel}...")

        viewModelScope.launch {
            processGeminiTurn(key.orEmpty())
        }
    }

    private suspend fun processGeminiTurn(apiKey: String) {
        val service = GeminiService(apiKey, _uiState.value.selectedModel)
        val systemPrompt = buildSystemPrompt()
        val startTime = System.currentTimeMillis()

        val response = service.generateWithTools(systemPrompt, conversationHistory)
        val latency = System.currentTimeMillis() - startTime

        _uiState.update { it.copy(lastLatencyMs = latency) }

        when (response) {
            is GeminiResponse.TextContent -> {
                val assistantMsg = ChatMessage(isUser = false, text = response.text)
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMsg,
                        isGenerating = false
                    )
                }

                // Add assistant response to history
                conversationHistory.add(JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", response.text)))
                })
                addTrace("✓ Response completed (${latency}ms).")
            }

            is GeminiResponse.FunctionCall -> {
                handleFunctionCalls(service, response.calls, response.rawThought)
            }

            is GeminiResponse.Error -> {
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            isUser = false,
                            text = "⚠️ Error: ${response.message}"
                        ),
                        isGenerating = false
                    )
                }
                addTrace("❌ Model returned error: ${response.message}")
            }
        }
    }

    private suspend fun handleFunctionCalls(
        service: GeminiService,
        calls: List<ToolCallRequest>,
        rawThought: String?
    ) {
        val toolCards = mutableListOf<ToolCallCard>()

        // Append model call to history
        val modelParts = JSONArray()
        if (!rawThought.isNullOrBlank()) {
            modelParts.put(JSONObject().put("text", rawThought))
        }
        for (call in calls) {
            modelParts.put(JSONObject().apply {
                put("functionCall", JSONObject().apply {
                    put("name", call.name)
                    put("args", JSONObject(call.args))
                })
            })
        }
        conversationHistory.add(JSONObject().apply {
            put("role", "model")
            put("parts", modelParts)
        })

        var requiresUserApproval = false
        var pendingApprovalCall: ToolCallRequest? = null
        var pendingDiffProposal: FileDiffProposal? = null

        for (call in calls) {
            val path = call.args["path"]?.toString().orEmpty()
            addTrace("🛠️ Executing tool: ${call.name}($path)")

            when (call.name) {
                "list_directory" -> {
                    val list = workspaceManager.listDirectory(path)
                    val result = list.joinToString("\n") {
                        if (it.isDirectory) "[DIR] ${it.relativePath}/" else "[FILE] ${it.relativePath} (${it.size} B)"
                    }.ifEmpty { "Empty directory" }

                    toolCards.add(ToolCallCard(call.id, call.name, "Listed directory '$path'", ToolStatus.COMPLETED, result))
                    addToolResponseToHistory(call.name, JSONObject().put("files", result))
                }

                "read_file" -> {
                    try {
                        val content = workspaceManager.readFile(path)
                        val truncated = if (content.length > 8000) content.take(8000) + "\n... [truncated]" else content
                        toolCards.add(ToolCallCard(call.id, call.name, "Read file '$path'", ToolStatus.COMPLETED, truncated))
                        addToolResponseToHistory(call.name, JSONObject().put("content", content))
                    } catch (e: Exception) {
                        toolCards.add(ToolCallCard(call.id, call.name, "Read file '$path'", ToolStatus.ERROR, e.message))
                        addToolResponseToHistory(call.name, JSONObject().put("error", e.message))
                    }
                }

                "read_lines" -> {
                    val start = (call.args["start"] as? Number)?.toInt() ?: 1
                    val end = (call.args["end"] as? Number)?.toInt() ?: 100
                    try {
                        val lines = workspaceManager.readLines(path, start, end)
                        toolCards.add(ToolCallCard(call.id, call.name, "Read lines $start-$end of '$path'", ToolStatus.COMPLETED, lines))
                        addToolResponseToHistory(call.name, JSONObject().put("lines", lines))
                    } catch (e: Exception) {
                        toolCards.add(ToolCallCard(call.id, call.name, "Read lines '$path'", ToolStatus.ERROR, e.message))
                        addToolResponseToHistory(call.name, JSONObject().put("error", e.message))
                    }
                }

                "git_status" -> {
                    val status = GitManager.getGitStatus(getApplication(), _uiState.value.activeWorkspace?.rawPath)
                    val summary = "Git Branch: ${status.branch}, Modified: ${status.modifiedFiles.joinToString()}, Untracked: ${status.untrackedFiles.joinToString()}"
                    toolCards.add(ToolCallCard(call.id, call.name, "Git Status (${status.branch})", ToolStatus.COMPLETED, summary))
                    addToolResponseToHistory(call.name, JSONObject().apply {
                        put("branch", status.branch)
                        put("modified", JSONArray(status.modifiedFiles))
                        put("untracked", JSONArray(status.untrackedFiles))
                    })
                }

                "get_device_telemetry" -> {
                    val telemetry = DeviceTelemetryManager.getDeviceTelemetry(getApplication())
                    val summary = "Device: ${telemetry.model} (Android ${telemetry.androidVersion}, API ${telemetry.apiLevel})\nBattery: ${telemetry.batteryPercent}% (${if (telemetry.isCharging) "Charging" else "Discharging"})\nRAM: ${telemetry.availableRamMb} MB free / ${telemetry.totalRamMb} MB total\nStorage: ${telemetry.freeStorageGb} GB free / ${telemetry.totalStorageGb} GB total\nNetwork: ${telemetry.networkType}"

                    toolCards.add(ToolCallCard(call.id, call.name, "Device Telemetry (${telemetry.model})", ToolStatus.COMPLETED, summary))
                    addToolResponseToHistory(call.name, JSONObject().apply {
                        put("model", telemetry.model)
                        put("androidVersion", telemetry.androidVersion)
                        put("batteryPercent", telemetry.batteryPercent)
                        put("isCharging", telemetry.isCharging)
                        put("availableRamMb", telemetry.availableRamMb)
                        put("freeStorageGb", telemetry.freeStorageGb)
                        put("networkType", telemetry.networkType)
                    })
                }

                "file_stat" -> {
                    val stat = workspaceManager.getFileStat(path)
                    val statSummary = "Exists: ${stat.exists}, Size: ${stat.size} B, Lines: ${stat.lineCount}, SHA256: ${stat.sha256.take(8)}..."
                    toolCards.add(ToolCallCard(call.id, call.name, "Stat '$path'", ToolStatus.COMPLETED, statSummary))
                    addToolResponseToHistory(call.name, JSONObject().apply {
                        put("exists", stat.exists)
                        put("size", stat.size)
                        put("line_count", stat.lineCount)
                        put("sha256", stat.sha256)
                    })
                }

                "search_index" -> {
                    val query = call.args["query"]?.toString().orEmpty()
                    val matches = localIndexer.searchIndex(query)
                    val formatted = matches.joinToString("\n") {
                        "${it.relativePath}:${it.lineNumber}: ${it.lineContent}"
                    }.ifEmpty { "No matching index results for '$query'" }

                    toolCards.add(ToolCallCard(call.id, call.name, "Room FTS Search '$query' (${matches.size} matches)", ToolStatus.COMPLETED, formatted))
                    addToolResponseToHistory(call.name, JSONObject().put("results", formatted))
                }

                "run_shell_command" -> {
                    val cmd = call.args["command"]?.toString().orEmpty()
                    val output = ShellExecutionBridge.executeCommand(getApplication(), cmd, _uiState.value.activeWorkspace?.rawPath)

                    toolCards.add(ToolCallCard(call.id, call.name, "Shell: $cmd", ToolStatus.COMPLETED, output))
                    addToolResponseToHistory(call.name, JSONObject().put("output", output))
                }

                "write_file", "edit_file" -> {
                    val newContent = call.args["content"]?.toString() ?: call.args["new_content"]?.toString().orEmpty()
                    val existingContent = try { workspaceManager.readFile(path) } catch (e: Exception) { "" }

                    val diff = DiffEngine.generateDiff(path, existingContent, newContent)

                    when (_uiState.value.permissionMode) {
                        PermissionMode.READ_ONLY -> {
                            toolCards.add(ToolCallCard(call.id, call.name, "Blocked write to '$path'", ToolStatus.REJECTED, "Denied by Read-Only mode"))
                            addToolResponseToHistory(call.name, JSONObject().put("status", "denied_read_only"))
                        }

                        PermissionMode.AUTO -> {
                            activeTurnAffectedPaths.add(path)
                            workspaceManager.writeFile(path, newContent)
                            _uiState.update { it.copy(modifiedPaths = it.modifiedPaths + path) }
                            toolCards.add(ToolCallCard(call.id, call.name, "Auto-applied edits to '$path'", ToolStatus.COMPLETED, "Successfully written", diff))
                            addToolResponseToHistory(call.name, JSONObject().put("status", "success"))
                        }

                        PermissionMode.ASK -> {
                            requiresUserApproval = true
                            pendingApprovalCall = call
                            pendingDiffProposal = diff
                            toolCards.add(ToolCallCard(call.id, call.name, "Proposed edits for '$path'", ToolStatus.PENDING_APPROVAL, null, diff))
                        }
                    }
                }
            }
        }

        val assistantText = rawThought ?: "Proposed execution step:"
        val msg = ChatMessage(isUser = false, text = assistantText, toolCalls = toolCards)
        _uiState.update { it.copy(messages = it.messages + msg) }

        if (requiresUserApproval && pendingApprovalCall != null && pendingDiffProposal != null) {
            _uiState.update {
                it.copy(
                    pendingDiff = PendingDiffState(pendingApprovalCall.id, pendingDiffProposal),
                    isGenerating = false
                )
            }
            addTrace("⏳ Paused for visual diff approval...")
        } else {
            processGeminiTurn(apiKey = keyManager.getApiKey().orEmpty())
        }
    }

    fun approvePendingDiff() {
        val pending = _uiState.value.pendingDiff ?: return
        val proposal = pending.diffProposal

        activeTurnAffectedPaths.add(proposal.relativePath)
        workspaceManager.createTurnSnapshot("turn_${System.currentTimeMillis()}", activeTurnAffectedPaths.toList())
        workspaceManager.writeFile(proposal.relativePath, proposal.proposedContent)

        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                val updatedCards = msg.toolCalls.map { card ->
                    if (card.id == pending.callId) {
                        card.copy(status = ToolStatus.COMPLETED, resultText = "Applied successfully")
                    } else card
                }
                msg.copy(toolCalls = updatedCards)
            }
            state.copy(
                messages = updatedMessages,
                pendingDiff = null,
                isGenerating = true,
                canUndoTurn = true,
                modifiedPaths = state.modifiedPaths + proposal.relativePath
            )
        }

        addTrace("✅ Applied visual diff to '${proposal.relativePath}'")
        addToolResponseToHistory("edit_file", JSONObject().put("status", "approved_and_applied"))

        viewModelScope.launch {
            processGeminiTurn(keyManager.getApiKey().orEmpty())
        }
    }

    fun rejectPendingDiff() {
        val pending = _uiState.value.pendingDiff ?: return

        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                val updatedCards = msg.toolCalls.map { card ->
                    if (card.id == pending.callId) {
                        card.copy(status = ToolStatus.REJECTED, resultText = "Rejected by user")
                    } else card
                }
                msg.copy(toolCalls = updatedCards)
            }
            state.copy(
                messages = updatedMessages,
                pendingDiff = null,
                isGenerating = false
            )
        }

        addTrace("❌ Rejected proposed diff for '${pending.diffProposal.relativePath}'")
        addToolResponseToHistory("edit_file", JSONObject().put("status", "rejected_by_user"))
    }

    fun undoLastTurn() {
        val success = workspaceManager.rollbackLastTurn()
        if (success) {
            _uiState.update {
                it.copy(
                    statusMessage = "Turn changes rolled back successfully!",
                    canUndoTurn = false
                )
            }
            addTrace("↩️ Rolled back last turn file modifications.")
        } else {
            _uiState.update { it.copy(statusMessage = "No prior turn snapshot to rollback.") }
        }
    }

    private fun addToolResponseToHistory(name: String, responseContent: JSONObject) {
        val parts = JSONArray().put(JSONObject().apply {
            put("functionResponse", JSONObject().apply {
                put("name", name)
                put("response", responseContent)
            })
        })
        conversationHistory.add(JSONObject().apply {
            put("role", "function")
            put("parts", parts)
        })
    }

    private fun addTrace(line: String) {
        _uiState.update { it.copy(actionTrace = (it.actionTrace + line).takeLast(20)) }
        com.example.overlay.OverlayHudManager.updateTrace(line)
    }

    private fun String?.isNull_or_blank_key(): Boolean {
        return this == null || this.isBlank() || this == "MY_GEMINI_API_KEY"
    }

    private fun buildSystemPrompt(): String {
        val ws = _uiState.value.activeWorkspace
        val customRules = InstructionsLoader.loadCustomInstructions(workspaceManager)

        return """
            You are a Personal AI Project Assistant with full file inspection, SQLite FTS code search, and shell execution capabilities.
            Current Active Workspace: ${ws?.name ?: "None"} (${if (ws?.isSaf == true) "SAF Document Tree" else "Raw File Path"})

            Use function tools (list_directory, read_file, read_lines, file_stat, search_index, run_shell_command, write_file, edit_file) to inspect and modify the project.
            Always inspect or search files before modifying them. In 'Ask' mode, edits trigger visual green/red diff review before applying.

            ${if (!customRules.isNullOrBlank()) "Project Custom Instructions (.ai/instructions.md):\n$customRules" else ""}
            Be concise, warm, and precise.
        """.trimIndent()
    }
}

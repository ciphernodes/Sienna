package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.data.WorkspaceInfo
import com.example.data.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkspaceUiState(
    val activeWorkspace: WorkspaceInfo? = null,
    val savedUris: List<String> = emptyList(),
    val statusMessage: String? = null
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val workspaceManager = WorkspaceManager(application)
    private val prefs = application.getSharedPreferences("workspace_view_model_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        loadWorkspaceState()
    }

    private fun loadWorkspaceState() {
        val current = workspaceManager.getActiveWorkspace()
        val saved = prefs.getStringSet("persistent_workspace_uris", emptySet())?.toList() ?: emptyList()
        _uiState.update {
            it.copy(
                activeWorkspace = current,
                savedUris = saved
            )
        }
    }

    fun selectWorkspaceUri(uri: Uri, displayName: String) {
        val context = getApplication<Application>()
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        workspaceManager.setWorkspaceUri(uri, displayName)

        // Save URI to persistent local storage
        val currentSet = prefs.getStringSet("persistent_workspace_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(uri.toString())
        prefs.edit().putStringSet("persistent_workspace_uris", currentSet).apply()

        val updated = workspaceManager.getActiveWorkspace()
        _uiState.update {
            it.copy(
                activeWorkspace = updated,
                savedUris = currentSet.toList(),
                statusMessage = "Project folder access granted & stored: $displayName"
            )
        }
    }

    fun selectWorkspacePath(path: String) {
        workspaceManager.setWorkspacePath(path)
        val updated = workspaceManager.getActiveWorkspace()
        _uiState.update {
            it.copy(
                activeWorkspace = updated,
                statusMessage = "Project path set to: $path"
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

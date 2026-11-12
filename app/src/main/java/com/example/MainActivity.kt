package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.ChatViewModel
import com.example.ui.screens.ApiKeyScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.ProjectAssistantTheme

class MainActivity : ComponentActivity() {

    private var initialSharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            initialSharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }

        setContent {
            ProjectAssistantTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProjectAssistantApp(initialSharedText = initialSharedText)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNull_or_blank()) {
                initialSharedText = sharedText
            }
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}

@Composable
fun ProjectAssistantApp(
    initialSharedText: String? = null,
    viewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            viewModel.handleSharedText(initialSharedText)
        }
    }

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                uiState = uiState,
                onSendMessage = { viewModel.sendMessage(it) },
                onSetPermissionMode = { viewModel.setPermissionMode(it) },
                onSelectModel = { viewModel.setSelectedModel(it) },
                onPerformLiveSearch = { viewModel.performLiveFtsSearch(it) },
                onApproveDiff = { viewModel.approvePendingDiff() },
                onRejectDiff = { viewModel.rejectPendingDiff() },
                onUndoTurn = { viewModel.undoLastTurn() },
                onRefreshWorkspaceFiles = { viewModel.refreshWorkspaceFiles() },
                onIndexWorkspaceFts = { viewModel.indexWorkspaceFts() },
                onOpenWorkspacePicker = { navController.navigate("workspace") },
                onOpenKeySettings = { navController.navigate("keys") },
                onOpenFridaStudio = { navController.navigate("frida") },
                onOpenAutomation = { navController.navigate("automation") }
            )
        }

        composable("workspace") {
            WorkspaceScreen(
                activeWorkspace = uiState.activeWorkspace,
                onSelectUri = { uri, name -> viewModel.setWorkspaceUri(uri, name) },
                onSelectPath = { path -> viewModel.setWorkspacePath(path) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("keys") {
            ApiKeyScreen(
                currentApiKey = uiState.apiKey,
                onSaveKey = { newKey -> viewModel.saveApiKey(newKey) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("frida") {
            com.example.ui.screens.FridaInstrumentationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("automation") {
            com.example.ui.screens.AutomationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

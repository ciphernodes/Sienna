package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.WorkspaceInfo
import com.example.ui.theme.ClaudeAccentClay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    activeWorkspace: WorkspaceInfo?,
    onSelectUri: (Uri, String) -> Unit,
    onSelectPath: (String) -> Unit,
    onBack: () -> Unit
) {
    var rawPathInput by remember { mutableStateOf(activeWorkspace?.rawPath ?: "") }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = uri.lastPathSegment?.substringAfterLast(':')?.ifEmpty { "Project Folder" } ?: "Project Folder"
            onSelectUri(uri, displayName)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspace Picker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Workspace Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = ClaudeAccentClay,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = activeWorkspace?.name ?: "No Active Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeWorkspace == null) "Select a project directory below"
                            else if (activeWorkspace.isSaf) "SAF Tree Access Active"
                            else "Direct Path: ${activeWorkspace.rawPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SAF Picker Option
            Card(
                onClick = { safLauncher.launch(null) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saf_picker_button"),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = ClaudeAccentClay
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Pick Directory (SAF)", fontWeight = FontWeight.Bold)
                        Text(
                            "Select any project folder via Android Document Picker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Raw Path Option (e.g. adb /sdcard)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = ClaudeAccentClay
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Raw Filesystem Path (adb/storage)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = rawPathInput,
                        onValueChange = { rawPathInput = it },
                        label = { Text("Path (e.g. /sdcard/MyProject)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (rawPathInput.isNotBlank()) {
                                onSelectPath(rawPathInput.trim())
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("set_path_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccentClay)
                    ) {
                        Text("Open Path")
                    }
                }
            }
        }
    }
}

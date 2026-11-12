package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FileItemInfo
import com.example.ui.theme.ClaudeAccentClay
import com.example.ui.theme.ClaudeTextMuted
import com.example.ui.theme.ClaudeTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTreeDrawer(
    fileList: List<FileItemInfo>,
    modifiedPaths: Set<String>,
    onSelectFile: (String) -> Unit,
    onRefreshTree: () -> Unit,
    onIndexTree: () -> Unit,
    onClose: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }

    val filteredList = remember(fileList, filterQuery) {
        if (filterQuery.isBlank()) fileList
        else fileList.filter { it.relativePath.contains(filterQuery, ignoreCase = true) }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = ClaudeAccentClay
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Project Tree",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = onIndexTree) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "FTS Index")
                    }
                    IconButton(onClick = onRefreshTree) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Tree")
                    }
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Filter
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Filter files...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // File List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredList) { file ->
                    val isModified = modifiedPaths.contains(file.relativePath)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!file.isDirectory) {
                                    onSelectFile(file.relativePath)
                                    onClose()
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isDirectory) ClaudeAccentClay else ClaudeTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (isModified) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "●",
                                        color = ClaudeAccentClay,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = file.relativePath,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = ClaudeTextMuted
                            )
                        }

                        if (!file.isDirectory) {
                            Text(
                                text = "${file.size / 1024} KB",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                fontSize = 10.sp,
                                color = ClaudeTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

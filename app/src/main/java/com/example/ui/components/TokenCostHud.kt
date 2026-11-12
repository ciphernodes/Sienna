package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClaudeAccentClay
import com.example.ui.theme.ClaudeTextMuted
import com.example.ui.theme.ClaudeTextSecondary

@Composable
fun TokenCostHud(
    selectedModel: String,
    onSelectModel: (String) -> Unit,
    gitBranch: String?,
    estimatedTokens: Int,
    lastLatencyMs: Long,
    indexedFilesCount: Int
) {
    var showModelMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Model Switcher Pill
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showModelMenu = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = ClaudeAccentClay,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (selectedModel) {
                            "gemini-2.5-pro" -> "2.5 Pro"
                            "gemini-1.5-pro" -> "1.5 Pro"
                            else -> "2.5 Flash"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Gemini 2.5 Flash (Fast)") },
                        onClick = {
                            onSelectModel("gemini-2.5-flash")
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gemini 2.5 Pro (Reasoning)") },
                        onClick = {
                            onSelectModel("gemini-2.5-pro")
                            showModelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gemini 1.5 Pro (Deep Context)") },
                        onClick = {
                            onSelectModel("gemini-1.5-pro")
                            showModelMenu = false
                        }
                    )
                }
            }

            // Git Branch Badge if present
            gitBranch?.let { branch ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = ClaudeAccentClay,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = branch,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                        color = ClaudeTextSecondary
                    )
                }
            }

            // FTS Index Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ClaudeTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$indexedFilesCount indexed",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontSize = 11.sp,
                    color = ClaudeTextSecondary
                )
            }

            // Latency & Token Usage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = ClaudeTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${if (lastLatencyMs > 0) "${lastLatencyMs}ms | " else ""}~$estimatedTokens tok",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontSize = 11.sp,
                    color = ClaudeTextSecondary
                )
            }
        }
    }
}

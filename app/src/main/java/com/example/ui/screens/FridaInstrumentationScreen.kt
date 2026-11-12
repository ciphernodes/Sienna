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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.frida.*
import com.example.ui.theme.ClaudeAccentClay
import com.example.ui.theme.DiffGreenText
import com.example.ui.theme.DiffRedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridaInstrumentationScreen(
    onBack: () -> Unit
) {
    val sessionStatus by FridaManager.sessionStatus.collectAsState()
    val logs by FridaManager.logs.collectAsState()
    val targetPackage by FridaManager.targetPackageName.collectAsState()

    var selectedScript by remember { mutableStateOf(FridaManager.defaultScripts.first()) }
    var scriptCodeInput by remember { mutableStateOf(selectedScript.code) }
    var targetPackageInput by remember { mutableStateOf(targetPackage) }
    var consoleFilterQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0 = Script Studio, 1 = Live Console

    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Auto scroll console logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = ClaudeAccentClay
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Frida Runtime Hooking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Authorized Runtime Instrumentation Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (sessionStatus) {
                            FridaSessionStatus.HOOKING_ACTIVE -> DiffGreenText.copy(alpha = 0.2f)
                            FridaSessionStatus.ATTACHING -> ClaudeAccentClay.copy(alpha = 0.2f)
                            FridaSessionStatus.PAUSED -> Color.Yellow.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when (sessionStatus) {
                                    FridaSessionStatus.HOOKING_ACTIVE -> DiffGreenText
                                    FridaSessionStatus.ATTACHING -> ClaudeAccentClay
                                    FridaSessionStatus.PAUSED -> Color(0xFFFFB300)
                                    else -> Color.Gray
                                },
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sessionStatus.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Target Package Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetPackageInput,
                        onValueChange = {
                            targetPackageInput = it
                            FridaManager.setTargetPackage(it)
                        },
                        label = { Text("Target Application Package") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("frida_target_package_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Tab Navigation (Script Studio vs Live Console)
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Script Manager") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Live Console (${logs.size})") },
                    icon = { Icon(Icons.Default.Dvr, contentDescription = null) }
                )
            }

            if (activeTab == 0) {
                // Script Studio Tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Preset Instrumentation Scripts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FridaManager.defaultScripts.forEach { script ->
                            FilterChip(
                                selected = selectedScript.id == script.id,
                                onClick = {
                                    selectedScript = script
                                    scriptCodeInput = script.code
                                },
                                label = { Text(script.title) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = selectedScript.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = scriptCodeInput,
                        onValueChange = { scriptCodeInput = it },
                        label = { Text("Frida JavaScript Code") },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("frida_script_code_editor"),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                FridaManager.startHookingSession(scriptCodeInput)
                                activeTab = 1
                            },
                            enabled = sessionStatus != FridaSessionStatus.HOOKING_ACTIVE,
                            colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccentClay),
                            modifier = Modifier.weight(1f).testTag("frida_inject_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inject & Hook")
                        }

                        if (sessionStatus == FridaSessionStatus.HOOKING_ACTIVE) {
                            OutlinedButton(
                                onClick = { FridaManager.pauseHookingSession() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }
                        }

                        OutlinedButton(
                            onClick = { FridaManager.stopHookingSession() },
                            enabled = sessionStatus != FridaSessionStatus.DETACHED,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detach")
                        }
                    }
                }
            } else {
                // Live Console Tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedTextField(
                            value = consoleFilterQuery,
                            onValueChange = { consoleFilterQuery = it },
                            placeholder = { Text("Filter console logs...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { FridaManager.clearConsoleLogs() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                        }

                        IconButton(onClick = {
                            val allLogs = logs.joinToString("\n") { "[${it.timestamp}] [${it.tag}] ${it.message}" }
                            clipboardManager.setText(AnnotatedString(allLogs))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        val filteredLogs = remember(logs, consoleFilterQuery) {
                            if (consoleFilterQuery.isBlank()) logs
                            else logs.filter { it.message.contains(consoleFilterQuery, ignoreCase = true) || it.tag.contains(consoleFilterQuery, ignoreCase = true) }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { item ->
                                Row {
                                    Text(
                                        text = "[${item.timestamp}] ",
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "[${item.tag}] ",
                                        color = when (item.level) {
                                            "HOOK" -> DiffGreenText
                                            "WARN" -> Color(0xFFFFB300)
                                            "ERROR" -> DiffRedText
                                            else -> ClaudeAccentClay
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = item.message,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayState(
    val isExecuting: Boolean = false,
    val currentAction: String = "Idle",
    val traceSteps: List<String> = emptyList(),
    val isMinimized: Boolean = false
)

object OverlayHudManager {
    private val _hudState = MutableStateFlow(OverlayState())
    val hudState: StateFlow<OverlayState> = _hudState.asStateFlow()

    fun updateTrace(action: String, isExecuting: Boolean = true) {
        val current = _hudState.value
        val updatedList = (current.traceSteps + action).takeLast(15)
        _hudState.value = current.copy(
            isExecuting = isExecuting,
            currentAction = action,
            traceSteps = updatedList
        )
    }

    fun setMinimized(minimized: Boolean) {
        _hudState.value = _hudState.value.copy(isMinimized = minimized)
    }

    fun stopTrace() {
        _hudState.value = _hudState.value.copy(
            isExecuting = false,
            currentAction = "Completed execution"
        )
    }

    fun canDrawOverlay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun startService(context: Context) {
        if (canDrawOverlay(context)) {
            val intent = Intent(context, OverlayHudService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, OverlayHudService::class.java)
        context.stopService(intent)
    }
}

package com.example.automation

import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ViewElementNode(
    val id: String,
    val text: String,
    val className: String,
    val viewIdResource: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean
)

enum class StepType {
    TAP,
    TYPE_TEXT,
    SWIPE,
    DELAY
}

data class MacroStep(
    val id: String = System.nanoTime().toString(),
    val type: StepType,
    val targetText: String = "",
    val targetViewId: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val delayMs: Long = 1000L
)

data class AutomationMacro(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<MacroStep>
)

object MacroRecorderManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _mappedNodes = MutableStateFlow<List<ViewElementNode>>(emptyList())
    val mappedNodes: StateFlow<List<ViewElementNode>> = _mappedNodes.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _recordedSteps = MutableStateFlow<List<MacroStep>>(emptyList())
    val recordedSteps: StateFlow<List<MacroStep>> = _recordedSteps.asStateFlow()

    private val _savedMacros = MutableStateFlow<List<AutomationMacro>>(
        listOf(
            AutomationMacro(
                id = "macro_1",
                title = "Automated Login & Key Input",
                description = "Maps text fields, enters credentials, and taps login.",
                steps = listOf(
                    MacroStep(type = StepType.TAP, targetViewId = "username_input", x = 300f, y = 500f),
                    MacroStep(type = StepType.TYPE_TEXT, targetText = "developer@sienna.ai"),
                    MacroStep(type = StepType.TAP, targetViewId = "submit_button", x = 300f, y = 800f)
                )
            )
        )
    )
    val savedMacros: StateFlow<List<AutomationMacro>> = _savedMacros.asStateFlow()

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
    }

    fun updateMappedNodes(nodes: List<ViewElementNode>) {
        _mappedNodes.value = nodes
    }

    fun startRecording() {
        _recordedSteps.value = emptyList()
        _isRecording.value = true
    }

    fun addStep(step: MacroStep) {
        if (_isRecording.value) {
            _recordedSteps.value = _recordedSteps.value + step
        }
    }

    fun stopRecording(title: String) {
        _isRecording.value = false
        if (_recordedSteps.value.isNotEmpty()) {
            val newMacro = AutomationMacro(
                id = System.currentTimeMillis().toString(),
                title = if (title.isBlank()) "Automated Sequence #${_savedMacros.value.size + 1}" else title,
                description = "${_recordedSteps.value.size} steps recorded automatically",
                steps = _recordedSteps.value
            )
            _savedMacros.value = _savedMacros.value + newMacro
        }
    }

    fun deleteMacro(id: String) {
        _savedMacros.value = _savedMacros.value.filter { it.id != id }
    }

    fun replayMacro(macro: AutomationMacro, onDispatchGesture: (Float, Float) -> Unit) {
        _isPlaying.value = true
        scope.launch {
            macro.steps.forEach { step ->
                when (step.type) {
                    StepType.TAP -> onDispatchGesture(step.x, step.y)
                    StepType.TYPE_TEXT -> { /* simulated text injection */ }
                    StepType.SWIPE -> { /* simulated swipe */ }
                    StepType.DELAY -> delay(step.delayMs)
                }
                delay(step.delayMs)
            }
            _isPlaying.value = false
        }
    }
}

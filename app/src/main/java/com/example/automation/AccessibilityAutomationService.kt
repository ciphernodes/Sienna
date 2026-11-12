package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        MacroRecorderManager.setServiceActive(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val rootNode = rootInActiveWindow ?: return
        val nodesList = mutableListOf<ViewElementNode>()
        traverseAndMapNodes(rootNode, nodesList)
        MacroRecorderManager.updateMappedNodes(nodesList)

        // Capture user interactions during recording mode
        if (MacroRecorderManager.isRecording.value && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val bounds = Rect()
            event.source?.getBoundsInScreen(bounds)
            val step = MacroStep(
                type = StepType.TAP,
                targetText = event.text.joinToString(" "),
                targetViewId = event.source?.viewIdResourceName ?: "",
                x = bounds.exactCenterX(),
                y = bounds.exactCenterY(),
                delayMs = 800L
            )
            MacroRecorderManager.addStep(step)
        }
    }

    private fun traverseAndMapNodes(node: AccessibilityNodeInfo, list: MutableList<ViewElementNode>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val mapped = ViewElementNode(
            id = node.viewIdResourceName ?: "node_${list.size}",
            text = node.text?.toString() ?: node.contentDescription?.toString() ?: "",
            className = node.className?.toString() ?: "View",
            viewIdResource = node.viewIdResourceName ?: "",
            bounds = bounds,
            isClickable = node.isClickable,
            isEditable = node.isEditable
        )
        if (mapped.text.isNotBlank() || mapped.viewIdResource.isNotBlank() || mapped.isClickable) {
            list.add(mapped)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndMapNodes(child, list)
        }
    }

    fun simulateTapGesture(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        MacroRecorderManager.setServiceActive(false)
    }

    override fun onDestroy() {
        MacroRecorderManager.setServiceActive(false)
        super.onDestroy()
    }
}

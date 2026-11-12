package com.example.data

object InstructionsLoader {

    fun loadCustomInstructions(workspaceManager: WorkspaceManager): String? {
        return try {
            val content = workspaceManager.readFile(".ai/instructions.md")
            if (content.isNotBlank()) content.trim() else null
        } catch (e: Exception) {
            // Check lowercase alternative
            try {
                val content = workspaceManager.readFile(".ai/instructions.txt")
                if (content.isNotBlank()) content.trim() else null
            } catch (ex: Exception) {
                null
            }
        }
    }
}

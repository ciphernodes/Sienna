package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitStatusSummary(
    val isGitRepo: Boolean,
    val branch: String,
    val modifiedFiles: List<String>,
    val untrackedFiles: List<String>
)

object GitManager {

    suspend fun getGitStatus(context: Context, rawPath: String?): GitStatusSummary = withContext(Dispatchers.IO) {
        if (rawPath.isNull_or_blank()) {
            return@withContext GitStatusSummary(false, "main", emptyList(), emptyList())
        }

        val statusOutput = ShellExecutionBridge.executeCommand(context, "git status --porcelain -b", rawPath)
        if (statusOutput.contains("fatal: not a git repository") || statusOutput.contains("Exit Status")) {
            return@withContext GitStatusSummary(false, "none", emptyList(), emptyList())
        }

        val lines = statusOutput.split("\n")
        var branch = "main"
        val modified = mutableListOf<String>()
        val untracked = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("##")) {
                branch = trimmed.removePrefix("##").substringBefore("...").trim()
            } else if (trimmed.startsWith("M ") || trimmed.startsWith(" M")) {
                modified.add(trimmed.substring(2).trim())
            } else if (trimmed.startsWith("??")) {
                untracked.add(trimmed.substring(2).trim())
            }
        }

        GitStatusSummary(true, branch, modified, untracked)
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.isBlank()
    }
}

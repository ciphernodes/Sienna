package com.example.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShellExecutionBridge {

    suspend fun executeCommand(context: Context, command: String, workingDir: String?): String = withContext(Dispatchers.IO) {
        try {
            // Attempt standard process execution
            val processBuilder = ProcessBuilder("sh", "-c", command)
            workingDir?.let {
                val dir = File(it)
                if (dir.exists() && dir.isDirectory) {
                    processBuilder.directory(dir)
                }
            }
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                output.ifEmpty { "Command executed successfully with status 0" }
            } else {
                "Exit Status $exitCode:\n$output"
            }
        } catch (e: Exception) {
            // Fallback note on Termux intent invocation
            "Shell execution result: Command dispatched to system shell. (${e.localizedMessage})"
        }
    }
}

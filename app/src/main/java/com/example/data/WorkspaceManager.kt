package com.example.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

data class FileItemInfo(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

data class FileStatInfo(
    val relativePath: String,
    val exists: Boolean,
    val isDirectory: Boolean,
    val size: Long,
    val lineCount: Int,
    val sha256: String,
    val mimeType: String
)

data class WorkspaceInfo(
    val name: String,
    val uriString: String?,
    val rawPath: String?,
    val isSaf: Boolean
)

data class TurnSnapshot(
    val turnId: String,
    val timestamp: Long,
    val files: Map<String, String?> // path to content (null if created in turn)
)

class WorkspaceManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("workspace_prefs", Context.MODE_PRIVATE)
    private var currentWorkspace: WorkspaceInfo? = null

    // Private snapshot storage directory
    private val snapshotDir: File by lazy {
        File(context.filesDir, "ai_snapshots").apply { if (!exists()) mkdirs() }
    }

    private val snapshotHistory = mutableListOf<TurnSnapshot>()

    init {
        restoreLastWorkspace()
    }

    private fun restoreLastWorkspace() {
        val name = prefs.getString("ws_name", null)
        val uriStr = prefs.getString("ws_uri", null)
        val pathStr = prefs.getString("ws_path", null)
        val isSaf = prefs.getBoolean("ws_is_saf", true)

        if (name != null) {
            currentWorkspace = WorkspaceInfo(name, uriStr, pathStr, isSaf)
        }
    }

    fun getActiveWorkspace(): WorkspaceInfo? = currentWorkspace

    fun setWorkspaceUri(uri: Uri, displayName: String) {
        val uriStr = uri.toString()
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentWorkspace = WorkspaceInfo(displayName, uriStr, null, isSaf = true)
        prefs.edit()
            .putString("ws_name", displayName)
            .putString("ws_uri", uriStr)
            .remove("ws_path")
            .putBoolean("ws_is_saf", true)
            .apply()
    }

    fun setWorkspacePath(path: String) {
        val file = File(path)
        val name = file.name.ifEmpty { "Root" }
        currentWorkspace = WorkspaceInfo(name, null, path, isSaf = false)
        prefs.edit()
            .putString("ws_name", name)
            .remove("ws_uri")
            .putString("ws_path", path)
            .putBoolean("ws_is_saf", false)
            .apply()
    }

    fun listDirectory(subPath: String = ""): List<FileItemInfo> {
        val ws = currentWorkspace ?: return emptyList()
        val normalizedSub = subPath.trim('/').replace('\\', '/')

        if (ws.isSaf && ws.uriString != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(ws.uriString)) ?: return emptyList()
            val targetDoc = findDocumentByPath(rootDoc, normalizedSub) ?: return emptyList()

            if (!targetDoc.isDirectory) return emptyList()

            return targetDoc.listFiles().map { doc ->
                val childRel = if (normalizedSub.isEmpty()) doc.name.orEmpty() else "$normalizedSub/${doc.name.orEmpty()}"
                FileItemInfo(
                    name = doc.name.orEmpty(),
                    relativePath = childRel,
                    isDirectory = doc.isDirectory,
                    size = doc.length(),
                    lastModified = doc.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else if (ws.rawPath != null) {
            val baseDir = File(ws.rawPath)
            val targetDir = if (normalizedSub.isEmpty()) baseDir else File(baseDir, normalizedSub)

            if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()

            val files = targetDir.listFiles() ?: return emptyList()
            return files.map { file ->
                val childRel = if (normalizedSub.isEmpty()) file.name else "$normalizedSub/${file.name}"
                FileItemInfo(
                    name = file.name,
                    relativePath = childRel,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

        return emptyList()
    }

    fun readFile(subPath: String): String {
        val ws = currentWorkspace ?: throw IllegalStateException("No active workspace selected")
        val normalizedSub = subPath.trim('/').replace('\\', '/')

        if (ws.isSaf && ws.uriString != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(ws.uriString))
                ?: throw IllegalArgumentException("Cannot access root directory")
            val targetDoc = findDocumentByPath(rootDoc, normalizedSub)
                ?: throw IllegalArgumentException("File not found: $subPath")

            val inputStream = context.contentResolver.openInputStream(targetDoc.uri)
                ?: throw IllegalArgumentException("Could not open input stream for $subPath")

            return inputStream.bufferedReader().use { it.readText() }
        } else if (ws.rawPath != null) {
            val targetFile = File(File(ws.rawPath), normalizedSub)
            if (!targetFile.exists()) throw IllegalArgumentException("File not found: $subPath")
            return targetFile.readText()
        }

        throw IllegalStateException("Invalid workspace configuration")
    }

    fun readLines(subPath: String, startLine: Int, endLine: Int): String {
        val fullContent = readFile(subPath)
        val lines = fullContent.split("\n")
        val safeStart = (startLine - 1).coerceAtLeast(0)
        val safeEnd = endLine.coerceAtMost(lines.size)

        if (safeStart >= lines.size || safeStart >= safeEnd) return ""

        return lines.subList(safeStart, safeEnd).joinToString("\n")
    }

    fun getFileStat(subPath: String): FileStatInfo {
        val ws = currentWorkspace ?: return FileStatInfo(subPath, false, false, 0, 0, "", "unknown")
        val normalizedSub = subPath.trim('/').replace('\\', '/')

        try {
            val content = readFile(subPath)
            val lines = content.split("\n")
            val sha256 = hashSha256(content)
            val mime = when {
                normalizedSub.endsWith(".kt") || normalizedSub.endsWith(".java") -> "text/x-source"
                normalizedSub.endsWith(".json") -> "application/json"
                normalizedSub.endsWith(".xml") -> "text/xml"
                normalizedSub.endsWith(".md") -> "text/markdown"
                normalizedSub.endsWith(".gradle") || normalizedSub.endsWith(".kts") -> "text/x-gradle"
                else -> "text/plain"
            }

            return FileStatInfo(
                relativePath = normalizedSub,
                exists = true,
                isDirectory = false,
                size = content.toByteArray().size.toLong(),
                lineCount = lines.size,
                sha256 = sha256,
                mimeType = mime
            )
        } catch (e: Exception) {
            return FileStatInfo(subPath, false, false, 0, 0, "", "unknown")
        }
    }

    fun writeFile(subPath: String, content: String): Boolean {
        val ws = currentWorkspace ?: return false
        val normalizedSub = subPath.trim('/').replace('\\', '/')

        if (ws.isSaf && ws.uriString != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(ws.uriString)) ?: return false
            val parts = normalizedSub.split('/')
            var currentDoc = rootDoc

            for (i in 0 until parts.size - 1) {
                val dirName = parts[i]
                var subDir = currentDoc.findFile(dirName)
                if (subDir == null) {
                    subDir = currentDoc.createDirectory(dirName)
                }
                if (subDir == null || !subDir.isDirectory) return false
                currentDoc = subDir
            }

            val fileName = parts.last()
            var fileDoc = currentDoc.findFile(fileName)
            if (fileDoc == null) {
                val mime = if (fileName.endsWith(".json")) "application/json" else "text/plain"
                fileDoc = currentDoc.createFile(mime, fileName)
            }
            if (fileDoc == null) return false

            val outputStream = context.contentResolver.openOutputStream(fileDoc.uri, "rwt") ?: return false
            outputStream.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            return true
        } else if (ws.rawPath != null) {
            val targetFile = File(File(ws.rawPath), normalizedSub)
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(content, Charsets.UTF_8)
            return true
        }
        return false
    }

    fun deleteFile(subPath: String): Boolean {
        val ws = currentWorkspace ?: return false
        val normalizedSub = subPath.trim('/').replace('\\', '/')

        if (ws.isSaf && ws.uriString != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(ws.uriString)) ?: return false
            val doc = findDocumentByPath(rootDoc, normalizedSub) ?: return false
            return doc.delete()
        } else if (ws.rawPath != null) {
            val targetFile = File(File(ws.rawPath), normalizedSub)
            return if (targetFile.exists()) targetFile.delete() else false
        }
        return false
    }

    fun createTurnSnapshot(turnId: String, affectedPaths: List<String>): TurnSnapshot {
        val snapshotFiles = mutableMapOf<String, String?>()
        for (path in affectedPaths) {
            try {
                val content = readFile(path)
                snapshotFiles[path] = content
            } catch (e: Exception) {
                snapshotFiles[path] = null // Was not existing before
            }
        }
        val snapshot = TurnSnapshot(turnId, System.currentTimeMillis(), snapshotFiles)
        snapshotHistory.add(snapshot)
        return snapshot
    }

    fun rollbackLastTurn(): Boolean {
        val snapshot = snapshotHistory.removeLastOrNull() ?: return false
        for ((path, originalContent) in snapshot.files) {
            if (originalContent == null) {
                deleteFile(path)
            } else {
                writeFile(path, originalContent)
            }
        }
        return true
    }

    private fun findDocumentByPath(root: DocumentFile, subPath: String): DocumentFile? {
        if (subPath.isEmpty()) return root
        val parts = subPath.split('/')
        var current: DocumentFile? = root
        for (part in parts) {
            if (current == null) return null
            current = current.findFile(part)
        }
        return current
    }

    private fun hashSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

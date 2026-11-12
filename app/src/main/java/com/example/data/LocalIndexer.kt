package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalIndexer(context: Context) {

    private val db = AppRoomDatabase.getDatabase(context)
    private val dao = db.fileIndexDao()

    suspend fun indexWorkspace(workspaceManager: WorkspaceManager): Int = withContext(Dispatchers.IO) {
        dao.clearIndex()
        val allFiles = getAllFilesRecursive(workspaceManager, "")
        var indexedCount = 0

        for (fileInfo in allFiles) {
            if (fileInfo.isDirectory) continue
            if (fileInfo.size > 2 * 1024 * 1024) continue // Skip files > 2MB

            try {
                val content = workspaceManager.readFile(fileInfo.relativePath)
                dao.insertFileIndex(
                    FileIndexEntity(
                        relativePath = fileInfo.relativePath,
                        content = content
                    )
                )
                indexedCount++
            } catch (e: Exception) {
                // Ignore binary/unreadable files
            }
        }
        indexedCount
    }

    suspend fun searchIndex(query: String): List<SearchResultSnippet> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val safeQuery = "*${query.replace("\"", "")}*"
        val results = dao.searchIndex(safeQuery)
        val snippets = mutableListOf<SearchResultSnippet>()

        for (item in results) {
            val lines = item.content.split("\n")
            lines.forEachIndexed { idx, line ->
                if (line.contains(query, ignoreCase = true)) {
                    snippets.add(
                        SearchResultSnippet(
                            relativePath = item.relativePath,
                            lineNumber = idx + 1,
                            lineContent = line.trim()
                        )
                    )
                }
            }
        }
        snippets.take(30)
    }

    suspend fun getIndexedCount(): Int = dao.getIndexedCount()

    private fun getAllFilesRecursive(workspaceManager: WorkspaceManager, currentPath: String): List<FileItemInfo> {
        val list = workspaceManager.listDirectory(currentPath)
        val result = mutableListOf<FileItemInfo>()

        for (item in list) {
            result.add(item)
            if (item.isDirectory) {
                result.addAll(getAllFilesRecursive(workspaceManager, item.relativePath))
            }
        }
        return result
    }
}

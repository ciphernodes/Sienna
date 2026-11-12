package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResultSnippet(
    val relativePath: String,
    val lineNumber: Int,
    val lineContent: String
)

class FtsIndexManager(context: Context) {

    private val dbHelper = FtsDbHelper(context)

    suspend fun indexWorkspace(workspaceManager: WorkspaceManager): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM file_index")
            val allFiles = getAllFilesRecursive(workspaceManager, "")
            var indexedCount = 0

            for (fileInfo in allFiles) {
                if (fileInfo.isDirectory) continue
                if (fileInfo.size > 2 * 1024 * 1024) continue // Skip files > 2MB

                try {
                    val content = workspaceManager.readFile(fileInfo.relativePath)
                    val values = ContentValues().apply {
                        put("relative_path", fileInfo.relativePath)
                        put("content", content)
                    }
                    db.insert("file_index", null, values)
                    indexedCount++
                } catch (e: Exception) {
                    // Skip unreadable binary files
                }
            }

            db.setTransactionSuccessful()
            indexedCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            db.endTransaction()
        }
    }

    suspend fun searchIndex(query: String): List<SearchResultSnippet> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<SearchResultSnippet>()
        val db = dbHelper.readableDatabase

        try {
            // Escape double quotes in FTS query
            val safeQuery = "\"${query.replace("\"", "\"\"")}\""
            val cursor = db.rawQuery(
                "SELECT relative_path, content FROM file_index WHERE file_index MATCH ? LIMIT 50",
                arrayOf(safeQuery)
            )

            cursor.use { c ->
                val pathIdx = c.getColumnIndexOrThrow("relative_path")
                val contentIdx = c.getColumnIndexOrThrow("content")

                while (c.moveToNext()) {
                    val path = c.getString(pathIdx)
                    val content = c.getString(contentIdx)

                    val lines = content.split("\n")
                    lines.forEachIndexed { idx, line ->
                        if (line.contains(query, ignoreCase = true)) {
                            results.add(
                                SearchResultSnippet(
                                    relativePath = path,
                                    lineNumber = idx + 1,
                                    lineContent = line.trim()
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results.take(30)
    }

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

    private class FtsDbHelper(context: Context) : SQLiteOpenHelper(context, "fts_code_index.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS file_index USING fts5(relative_path, content)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS file_index")
            onCreate(db)
        }
    }
}

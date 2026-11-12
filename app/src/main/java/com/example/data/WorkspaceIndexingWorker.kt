package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkspaceIndexingWorker(private val context: Context) {

    private val localIndexer = LocalIndexer(context)

    fun triggerAutoIndexing(workspaceManager: WorkspaceManager, onComplete: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = localIndexer.indexWorkspace(workspaceManager)
                onComplete(count)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(0)
            }
        }
    }
}

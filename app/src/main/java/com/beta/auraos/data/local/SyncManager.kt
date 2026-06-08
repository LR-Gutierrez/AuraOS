package com.beta.auraos.data.local

import android.content.Context
import android.util.Log
import com.beta.auraos.data.local.room.AppDatabase

class SyncManager(
    context: Context,
    private val database: AppDatabase,
    private val token: String
) {

    private val changeJournal = ChangeJournal(database, context)
    private val journalSyncManager = ChangeJournalSyncManager(database, changeJournal)

    suspend fun sync(): SyncResult {
        Log.d(TAG, "Starting sync")
        return when (val result = journalSyncManager.sync(token)) {
            is ChangeJournalSyncManager.SyncResult.Success -> {
                Log.d(TAG, "Sync completed successfully (${result.changesApplied} changes applied)")
                SyncResult.Success
            }
            is ChangeJournalSyncManager.SyncResult.Error -> {
                Log.w(TAG, "Journal sync failed: ${result.message}")
                SyncResult.Error(result.message)
            }
        }
    }

    sealed class SyncResult {
        data object Success : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}

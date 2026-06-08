package com.beta.auraos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.beta.auraos.data.local.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncCoordinator(
    private val context: Context,
    connectivityObserver: ConnectivityObserver,
    private val scope: CoroutineScope
) {

    private val sessionManager = SessionManager(context.applicationContext)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var isOnline = false

    private var pendingSync: Boolean
        get() = prefs.getBoolean(KEY_PENDING_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_PENDING_SYNC, value).apply()

    private var syncJob: Job? = null

    init {
        scope.launch {
            connectivityObserver.isOnline.collectLatest { online ->
                isOnline = online
                if (online) {
                    if (pendingSync) {
                        Log.d(TAG, "Back online — syncing pending mutations")
                        triggerSync()
                    } else {
                        triggerExitSync()
                    }
                }
            }
        }
    }

    fun onMutation() {
        Log.d(TAG, "Mutation recorded, isOnline=$isOnline")
        if (!isOnline) {
            pendingSync = true
            Log.d(TAG, "Offline — sync will run when connectivity is restored")
            return
        }
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_MS)
            triggerSync()
        }
    }

    private suspend fun triggerSync() {
        if (syncing || isApiInProgress) {
            Log.d(TAG, "Sync skipped: syncing=$syncing, apiInProgress=$isApiInProgress")
            return
        }
        syncing = true
        try {
            val token = sessionManager.getToken()
            if (token == null) {
                Log.w(TAG, "Cannot sync: no token available")
                return
            }
            val db = AppDatabase.getInstance(context)
            val result = SyncManager(context, db, token).sync()
            when {
                result is SyncManager.SyncResult.Success -> {
                    pendingSync = false
                    Log.d(TAG, "Journal sync completed successfully")
                }
                isAuthError(result) -> {
                    pendingSync = false
                    Log.w(TAG, "Sync failed with 401 — clearing pendingSync, token may be invalid")
                    sessionManager.clearToken()
                }
                else -> {
                    Log.w(TAG, "Journal sync did not complete successfully, will retry: $result")
                }
            }
            triggerExitSync()
        } catch (e: Exception) {
            Log.e(TAG, "Sync after mutation failed: ${e.message}")
        } finally {
            syncing = false
        }
    }

    private suspend fun triggerExitSync() {
        try {
            val db = AppDatabase.getInstance(context)
            val result = ExitSyncManager(db).syncPending()
            when (result) {
                is ExitSyncManager.SyncResult.Noop -> {}
                is ExitSyncManager.SyncResult.Success -> Log.d(TAG, "Exits synced: ${result.count}")
                is ExitSyncManager.SyncResult.Partial -> Log.d(TAG, "Exits partial: ${result.synced} synced, ${result.failed} failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exit sync failed: ${e.message}")
        }
    }

    private fun isAuthError(result: SyncManager.SyncResult): Boolean {
        return result is SyncManager.SyncResult.Error &&
                (result.message.contains("401") || result.message.contains("Unauthorized"))
    }

    companion object {
        private const val TAG = "SyncCoordinator"
        private const val DEBOUNCE_MS = 800L
        private const val PREFS_NAME = "aura_sync_coordinator_prefs"
        private const val KEY_PENDING_SYNC = "pending_sync"

        @Volatile
        private var syncing = false

        @Volatile
        var isApiInProgress = false
    }
}

package com.beta.auraos.data.local

import android.content.Context
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
    private var isOnline = false
    private var pendingSync = false
    private var syncJob: Job? = null

    init {
        scope.launch {
            connectivityObserver.isOnline.collectLatest { online ->
                isOnline = online
                if (online && pendingSync) {
                    Log.d(TAG, "Back online — syncing pending mutations")
                    pendingSync = false
                    triggerSync()
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
        val token = sessionManager.getToken()
        if (token == null) {
            Log.w(TAG, "Cannot sync: no token available")
            return
        }
        try {
            val db = AppDatabase.getInstance(context)
            SyncManager(context, db, token).sync()
        } catch (e: Exception) {
            Log.e(TAG, "Sync after mutation failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SyncCoordinator"
        private const val DEBOUNCE_MS = 800L
    }
}

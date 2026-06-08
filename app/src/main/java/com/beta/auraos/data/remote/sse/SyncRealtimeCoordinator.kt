package com.beta.auraos.data.remote.sse

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ChangeJournalSyncManager
import com.beta.auraos.data.local.ConnectivityObserver
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.SyncApi
import com.beta.auraos.data.remote.fcm.AuraFcmTokenHolder
import com.beta.auraos.data.remote.fcm.FcmTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class SyncRealtimeCoordinator(
    application: Application,
    private val connectivityObserver: ConnectivityObserver,
    private val sessionManager: SessionManager,
    private val changeJournal: ChangeJournal,
    private val syncNotificationManager: SyncNotificationManager,
    private val scope: CoroutineScope
) {
    private val pullMutex = Mutex()
    private val needsSync = AtomicBoolean(false)
    private var pullJob: Job? = null
    private var isInForeground = false
    private var lastHints: List<String>? = null
    private var tokenObserverJob: Job? = null

    private val appContext = application.applicationContext
    private val api: SyncApi = ApiClient.createApi()

    fun onJournalUpdated(cursor: String?, hints: List<String>?) {
        Log.d(TAG, "Journal updated: cursor=$cursor hints=$hints")

        if (hints != null) {
            lastHints = hints
        }

        needsSync.set(true)

        if (pullJob?.isActive != true) {
            pullJob = scope.launch(Dispatchers.IO) {
                drainLoop()
            }
        }
    }

    private fun observeForeground() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isInForeground = true
                            Log.d(TAG, "App in foreground")
                        }
                        Lifecycle.Event.ON_PAUSE -> {
                            isInForeground = false
                            Log.d(TAG, "App in background")
                        }
                        else -> {}
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "ProcessLifecycleOwner not available — assuming background (FCM wakeup): ${e.message}")
            isInForeground = false
        }
    }

    private fun observeTokenChanges() {
        tokenObserverJob?.cancel()
        tokenObserverJob = scope.launch {
            var lastToken: String? = sessionManager.getToken()

            if (lastToken != null) {
                registerCurrentFcmToken(lastToken)
            }

            while (true) {
                delay(TOKEN_POLL_MS)
                val current = sessionManager.getToken()
                when {
                    current == null && lastToken != null -> {
                        lastToken = null
                        Log.d(TAG, "Token cleared — unregistering FCM token")
                        unregisterFcmToken()
                    }
                    current != null && lastToken == null -> {
                        lastToken = current
                        Log.d(TAG, "Token acquired after login — registering FCM token")
                        registerCurrentFcmToken(current)
                    }
                    else -> {
                        lastToken = current
                    }
                }
            }
        }
    }

    private suspend fun registerCurrentFcmToken(authToken: String) {
        try {
            val fcmToken = AuraFcmTokenHolder.getToken()
            if (fcmToken == null) {
                Log.d(TAG, "FCM token not yet available, waiting...")
                val resolved = AuraFcmTokenHolder.awaitToken()
                doRegisterFcmToken(resolved, authToken)
            } else {
                doRegisterFcmToken(fcmToken, authToken)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register FCM token: ${e.message}")
        }
    }

    private suspend fun doRegisterFcmToken(fcmToken: String, authToken: String) {
        try {
            val response = api.registerFcmToken("Bearer $authToken", FcmTokenRequest(fcmToken))
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token registered successfully")
            } else {
                Log.w(TAG, "FCM token registration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM token registration error: ${e.message}")
        }
    }

    private suspend fun unregisterFcmToken() {
        try {
            val fcmToken = AuraFcmTokenHolder.getToken() ?: return
            val authToken = sessionManager.getToken()
            if (authToken == null) {
                Log.d(TAG, "No auth token — skipping FCM unregister")
                return
            }
            val response = api.unregisterFcmToken("Bearer $authToken", fcmToken)
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token unregistered successfully")
            } else {
                Log.w(TAG, "FCM token unregistration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM token unregistration error: ${e.message}")
        }
    }

    private suspend fun drainLoop() {
        pullMutex.withLock {
            while (needsSync.getAndSet(false)) {
                Log.d(TAG, "Drain loop: pulling remote changes")
                val token = sessionManager.getToken()
                if (token == null) {
                    Log.w(TAG, "Cannot pull: no token")
                    return@withLock
                }

                val db = AppDatabase.getInstance(appContext)
                val manager = ChangeJournalSyncManager(db, changeJournal)
                val result = manager.pullOnly(token)

                when (result) {
                    is ChangeJournalSyncManager.SyncResult.Success -> {
                        Log.d(TAG, "Pull completed (${result.changesApplied} changes)")
                        if (result.changesApplied > 0 && !isInForeground) {
                            syncNotificationManager.notifySyncCompleted(lastHints, result.changesApplied)
                        }
                    }
                    is ChangeJournalSyncManager.SyncResult.Error -> {
                        Log.w(TAG, "Pull failed: ${result.message}")
                    }
                }
            }
        }
    }

    init {
        observeForeground()
        observeTokenChanges()
    }

    companion object {
        private const val TAG = "SyncRealtimeCoordinator"
        private const val TOKEN_POLL_MS = 10_000L
    }
}

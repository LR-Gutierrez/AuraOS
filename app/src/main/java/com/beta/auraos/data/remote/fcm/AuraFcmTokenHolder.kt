package com.beta.auraos.data.remote.fcm

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AuraFcmTokenHolder {

    @Volatile
    private var cachedToken: String? = null

    private var pendingCallbacks: MutableList<(String) -> Unit> = mutableListOf()
    private var resolved = false

    init {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                cachedToken = token
                resolved = true
                synchronized(pendingCallbacks) {
                    pendingCallbacks.forEach { it(token) }
                    pendingCallbacks.clear()
                }
                Log.d(TAG, "FCM token resolved: ${token.take(16)}...")
                subscribeToTopic()
            } else {
                Log.w(TAG, "Failed to get FCM token", task.exception)
            }
        }
    }

    fun getToken(): String? = cachedToken

    suspend fun awaitToken(): String {
        cachedToken?.let { return it }
        return suspendCancellableCoroutine { cont ->
            synchronized(pendingCallbacks) {
                if (cachedToken != null) {
                    cont.resume(cachedToken!!)
                } else {
                    pendingCallbacks.add { token ->
                        if (cont.isActive) cont.resume(token)
                    }
                }
            }
        }
    }

    private fun subscribeToTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Subscribed to topic: $TOPIC")
                } else {
                    Log.w(TAG, "Failed to subscribe to topic: $TOPIC", task.exception)
                }
            }
    }

    internal fun onTokenRefreshed(token: String) {
        cachedToken = token
        Log.d(TAG, "FCM token updated: ${token.take(16)}...")
        subscribeToTopic()
    }

    private const val TAG = "AuraFcmToken"
    private const val TOPIC = "todos"
}

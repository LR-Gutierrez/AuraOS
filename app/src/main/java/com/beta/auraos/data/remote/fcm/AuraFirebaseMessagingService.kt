package com.beta.auraos.data.remote.fcm

import android.util.Log
import com.beta.auraos.AuraApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray

class AuraFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received: ${message.messageId}")

        val data = message.data ?: run {
            Log.w(TAG, "FCM message has no data payload")
            return
        }

        if (data[TYPE_KEY] != EVENT_JOURNAL_UPDATED) {
            Log.d(TAG, "Ignoring FCM message type: ${data[TYPE_KEY]}")
            return
        }

        val cursor = data[CURSOR_KEY]
        val hintsRaw = data[HINTS_KEY]

        Log.d(TAG, "Journal updated via FCM: cursor=$cursor hints=$hintsRaw")

        val hints = parseHints(hintsRaw)

        val coordinator = AuraApplication.instance.syncRealtimeCoordinator
        if (coordinator != null) {
            coordinator.onJournalUpdated(cursor, hints)
        } else {
            Log.w(TAG, "SyncRealtimeCoordinator not initialized yet")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(16)}...")
        AuraFcmTokenHolder.onTokenRefreshed(token)
    }

    private fun parseHints(raw: String?): List<String>? {
        if (raw == null) return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it, "") }
                .filter { it.isNotBlank() }
                .ifEmpty { null }
        } catch (_: Exception) {
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null }
        }
    }

    companion object {
        private const val TAG = "AuraFcmMessaging"
        private const val TYPE_KEY = "type"
        private const val CURSOR_KEY = "latestCursor"
        private const val HINTS_KEY = "hints"
        private const val EVENT_JOURNAL_UPDATED = "journal_updated"
    }
}

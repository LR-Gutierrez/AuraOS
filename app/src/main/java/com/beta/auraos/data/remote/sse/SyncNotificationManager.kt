package com.beta.auraos.data.remote.sse

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.beta.auraos.HomeActivity
import com.beta.auraos.R
import java.util.concurrent.atomic.AtomicInteger

class SyncNotificationManager(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var lastNotificationId = AtomicInteger(SYNC_NOTIFICATION_ID_BASE)

    init {
        createChannel()
    }

    private fun createChannel() {
        // Channels exist from Android 8; on 7 notifications post without one.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notifySyncCompleted(hints: List<String>?, changesApplied: Int) {
        if (changesApplied <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val title = "Sincronización completada"
        val body = buildBody(hints, changesApplied)

        val intent = Intent(appContext, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(NOTIFICATION_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val id = lastNotificationId.getAndIncrement()
        notificationManager.notify(id, notification)
    }

    private fun buildBody(hints: List<String>?, changesApplied: Int): String {
        val types = hints?.mapNotNull { hint ->
            when (hint) {
                "vehicle_entry" -> "vehículos"
                "branch" -> "sucursales"
                "membership" -> "membresías"
                else -> null
            }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        return if (types != null) {
            "Se actualizaron $changesApplied registro(s) en: $types"
        } else {
            "Se actualizaron $changesApplied registro(s) nuevos"
        }
    }

    companion object {
        private const val CHANNEL_ID = "aura_sync_updates"
        private const val CHANNEL_NAME = "Actualizaciones de datos"
        private const val CHANNEL_DESC = "Notificaciones cuando hay nuevos datos sincronizados"
        private const val REQUEST_CODE = 1001
        private const val SYNC_NOTIFICATION_ID_BASE = 2000
        private val NOTIFICATION_COLOR = 0xFFF5A524.toInt()
    }
}

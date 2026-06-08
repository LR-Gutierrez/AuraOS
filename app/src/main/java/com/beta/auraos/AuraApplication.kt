package com.beta.auraos

import android.app.Application
import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ConnectivityObserver
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.remote.fcm.AuraFcmTokenHolder
import com.beta.auraos.data.remote.sse.SyncNotificationManager
import com.beta.auraos.data.remote.sse.SyncRealtimeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AuraApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var connectivityObserver: ConnectivityObserver
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var changeJournal: ChangeJournal
        private set
    lateinit var syncNotificationManager: SyncNotificationManager
        private set
    lateinit var syncRealtimeCoordinator: SyncRealtimeCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        connectivityObserver = ConnectivityObserver(this)
        sessionManager = SessionManager(this)
        changeJournal = ChangeJournal(AppDatabase.getInstance(this), this)
        syncNotificationManager = SyncNotificationManager(this)

        AuraFcmTokenHolder

        syncRealtimeCoordinator = SyncRealtimeCoordinator(
            application = this,
            connectivityObserver = connectivityObserver,
            sessionManager = sessionManager,
            changeJournal = changeJournal,
            syncNotificationManager = syncNotificationManager,
            scope = appScope
        )
    }

    companion object {
        lateinit var instance: AuraApplication
            private set
    }
}

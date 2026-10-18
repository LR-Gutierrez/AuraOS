package com.beta.auraos

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ConnectivityObserver
import com.beta.auraos.data.local.SeedDataInitializer
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.remote.fcm.AuraFcmTokenHolder
import com.beta.auraos.data.remote.sse.SyncNotificationManager
import com.beta.auraos.data.remote.sse.SyncRealtimeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        // Apply the saved theme before any activity inflates; without a choice, follow the system.
        AppCompatDelegate.setDefaultNightMode(
            when (sessionManager.darkModePreference()) {
                true -> AppCompatDelegate.MODE_NIGHT_YES
                false -> AppCompatDelegate.MODE_NIGHT_NO
                null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
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

        // Demo user, branch and entries are for development only: a release install starts empty
        // and signs in against the backend.
        if (BuildConfig.SEED_DEMO_DATA) appScope.launch(Dispatchers.IO) {
            SeedDataInitializer(this@AuraApplication, sessionManager, AppDatabase.getInstance(this@AuraApplication)).seedIfNeeded()
        }
    }

    companion object {
        lateinit var instance: AuraApplication
            private set
    }
}

package com.beta.auraos

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.local.ChangeJournalProvider
import com.beta.auraos.data.local.ConnectivityObserver
import com.beta.auraos.data.local.SyncCoordinator
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.ui.widget.ConnectivityBannerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var connectivityObserver: ConnectivityObserver
    private var connectivityBannerView: ConnectivityBannerView? = null
    private var connectivityJob: Job? = null
    private lateinit var syncCoordinator: SyncCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityObserver = ConnectivityObserver(this)
        ChangeJournalProvider.initialize(this, AppDatabase.getInstance(this))
        syncCoordinator = SyncCoordinator(this, connectivityObserver, lifecycleScope)
        ChangeJournalProvider.get()?.onMutation = {
            syncCoordinator.onMutation()
        }
    }

    override fun setContentView(view: View) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val banner = ConnectivityBannerView(this)
        container.addView(banner)
        connectivityBannerView = banner

        super.setContentView(container)
    }

    override fun onStart() {
        super.onStart()
        connectivityJob?.cancel()
        connectivityJob = lifecycleScope.launch {
            connectivityObserver.isOnline.collectLatest { online ->
                connectivityBannerView?.let { banner ->
                    if (online) banner.showOnline() else banner.showOffline()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        connectivityJob?.cancel()
        connectivityBannerView?.cleanup()
    }
}

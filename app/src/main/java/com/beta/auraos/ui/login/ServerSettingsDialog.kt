package com.beta.auraos.ui.login

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.AuraApplication
import com.beta.auraos.R
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.HealthApi
import com.beta.auraos.data.remote.HealthResponse
import com.beta.auraos.data.remote.ServerConfig
import com.beta.auraos.databinding.DialogServerBinding
import com.beta.auraos.util.ScreenLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * "Server": shows where the app syncs (if anywhere), tests an address and saves it. The app
 * is complete without a server, so an empty field is a valid choice, not an error.
 */
class ServerSettingsDialog(private val activity: AppCompatActivity) {

    private val sessionManager get() = AuraApplication.instance.sessionManager
    private lateinit var binding: DialogServerBinding
    private var checkJob: Job? = null

    fun show() {
        binding = DialogServerBinding.inflate(activity.layoutInflater)
        val current = ServerConfig.baseUrl()?.toString()
        binding.etServer.setText(current?.removeSuffix("/").orEmpty())

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.server_title)
            .setView(binding.root)
            .setPositiveButton(R.string.server_save, null)
            .setNeutralButton(R.string.server_test, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()

        check(current)

        // Set after show() so a tap on Test or an invalid Save keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val text = binding.etServer.text.toString()
            if (text.isBlank()) check(null)
            else ServerConfig.normalize(text)?.let { check(it) } ?: showStatus(activity.getString(R.string.server_invalid))
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = binding.etServer.text.toString()
            val chosen = if (text.isBlank()) "" else ServerConfig.normalize(text)
            if (chosen == null) {
                showStatus(activity.getString(R.string.server_invalid))
                return@setOnClickListener
            }
            if (chosen == current.orEmpty()) {
                dialog.dismiss()
                return@setOnClickListener
            }
            confirmIfDeviceInUse {
                save(chosen)
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { checkJob?.cancel() }
    }

    /**
     * On a device already in use, a new address decides where its pending records go, so the
     * owner confirms with the screen lock. A phone without a lock cannot be protected anyway.
     */
    private fun confirmIfDeviceInUse(onConfirmed: () -> Unit) {
        if (sessionManager.isDeviceEmpty() || !ScreenLock.isAvailable(activity)) {
            onConfirmed()
            return
        }
        ScreenLock.confirm(
            activity,
            activity.getString(R.string.server_confirm_title),
            activity.getString(R.string.server_confirm_body),
            onConfirmed,
        )
    }

    private fun save(url: String) {
        ServerConfig.save(url)
        // The session token belongs to the previous server.
        sessionManager.clearToken()
        Toast.makeText(
            activity,
            if (url.isEmpty()) R.string.server_removed else R.string.server_saved,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun check(url: String?) {
        checkJob?.cancel()
        if (url == null) {
            showStatus(activity.getString(R.string.server_none))
            return
        }
        showStatus(activity.getString(R.string.network_checking))
        checkJob = activity.lifecycleScope.launch {
            val health = withContext(Dispatchers.IO) {
                runCatching {
                    Retrofit.Builder()
                        .baseUrl(url)
                        .client(ApiClient.okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(HealthApi::class.java)
                        .health()
                }.getOrNull()
            }
            showStatus(health?.let(::describe) ?: activity.getString(R.string.network_offline))
        }
    }

    private fun describe(health: HealthResponse): String {
        val status = when (health.status) {
            "ok" -> activity.getString(R.string.health_status_ok)
            "error" -> activity.getString(R.string.health_status_error)
            else -> health.status
        }
        val database = when (health.database) {
            "ok" -> activity.getString(R.string.health_db_ok)
            "error" -> activity.getString(R.string.health_db_error)
            else -> health.database
        }
        val seconds = health.uptime.toLong()
        val uptime = if (seconds >= 3600) "%dh %dm".format(seconds / 3600, (seconds % 3600) / 60)
        else "%dm %ds".format(seconds / 60, seconds % 60)
        return activity.getString(R.string.network_health_format, status, database, uptime)
    }

    private fun showStatus(text: String) {
        binding.tvServerStatus.text = text
    }
}

package com.beta.auraos.ui.setup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.BaseActivity
import com.beta.auraos.DashboardActivity
import com.beta.auraos.R
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivitySetupBinding
import com.beta.auraos.util.ScreenLock
import com.beta.auraos.util.TabNavigator

/**
 * No-server setup: creates the device's first account and branch locally. Offered only on an
 * empty device and confirmed with the phone's screen lock, so a stray tap or someone holding
 * the phone cannot mint an administrator on a device that is already in use.
 */
class SetupActivity : BaseActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var viewModel: SetupViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        if (!sessionManager.isDeviceEmpty()) {
            Toast.makeText(this, R.string.setup_not_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            SetupViewModelFactory(sessionManager, BranchRepository(ApiClient.createApi<BranchApi>(), db)),
        )[SetupViewModel::class.java]

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val basePadding = binding.setupRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.setupRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = basePadding + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreate.setOnClickListener { onCreateTapped() }

        viewModel.state.observe(this) { state ->
            val saving = state is SetupState.Saving
            binding.btnCreate.isEnabled = !saving
            binding.btnCreate.setText(if (saving) R.string.setup_creating else R.string.setup_create)
            when (state) {
                is SetupState.Invalid -> showErrors(state.errors)
                is SetupState.Done -> openDashboard(state)
                is SetupState.Failed -> Toast.makeText(
                    this,
                    if (state.message == "device_not_empty") R.string.setup_not_empty else R.string.setup_failed,
                    Toast.LENGTH_LONG,
                ).show()
                else -> Unit
            }
        }
    }

    private fun input() = SetupInput(
        name = binding.etName.text.toString(),
        email = binding.etEmail.text.toString(),
        password = binding.etPassword.text.toString(),
        confirm = binding.etConfirm.text.toString(),
        branchName = binding.etBranch.text.toString(),
        lightCapacity = binding.etCapLight.text.toString(),
        heavyCapacity = binding.etCapHeavy.text.toString(),
        motoCapacity = binding.etCapMoto.text.toString(),
        lightRate = binding.etRateLight.text.toString(),
        heavyRate = binding.etRateHeavy.text.toString(),
        motoRate = binding.etRateMoto.text.toString(),
    )

    private fun onCreateTapped() {
        val errors = viewModel.validate(input())
        showErrors(errors)
        if (!errors.isValid) return
        confirmWithScreenLock { viewModel.create(input()) }
    }

    private fun showErrors(errors: SetupErrors) {
        fun bind(view: TextView, res: Int?) {
            view.visibility = if (res == null) View.GONE else View.VISIBLE
            if (res != null) view.setText(res)
        }
        bind(binding.etNameError, errors.name)
        bind(binding.etEmailError, errors.email)
        bind(binding.etPasswordError, errors.password)
        bind(binding.etConfirmError, errors.confirm)
        bind(binding.etBranchError, errors.branchName)
        bind(binding.capacityError, errors.capacity)
        bind(binding.ratesError, errors.rates)
        if (!errors.isValid) binding.scroll.smoothScrollTo(0, 0)
    }

    /** Runs [onConfirmed] after the phone's screen lock (or a biometric) confirms the owner. */
    private fun confirmWithScreenLock(onConfirmed: () -> Unit) {
        if (!ScreenLock.isAvailable(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.setup_no_lock_title)
                .setMessage(R.string.setup_no_lock_body)
                .setPositiveButton(R.string.setup_open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            return
        }
        ScreenLock.confirm(this, getString(R.string.setup_confirm_title), getString(R.string.setup_confirm_body), onConfirmed)
    }

    private fun openDashboard(done: SetupState.Done) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(TabNavigator.EXTRA_TAB_ID, R.id.nav_dashboard)
            putExtra(DashboardActivity.EXTRA_EMAIL, done.user.email)
            putExtra(DashboardActivity.EXTRA_DISPLAY_NAME, done.user.displayName)
            putExtra(DashboardActivity.EXTRA_PASSWORD, done.password)
            putExtra(DashboardActivity.EXTRA_USER_ID, done.user.userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

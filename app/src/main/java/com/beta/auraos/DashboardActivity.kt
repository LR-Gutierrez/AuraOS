package com.beta.auraos

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityDashboardBinding
import com.beta.auraos.ui.branches.BranchListActivity
import com.beta.auraos.ui.dashboard.DashboardUiState
import com.beta.auraos.ui.dashboard.DashboardViewModel
import com.beta.auraos.ui.dashboard.DashboardViewModelFactory
import com.beta.auraos.util.BorderGlowDrawable
import com.beta.auraos.util.BottomNavIndicator
import com.beta.auraos.util.TabNavigator
import com.google.android.material.color.MaterialColors
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.BiometricKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.lifecycle.lifecycleScope

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager
    private lateinit var branchRepository: BranchRepository
    private var viewModel: DashboardViewModel? = null

    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        biometricKeyManager = BiometricKeyManager(this)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.selectedItemId = R.id.nav_dashboard

        BottomNavIndicator.attach(
            bottomNav = binding.bottomNavigation,
            indicator = binding.bottomNavIndicator,
            initialItemId = R.id.nav_dashboard,
        )

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = R.id.nav_dashboard,
                    )
                    true
                }
                R.id.nav_profile -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = R.id.nav_profile,
                    )
                    TabNavigator.navigate(this, R.id.nav_profile)
                    true
                }
                else -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = item.itemId,
                    )
                    TabNavigator.navigate(this, item.itemId)
                    true
                }
            }
        }

        val email = intent.getStringExtra(EXTRA_EMAIL)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        if (email != null && userId != null && hasBiometricHardware()) {
            val keyExists = biometricKeyManager.isKeyGenerated()
            val alreadyEnrolled = sessionManager.isBiometricEnrolled() && keyExists
            if (!alreadyEnrolled) {
                if (sessionManager.isBiometricEnrolled() && !keyExists) {
                    sessionManager.setBiometricEnrolled(false)
                }
                promptBiometricEnrollment(email, userId)
            }
        }

        val branchApi = ApiClient.createApi<BranchApi>()
        branchRepository = BranchRepository(branchApi)

        setupBranchSelector()
        loadBranches()

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val bottomNavH = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = bottomNavH + navBarInsets.bottom)
            insets
        }

        val cornerRadiusPx = resources.getDimension(R.dimen.card_corner_radius_large)
        val successColor = MaterialColors.getColor(binding.cardRevenue, R.attr.semanticSuccessCustom)
        val primaryColor = MaterialColors.getColor(binding.cardMemberships, android.R.attr.colorPrimary)

        startBorderGlow(binding.glowRevenue, successColor, cornerRadiusPx)
        startBorderGlow(binding.glowMemberships, primaryColor, cornerRadiusPx)

        binding.cardBranches.setOnClickListener {
            startActivity(android.content.Intent(this, BranchListActivity::class.java))
        }

        binding.cardMemberships.setOnClickListener {
            startActivity(android.content.Intent(this, com.beta.auraos.ui.memberships.MembershipListActivity::class.java))
        }

        binding.cardExit.setOnClickListener {
            startActivity(android.content.Intent(this, com.beta.auraos.ui.exit.ExitActivity::class.java))
        }

        setupViewModel()
        observeDashboard()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun setupBranchSelector() {
        binding.btnBranchSelector.setOnClickListener { showBranchSelector() }
        binding.tvBranchName.setOnClickListener { showBranchSelector() }
    }

    private fun loadBranches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = branchRepository.getBranches()
            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        branches = result.data
                        restoreOrSelectBranch()
                    }
                    is Result.Error -> {
                        binding.tvBranchName.text = getString(R.string.branch_load_error)
                    }
                }
            }
        }
    }

    private fun restoreOrSelectBranch() {
        val savedId = sessionManager.getSelectedBranchId()
        val saved = if (savedId != null) branches.find { it.id == savedId } else null

        if (saved != null) {
            selectedBranch = saved
            binding.tvBranchName.text = saved.name
        } else if (branches.isNotEmpty()) {
            selectedBranch = branches.first()
            sessionManager.saveSelectedBranchId(selectedBranch!!.id)
            binding.tvBranchName.text = selectedBranch!!.name
        } else {
            binding.tvBranchName.text = getString(R.string.branch_no_branch)
        }
        loadDashboard()
    }

    private fun showBranchSelector() {
        if (branches.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_branches_title))
                .setMessage(getString(R.string.dialog_no_branches_message))
                .setPositiveButton(getString(R.string.dialog_setup)) { _, _ ->
                    startActivity(android.content.Intent(this, BranchListActivity::class.java))
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
            return
        }

        val names = branches.map { it.name }.toTypedArray()
        val selectedIndex = if (selectedBranch != null) branches.indexOf(selectedBranch) else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_branch_title))
            .setSingleChoiceItems(names, if (selectedIndex >= 0) selectedIndex else 0) { dialog, which ->
                val branch = branches[which]
                selectedBranch = branch
                sessionManager.saveSelectedBranchId(branch.id)
                binding.tvBranchName.text = branch.name
                dialog.dismiss()
                loadDashboard()
            }
            .setPositiveButton(getString(R.string.dialog_manage_branches)) { _, _ ->
                startActivity(android.content.Intent(this, BranchListActivity::class.java))
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun setupViewModel() {
        val token = sessionManager.getToken() ?: return
        val repository = DashboardRepository(token)
        val factory = DashboardViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
    }

    private fun observeDashboard() {
        val vm = viewModel ?: return
        vm.uiState.observe(this) { state ->
            when (state) {
                is DashboardUiState.Success -> bindData(state.data)
                is DashboardUiState.Error -> showError(state.message)
                is DashboardUiState.Loading -> { }
            }
        }
    }

    private fun loadDashboard() {
        val vm = viewModel
        if (vm == null) {
            showError("Sesión no iniciada. Inicia sesión para ver el dashboard.")
            return
        }
        val branchId = sessionManager.getSelectedBranchId()
        if (branchId != null) {
            vm.loadDashboard(branchId)
        }
    }

    private fun bindData(data: DashboardResponse) {
        val percent = data.occupancyPercent.toInt()
        binding.tvOccupancyTitle.text = getString(R.string.dashboard_occupancy_title, percent)
        binding.tvBaysCounter.text = getString(
            R.string.dashboard_bays_counter,
            data.occupiedBays,
            data.totalBays
        )
        binding.pbOccupancy.progress = percent

        val trend = data.trendPercent
        if (trend >= 0) {
            binding.tvTrend.text = getString(R.string.dashboard_trend_up, trend.toInt())
            binding.tvTrend.setTextColor(
                MaterialColors.getColor(binding.tvTrend, R.attr.semanticSuccessCustom)
            )
        } else {
            binding.tvTrend.text = getString(R.string.dashboard_trend_down, (-trend).toInt())
            binding.tvTrend.setTextColor(
                MaterialColors.getColor(binding.tvTrend, com.google.android.material.R.attr.colorError)
            )
        }

        binding.tvRevenueAmount.text = getString(R.string.dashboard_revenue_amount, data.dailyRevenue)
        binding.tvMembershipsCount.text = getString(
            R.string.dashboard_memberships_count,
            data.activeMemberships,
            data.totalMemberships
        )

        binding.badgeCritical.text = getString(R.string.dashboard_badge_critical, data.criticalAlertsCount)
        binding.badgeCritical.visibility = if (data.criticalAlertsCount > 0) View.VISIBLE else View.GONE

        if (data.expiringMemberships.isNotEmpty()) {
            val m = data.expiringMemberships[0]
            binding.alertTitle1.text = getString(R.string.dashboard_alert_membership_title, m.memberName)
            binding.alertSubtitle1.text = getString(R.string.dashboard_alert_membership_subtitle, m.tier, m.timeLeft)
        } else {
            binding.alertTitle1.text = getString(R.string.dashboard_alert_none)
            binding.alertSubtitle1.text = ""
        }

        if (data.vipArrivals.isNotEmpty()) {
            val v = data.vipArrivals[0]
            binding.alertTitle2.text = getString(R.string.dashboard_alert_vip_title, v.guestName)
            binding.alertSubtitle2.text = getString(R.string.dashboard_alert_vip_subtitle, v.slot)
        } else {
            binding.alertTitle2.text = getString(R.string.dashboard_alert_vip_none)
            binding.alertSubtitle2.text = ""
        }
    }

    private fun showError(message: String) {
        if (message.contains("Sesión expirada") || message.contains("sesión") ||
            message.contains("Session expired") || message.contains("session")) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, getString(R.string.dashboard_load_error, message), Toast.LENGTH_SHORT).show()
    }

    private fun startBorderGlow(overlay: View, color: Int, cornerRadiusPx: Float) {
        val drawable = BorderGlowDrawable(color, cornerRadiusPx)
        overlay.background = drawable

        val updater = object : Runnable {
            override fun run() {
                drawable.setPhase(System.nanoTime() / 3e9f)
                overlay.postOnAnimation(this)
            }
        }
        overlay.postOnAnimation(updater)
    }

    private fun hasBiometricHardware(): Boolean {
        return try {
            val mgr = BiometricManager.from(this)
            mgr.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun promptBiometricEnrollment(email: String, userId: String) {
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.biometric_enroll_title))
            .setMessage(getString(R.string.biometric_enroll_message))
            .setPositiveButton(getString(R.string.biometric_enroll_activate)) { _, _ ->
                enrollBiometric(email, password, userId)
            }
            .setNegativeButton(getString(R.string.biometric_enroll_later), null)
            .show()
    }

    private fun enrollBiometric(email: String, password: String, userId: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    biometricKeyManager.generateKeyPair()
                    val publicKey = biometricKeyManager.getPublicKey()
                        ?: return@withContext Result.Error(java.io.IOException(getString(R.string.biometric_key_generate_error)))

                    val token = sessionManager.getToken()
                        ?: return@withContext Result.Error(java.io.IOException(getString(R.string.biometric_session_expired)))

                    val dataSource = LoginDataSource(sessionManager)
                    dataSource.biometricEnroll(token, userId, publicKey)
                } catch (e: Exception) {
                    biometricKeyManager.deleteKey()
                    Result.Error(java.io.IOException(e.localizedMessage ?: getString(R.string.biometric_enrolled_error)))
                }
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        sessionManager.saveCredentials(email, password)
                        sessionManager.setBiometricEnrolled(true)
                        Toast.makeText(this@DashboardActivity, getString(R.string.biometric_enrolled_success), Toast.LENGTH_SHORT).show()
                    }
                    is Result.Error -> {
                        biometricKeyManager.deleteKey()
                        Toast.makeText(this@DashboardActivity, result.exception.localizedMessage ?: getString(R.string.biometric_enrolled_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}

package com.beta.auraos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.audit.FeeCalculator
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.DashboardCalculator
import com.beta.auraos.data.local.DashboardLocalDataSource
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityDashboardBinding
import com.beta.auraos.databinding.ItemPcBranchRowBinding
import com.beta.auraos.databinding.ItemPcVehicleRowBinding
import com.beta.auraos.ui.branches.BranchListActivity
import com.beta.auraos.ui.dashboard.DashboardUiState
import com.beta.auraos.ui.dashboard.DashboardViewModel
import com.beta.auraos.ui.dashboard.DashboardViewModelFactory
import com.beta.auraos.ui.dashboard.HomeSnapshot
import com.beta.auraos.ui.exit.ExitActivity
import com.beta.auraos.ui.memberships.MembershipListActivity
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PcUi
import com.beta.auraos.util.TabNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager
    private lateinit var viewModel: DashboardViewModel

    private var branches: List<Branch> = emptyList()
    private var online = true
    private var lastFromCache = false
    private var selectedBranch: Branch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        biometricKeyManager = BiometricKeyManager(this)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_dashboard)
        PcUi.underline(binding.btnSeeAll)
        binding.tvToday.text = ParkingFormat.todayLabel()

        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
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

        binding.btnRegisterEntry.setOnClickListener {
            TabNavigator.navigate(this, R.id.nav_register, R.id.nav_dashboard)
        }
        binding.btnRegisterExit.setOnClickListener { openExit(null) }
        binding.btnSeeAll.setOnClickListener { openExit(null) }
        binding.cardKiosk.setOnClickListener {
            startActivity(Intent(this, KioskActivity::class.java))
        }
        binding.rowMembershipAlert.setOnClickListener {
            startActivity(Intent(this, MembershipListActivity::class.java))
        }

        setupViewModel()
        observeViewModel()
        // Figures are always computed locally (local-first); "offline" depends on the network only.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectivityObserver.isOnline.collect {
                    online = it
                    updateCachedBadge()
                }
            }
        }
        viewModel.loadBranches()
    }

    override fun onResume() {
        super.onResume()
        if (branches.isNotEmpty()) loadDashboard()
    }

    private fun setupViewModel() {
        val db = AppDatabase.getInstance(this)
        val repository = DashboardRepository(
            sessionManager.getToken(),
            DashboardLocalDataSource(db),
            DashboardCalculator(db),
        )
        val factory = DashboardViewModelFactory(
            repository,
            MembershipRepository(db),
            VehicleEntryRepository(db),
            BranchRepository(ApiClient.createApi<BranchApi>(), db),
            db,
        )
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.branches.observe(this) { list ->
            if (list == null) return@observe
            branches = list
            val savedId = sessionManager.getSelectedBranchId()
            selectedBranch = list.find { it.id == savedId } ?: list.firstOrNull()
            selectedBranch?.let { sessionManager.saveSelectedBranchId(it.id) }
            renderBranchChips()
            renderBranchName()
            loadDashboard()
        }
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is DashboardUiState.Success -> bindData(state.data, state.isFromCache)
                is DashboardUiState.Error -> showError(state.message)
                is DashboardUiState.Loading -> Unit
            }
        }
        viewModel.home.observe(this) { bindHome(it) }
    }

    private fun loadDashboard() {
        selectedBranch?.let { viewModel.loadDashboard(it.id) }
    }

    private fun selectBranch(branch: Branch) {
        if (branch.id == selectedBranch?.id) return
        selectedBranch = branch
        sessionManager.saveSelectedBranchId(branch.id)
        renderBranchChips()
        renderBranchName()
        loadDashboard()
    }

    private fun renderBranchChips() {
        val row = binding.llBranchChips
        row.removeAllViews()
        if (branches.isEmpty()) {
            PcUi.addChip(row, getString(R.string.pc_no_branches_chip), checked = false) {
                startActivity(Intent(this, BranchListActivity::class.java))
            }
            return
        }
        branches.forEach { branch ->
            PcUi.addChip(row, branch.name, checked = branch.id == selectedBranch?.id) {
                selectBranch(branch)
            }
        }
    }

    private fun renderBranchName() {
        val name = selectedBranch?.name ?: getString(R.string.branch_no_branch)
        binding.tvOccupancyLabel.text = getString(R.string.pc_occupancy_label, name)
        binding.tvKioskSub.text = getString(R.string.pc_kiosk_sub, name)
        // Capacity from the cached branch, so the hero reads "00 / 90" before the server answers.
        selectedBranch?.let { b ->
            val capacity = b.lightVehicleCapacity + b.heavyVehicleCapacity + b.motorcycleCapacity
            binding.tvCapacity.text = getString(R.string.pc_capacity, capacity)
        }
    }

    private fun bindData(data: DashboardResponse, isFromCache: Boolean) {
        val percent = data.occupancyPercent.toInt().coerceIn(0, 100)
        binding.tvOccupied.text = ParkingFormat.pad2(data.occupiedBays)
        binding.tvCapacity.text = getString(R.string.pc_capacity, data.totalBays)
        binding.pbOccupancy.setProgressCompat(percent, true)
        binding.tvOccupancyPct.text = getString(R.string.pc_occupancy_pct, percent)
        val free = (data.totalBays - data.occupiedBays).coerceAtLeast(0)
        binding.tvFree.text = resources.getQuantityString(R.plurals.pc_free, free, free)
        binding.tvRevenueToday.text = ParkingFormat.money(data.dailyRevenue, selectedBranch?.currency)
        lastFromCache = isFromCache
        updateCachedBadge()

        val expiring = data.expiringMemberships
        binding.rowMembershipAlert.visibility = if (expiring.isEmpty()) View.GONE else View.VISIBLE
        expiring.firstOrNull()?.let { m ->
            binding.tvMembershipAlertLabel.text =
                resources.getQuantityString(R.plurals.pc_memberships_expiring, expiring.size, ParkingFormat.pad2(expiring.size))
            binding.tvMembershipAlert.text =
                getString(R.string.pc_membership_alert, m.memberName, m.tier, m.timeLeft)
        }
    }

    private fun updateCachedBadge() {
        binding.cachedBadge.visibility = if (lastFromCache && !online) View.VISIBLE else View.GONE
    }

    private fun bindHome(home: HomeSnapshot) {
        binding.tvEntriesToday.text = ParkingFormat.pad2(home.entriesToday)
        binding.tvExitsToday.text = ParkingFormat.pad2(home.exitsToday)

        // All branches
        binding.tvTotalVehicles.text =
            home.branches.sumOf { it.occupied }.let { resources.getQuantityString(R.plurals.pc_vehicles_count, it, it) }
        val rows = binding.llBranchRows
        rows.removeAllViews()
        home.branches.forEachIndexed { index, row ->
            if (index > 0) {
                layoutInflater.inflate(R.layout.view_pc_hairline, rows, true)
            }
            val item = ItemPcBranchRowBinding.inflate(layoutInflater, rows, true)
            val isSelected = row.branch.id == selectedBranch?.id
            item.vMark.setBackgroundResource(
                if (isSelected) R.drawable.bg_pc_circle_accent else R.drawable.bg_pc_circle_outline
            )
            item.tvName.text = row.branch.name
            item.tvCount.text = getString(
                R.string.pc_count_of_cap, ParkingFormat.pad2(row.occupied), row.capacity
            )
            item.pbBranch.progress =
                if (row.capacity > 0) row.occupied * 100 / row.capacity else 0
            item.root.setOnClickListener { selectBranch(row.branch) }
        }
        rows.visibility = if (home.branches.isEmpty()) View.GONE else View.VISIBLE

        // On site
        binding.btnSeeAll.text = resources.getQuantityString(R.plurals.pc_see_all, home.onSite.size, home.onSite.size)
        binding.btnSeeAll.visibility = if (home.onSite.isEmpty()) View.GONE else View.VISIBLE
        binding.tvOnSiteEmpty.visibility = if (home.onSite.isEmpty()) View.VISIBLE else View.GONE
        val list = binding.llOnSite
        list.removeAllViews()
        home.onSite.take(ON_SITE_PREVIEW).forEach { entry ->
            val item = ItemPcVehicleRowBinding.inflate(layoutInflater, list, true)
            bindVehicleRow(item, entry, home.memberNames)
            item.root.setOnClickListener { openExit(entry.id) }
        }
    }

    private fun bindVehicleRow(
        item: ItemPcVehicleRowBinding,
        entry: VehicleEntry,
        memberNames: Map<String, String>,
    ) {
        val member = entry.membershipId?.let { memberNames[it] }
        item.tvPlate.text = if (entry.plate == "NFC" && member != null) member else entry.plate
        val kind = if (entry.membershipId != null) {
            getString(R.string.pc_member)
        } else {
            ParkingFormat.vehicleType(this, entry.vehicleType)
        }
        item.tvMeta.text = getString(R.string.pc_meta, kind, ParkingFormat.duration(entry.createdAt))
        val fee = if (entry.membershipId != null) 0.0
        else entry.fee ?: FeeCalculator.exitFee(entry, selectedBranch)
        item.tvAmount.text = ParkingFormat.money(fee, selectedBranch?.currency)
        item.tvSince.text = getString(R.string.pc_since, ParkingFormat.hhmm(entry.createdAt))
    }

    private fun openExit(entryId: String?) {
        val intent = Intent(this, ExitActivity::class.java)
        if (entryId != null) intent.putExtra(ExitActivity.EXTRA_ENTRY_ID, entryId)
        TabNavigator.open(this, intent, R.id.nav_register, R.id.nav_dashboard)
    }

    private fun showError(message: String) {
        if (message.contains("Sesión expirada") || message.contains("sesión") ||
            message.contains("Session expired") || message.contains("session")) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, getString(R.string.dashboard_load_error, message), Toast.LENGTH_SHORT).show()
    }

    private fun hasBiometricHardware(): Boolean {
        return try {
            val mgr = BiometricManager.from(this)
            mgr.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
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
            try {
                biometricKeyManager.generateKeyPair()

                sessionManager.saveCredentials(email, password)
                sessionManager.setBiometricEnrolled(true)

                launch(Dispatchers.IO) {
                    try {
                        val publicKey = biometricKeyManager.getPublicKey() ?: return@launch
                        val token = sessionManager.getToken() ?: return@launch
                        val dataSource = LoginDataSource(sessionManager)
                        dataSource.biometricEnroll(token, userId, publicKey)
                    } catch (_: Exception) { }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardActivity, getString(R.string.biometric_enrolled_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                biometricKeyManager.deleteKey()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardActivity, e.localizedMessage ?: getString(R.string.biometric_enrolled_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val ON_SITE_PREVIEW = 5
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}

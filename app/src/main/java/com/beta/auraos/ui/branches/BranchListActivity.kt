package com.beta.auraos.ui.branches

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.beta.auraos.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.databinding.ActivityBranchListBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator

class BranchListActivity : BaseActivity() {

    private lateinit var binding: ActivityBranchListBinding
    private lateinit var viewModel: BranchViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        binding = ActivityBranchListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        viewModel = ViewModelProvider(this, BranchViewModelFactory(db))[BranchViewModel::class.java]

        setupViews()
        setupObservers()
        setupWindowInsets()

        viewModel.loadBranches()
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        // Creating, editing and deleting branches is an admin action on the server.
        binding.fabNewBranch.visibility = if (sessionManager.isAdmin()) View.VISIBLE else View.GONE
        binding.fabNewBranch.setOnClickListener {
            BranchFormActivity.open(this, branchId = null)
        }

        binding.etSearch.setOnClickListener {
            binding.etSearch.isFocusableInTouchMode = true
        }
    }

    private fun setupObservers() {
        viewModel.branches.observe(this) { branches ->
            renderBranchList(branches)
            updateKpis(branches)
            viewModel.loadOccupancyData(branches)
        }

        viewModel.branchOccupancy.observe(this) { _ ->
            val branches = viewModel.branches.value ?: return@observe
            renderBranchList(branches)
            updateKpis(branches)
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.tvError.visibility = View.GONE
            }
        }

        viewModel.operationResult.observe(this) { result ->
            if (result != null) {
                Toast.makeText(this, result.message, if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                viewModel.clearOperationResult()
            }
        }
    }

    private fun updateKpis(branches: List<Branch>) {
        if (branches.isEmpty()) return

        val totalCapacity = branches.sumOf {
            it.motorcycleCapacity + it.lightVehicleCapacity + it.heavyVehicleCapacity
        }
        binding.tvActiveFleet.text = formatNumber(totalCapacity)

        val occupancyMap = viewModel.branchOccupancy.value ?: emptyMap()
        val occupiedTotal = branches.sumOf { occupancyMap[it.id] ?: 0 }
        val globalCapPct = if (totalCapacity > 0) (occupiedTotal * 100 / totalCapacity) else 0
        val globalCapText = if (occupancyMap.isEmpty()) "--%" else "$globalCapPct%"
        binding.tvGlobalCap.text = globalCapText
    }

    private fun formatNumber(n: Int): String {
        return when {
            n >= 10000 -> "${n / 1000},${String.format("%03d", n % 1000)}"
            n >= 1000 -> "${n / 1000},${String.format("%03d", n % 1000)}"
            else -> n.toString()
        }
    }

    private fun renderBranchList(branches: List<Branch>) {
        binding.layoutBranchList.removeAllViews()
        val defaultId = sessionManager.getSelectedBranchId()

        if (branches.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        branches.forEach { branch ->
            val card = createBranchCard(branch, branch.id == defaultId)
            binding.layoutBranchList.addView(card)
        }
    }

    private fun createBranchCard(branch: Branch, isDefault: Boolean): View {
        val card = layoutInflater.inflate(R.layout.item_branch_card, binding.layoutBranchList, false) as MaterialCardView
        val tvName = card.findViewById<TextView>(R.id.tv_branch_name)
        val tvStatusBadge = card.findViewById<TextView>(R.id.tv_status_badge)
        val tvAddress = card.findViewById<TextView>(R.id.tv_branch_address)
        val tvOccupancy = card.findViewById<TextView>(R.id.tv_occupancy)
        val progressOccupancy = card.findViewById<LinearProgressIndicator>(R.id.progress_occupancy)
        val btnManage = card.findViewById<TextView>(R.id.btn_manage)

        tvName.text = branch.name

        val totalCapacity = branch.motorcycleCapacity + branch.lightVehicleCapacity + branch.heavyVehicleCapacity
        val isActive = isDefault

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            if (isActive) {
                setColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_green_badge_bg))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_green_neon))
                tvStatusBadge.text = getString(R.string.branch_status_active)
            } else {
                setColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_gray_badge_bg))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_gray_muted))
                tvStatusBadge.text = getString(R.string.branch_status_inactive)
            }
        }
        tvStatusBadge.background = badgeBg

        tvAddress.text = branch.address ?: getString(R.string.branch_no_address)

        val occupancyMap = viewModel.branchOccupancy.value ?: emptyMap()
        val occupied = (occupancyMap[branch.id] ?: 0).coerceAtMost(totalCapacity)
        val occupancyText = if (occupancyMap.isEmpty()) "-- / $totalCapacity"
            else getString(R.string.branch_occupancy_format, occupied, totalCapacity)
        tvOccupancy.text = occupancyText

        val occupancyPct = if (totalCapacity > 0 && occupancyMap.isNotEmpty()) (occupied * 100 / totalCapacity) else 0
        progressOccupancy.progress = occupancyPct

        if (occupancyPct > 85) {
            progressOccupancy.setIndicatorColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, Color.RED))
        } else {
            progressOccupancy.setIndicatorColor(ContextCompat.getColor(this, R.color.branch_green_neon))
        }

        val manageBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            if (isActive) {
                setColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_blue_manage))
                btnManage.setTextColor(ContextCompat.getColor(this@BranchListActivity, R.color.branch_dark_card))
                btnManage.text = getString(R.string.branch_action_manage)
            } else {
                setColor(MaterialColors.getColor(this@BranchListActivity, R.attr.dividerColorCustom, Color.GRAY))
                btnManage.setTextColor(MaterialColors.getColor(this@BranchListActivity, R.attr.textSecondaryCustom, Color.GRAY))
                btnManage.text = getString(R.string.branch_action_activate)
            }
        }
        btnManage.background = manageBg

        btnManage.setOnClickListener {
            if (isActive) {
                BranchFormActivity.open(this, branchId = branch.id)
            } else {
                sessionManager.saveSelectedBranchId(branch.id)
                renderBranchList(viewModel.branches.value ?: emptyList())
                Toast.makeText(this, getString(R.string.branch_default_toast, branch.name), Toast.LENGTH_SHORT).show()
            }
        }

        if (sessionManager.isAdmin()) {
            card.setOnLongClickListener {
                confirmDeleteBranch(branch)
                true
            }
        }

        card.setOnClickListener {
            BranchFormActivity.open(this, branchId = branch.id)
        }

        return card
    }

    private fun confirmDeleteBranch(branch: Branch) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.branch_delete_title))
            .setMessage(getString(R.string.branch_delete_confirm, branch.name))
            .setPositiveButton(getString(R.string.dialog_delete)) { _, _ ->
                viewModel.deleteBranch(branch.id)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadBranches()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
    }
}

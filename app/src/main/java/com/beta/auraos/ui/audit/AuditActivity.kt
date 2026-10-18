package com.beta.auraos.ui.audit

import android.content.Intent
import android.os.Bundle
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
import com.beta.auraos.R
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.audit.AuditPeriodSummary
import com.beta.auraos.data.audit.AuditRecord
import com.beta.auraos.data.audit.ClosureBatch
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityAuditBinding
import com.beta.auraos.databinding.ItemPcAmountRowBinding
import com.beta.auraos.databinding.ItemPcMovementBinding
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PcUi
import com.beta.auraos.util.TabNavigator
import com.google.android.material.snackbar.Snackbar
import java.util.Date
import java.util.Locale

/** Caja y auditoría: period revenue, closure and history for the selected branch. */
class AuditActivity : BaseActivity() {

    private lateinit var binding: ActivityAuditBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: AuditViewModel

    private var branches: List<Branch> = emptyList()
    private var pendingCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)

        val db = AppDatabase.getInstance(this)
        val factory = AuditViewModelFactory(
            AuditEngine(applicationContext),
            MembershipRepository(db),
            BranchRepository(ApiClient.createApi<BranchApi>(), db),
            sessionManager.getUserId() ?: "",
        )
        viewModel = ViewModelProvider(this, factory)[AuditViewModel::class.java]

        binding = ActivityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_cash)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        binding.btnExecuteClosure.setOnClickListener {
            if (pendingCount == 0) {
                Snackbar.make(binding.root, R.string.audit_no_pending_message, Snackbar.LENGTH_SHORT).show()
            } else {
                showClosureConfirmDialog()
            }
        }

        observeViewModel()
        viewModel.loadBranches()
        loadSummary()
    }

    private fun loadSummary() {
        val branchId = sessionManager.getSelectedBranchId()
        if (branchId != null) {
            viewModel.loadSummary(branchId)
        } else {
            binding.tvPeriodRange.text = getString(R.string.audit_error_no_branch)
            setClosureEnabled(false)
        }
    }

    private fun observeViewModel() {
        viewModel.branches.observe(this) { list ->
            branches = list
            renderBranchChips()
        }
        viewModel.uiState.observe(this) { state ->
            binding.loadingOverlay.visibility = if (state is AuditUiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is AuditUiState.Summary ->
                    bindSummary(state.periodSummary, state.closureHistory, state.pendingRecords)
                is AuditUiState.ClosureResult -> showClosureResultDialog(state.report)
                is AuditUiState.Error -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                is AuditUiState.Loading -> Unit
            }
        }
    }

    private fun currentBranch(): Branch? =
        branches.find { it.id == sessionManager.getSelectedBranchId() }

    private fun renderBranchChips() {
        val row = binding.llBranchChips
        row.removeAllViews()
        val selectedId = sessionManager.getSelectedBranchId()
        branches.forEach { branch ->
            PcUi.addChip(row, branch.name, checked = branch.id == selectedId) {
                if (branch.id != sessionManager.getSelectedBranchId()) {
                    sessionManager.saveSelectedBranchId(branch.id)
                    renderBranchChips()
                    loadSummary()
                }
            }
        }
    }

    private fun setClosureEnabled(enabled: Boolean) {
        binding.btnExecuteClosure.alpha = if (enabled) 1f else 0.4f
    }

    private fun bindSummary(
        summary: AuditPeriodSummary,
        history: List<ClosureBatch>,
        pending: List<AuditRecord>,
    ) {
        val branchName = currentBranch()?.name ?: getString(R.string.audit_branch_default)
        val currency = currentBranch()?.currency
        val operator = sessionManager.getLastDisplayName().orEmpty()
        binding.tvPeriodRange.text = if (summary.lastClosureDate != null) {
            getString(R.string.pc_period_since, ParkingFormat.dateTime(Date(summary.lastClosureDate)), operator)
        } else {
            getString(R.string.pc_period_first, operator)
        }.trimEnd(' ', '·')

        pendingCount = summary.pendingClosureRecords
        binding.tvPeriodRevenue.text = ParkingFormat.money(summary.totalRevenue, currency)
        binding.tvPeriodMeta.text =
            resources.getQuantityString(R.plurals.pc_charges, summary.exitCount, ParkingFormat.pad2(summary.exitCount), branchName)

        // Breakdown: vehicle revenue by type, memberships kept apart.
        val breakdown = binding.llBreakdown
        breakdown.removeAllViews()
        pending.groupBy { it.vehicleType }.toSortedMap().forEach { (type, records) ->
            val row = ItemPcAmountRowBinding.inflate(layoutInflater, breakdown, true)
            row.tvLabel.text = ParkingFormat.vehicleType(this, type)
            row.tvSub.text = resources.getQuantityString(R.plurals.pc_charges_short, records.size, ParkingFormat.pad2(records.size))
            row.tvAmount.text = ParkingFormat.money(records.sumOf { it.totalFee }, currency)
        }
        if (summary.membershipRevenue > 0) {
            val row = ItemPcAmountRowBinding.inflate(layoutInflater, breakdown, true)
            row.tvLabel.setText(R.string.pc_memberships)
            row.tvSub.text = ""
            row.tvAmount.text = ParkingFormat.money(summary.membershipRevenue, currency)
        }
        breakdown.visibility = if (breakdown.childCount == 0) View.GONE else View.VISIBLE

        binding.tvPendingCount.text = ParkingFormat.pad2(summary.pendingClosureRecords)
        binding.tvVehicleRevenue.text = ParkingFormat.money(summary.vehicleRevenue, currency)
        binding.tvToClose.text = ParkingFormat.money(summary.totalRevenue, currency)
        setClosureEnabled(summary.pendingClosureRecords > 0)

        // Movements
        val movements = binding.llMovements
        movements.removeAllViews()
        pending.take(MOVEMENTS_PREVIEW).forEach { record ->
            val row = ItemPcMovementBinding.inflate(layoutInflater, movements, true)
            row.tvTime.text = ParkingFormat.hhmm(record.exitTime)
            row.tvPlate.text = record.plate
            val kind = ParkingFormat.vehicleType(this, record.vehicleType)
            row.tvKind.text = record.durationMinutes?.let {
                getString(R.string.pc_meta, kind, ParkingFormat.durationMinutes(it))
            } ?: kind
            row.tvAmount.text = ParkingFormat.money(record.totalFee, currency)
        }
        binding.tvNoMovements.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE

        // History
        binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.closureHistoryContainer.removeAllViews()
        for (batch in history.take(10)) {
            val card = layoutInflater.inflate(R.layout.item_audit_closure, binding.closureHistoryContainer, false)
            card.findViewById<TextView>(R.id.tv_closure_date).text =
                getString(R.string.pc_meta, ParkingFormat.dateLabel(Date(batch.closedAt), ParkingFormat.dayMonthPattern()), branchName)
            card.findViewById<TextView>(R.id.tv_closure_revenue).text =
                ParkingFormat.money(batch.totalRevenue, currency)
            card.findViewById<TextView>(R.id.tv_closure_count).text =
                resources.getQuantityString(R.plurals.pc_closure_meta, batch.exitCount, ParkingFormat.hhmm(Date(batch.closedAt)), batch.exitCount)
            card.findViewById<TextView>(R.id.tv_closure_verified).setText(
                if (batch.isVerified) R.string.audit_verified else R.string.audit_pending_verify
            )
            card.setOnClickListener { showClosureDetailDialog(batch) }
            binding.closureHistoryContainer.addView(card)
        }
    }

    private fun showClosureConfirmDialog() {
        val branchId = sessionManager.getSelectedBranchId() ?: return
        val pending = pendingCount.toString()

        AlertDialog.Builder(this)
            .setTitle(R.string.audit_closure_confirm_title)
            .setMessage(getString(R.string.audit_closure_confirm_message, pending))
            .setPositiveButton(R.string.audit_closure_confirm_action) { _, _ ->
                viewModel.executeClosure(branchId)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showClosureResultDialog(report: com.beta.auraos.data.audit.ClosureReport) {
        val message = buildClosureResultSummary(report)

        AlertDialog.Builder(this)
            .setTitle(R.string.audit_result_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNeutralButton(R.string.audit_share_action) { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.audit_share_subject))
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.audit_share_action)))
            }
            .show()
    }

    private fun buildClosureResultSummary(report: com.beta.auraos.data.audit.ClosureReport): String {
        return buildString {
            append(getString(R.string.audit_result_date, ParkingFormat.dateTime(Date(report.closure.closedAt))))
            append("\n\n")
            append(getString(R.string.audit_result_entries, report.totalEntries))
            append("\n")
            append(getString(R.string.audit_result_exits, report.totalExits))
            append("\n")
            append(getString(R.string.audit_result_vehicle_revenue, report.closure.totalVehicleRevenue))
            if (report.membershipRevenueTotal > 0) {
                append("\n")
                append(getString(R.string.audit_result_membership_revenue, report.membershipRevenueTotal))
            }
            append("\n\n")
            append(getString(R.string.audit_result_total, report.closure.totalRevenue))
            if (report.discrepancies.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.audit_result_discrepancies, report.discrepancies.size))
            }
            if (report.expiringMembershipsCount > 0) {
                append("\n\n")
                append(getString(R.string.audit_result_expiring, report.expiringMembershipsCount))
            }
        }
    }

    private fun showClosureDetailDialog(batch: ClosureBatch) {
        val message = buildClosureSummary(batch)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.audit_closure_detail))
            .setMessage(message)
            .setPositiveButton(R.string.dialog_close, null)
            .setNeutralButton(R.string.audit_share_action) { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.audit_share_subject))
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.audit_share_action)))
            }
            .show()
    }

    private fun buildClosureSummary(batch: ClosureBatch): String {
        return buildString {
            append(getString(R.string.audit_result_date, ParkingFormat.dateTime(Date(batch.closedAt))))
            append("\n\n")
            append(getString(R.string.audit_result_entries, batch.entryCount))
            append("\n")
            append(getString(R.string.audit_result_exits, batch.exitCount))
            append("\n")
            append(getString(R.string.audit_result_vehicle_revenue, batch.totalVehicleRevenue))
            if (batch.totalMembershipRevenue > 0) {
                append("\n")
                append(getString(R.string.audit_result_membership_revenue, batch.totalMembershipRevenue))
            }
            append("\n\n")
            append(getString(R.string.audit_result_total, batch.totalRevenue))
            append("\n")
            append(if (batch.isVerified) getString(R.string.audit_verified) else getString(R.string.audit_pending_verify))
        }
    }

    companion object {
        private const val MOVEMENTS_PREVIEW = 8
    }
}

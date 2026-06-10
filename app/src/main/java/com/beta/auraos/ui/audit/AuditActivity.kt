package com.beta.auraos.ui.audit

import android.content.Intent

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.beta.auraos.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.audit.ClosureBatch
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityAuditBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditActivity : BaseActivity() {

    private lateinit var binding: ActivityAuditBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: AuditViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)

        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        val auditEngine = AuditEngine(applicationContext)
        val membershipRepo = MembershipRepository(db)
        val userId = sessionManager.getUserId() ?: ""
        val factory = AuditViewModelFactory(auditEngine, membershipRepo, userId)
        viewModel = ViewModelProvider(this, factory)[AuditViewModel::class.java]

        binding = ActivityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickHandlers()
        observeViewModel()

        val branchId = sessionManager.getSelectedBranchId()
        if (branchId != null) {
            binding.tvBranchName.text = sessionManager.getLastDisplayName() ?: getString(R.string.audit_branch_default)
            viewModel.loadSummary(branchId)
        } else {
            binding.tvPeriodRange.text = getString(R.string.audit_error_no_branch)
            binding.btnExecuteClosure.alpha = 0.4f
        }
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

    private fun setupClickHandlers() {
        binding.btnExecuteClosure.setOnClickListener {
            val pending = binding.tvPendingCount.text.toString().toIntOrNull() ?: 0
            if (pending == 0) {
                Snackbar.make(binding.root, R.string.audit_no_pending_message, Snackbar.LENGTH_SHORT).show()
            } else {
                showClosureConfirmDialog()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is AuditUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is AuditUiState.Summary -> {
                    binding.loadingOverlay.visibility = View.GONE
                    bindSummary(state.periodSummary, state.closureHistory)
                }
                is AuditUiState.ClosureResult -> {
                    binding.loadingOverlay.visibility = View.GONE
                    showClosureResultDialog(state.report)
                }
                is AuditUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bindSummary(
        summary: com.beta.auraos.data.audit.AuditPeriodSummary,
        history: List<ClosureBatch>
    ) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        val periodText = if (summary.lastClosureDate != null) {
            getString(R.string.audit_period_since, dateFormat.format(Date(summary.lastClosureDate)))
        } else {
            getString(R.string.audit_period_first)
        }
        binding.tvPeriodRange.text = periodText
        binding.tvPendingCount.text = summary.pendingClosureRecords.toString()
        binding.tvPeriodRevenue.text = getString(R.string.audit_revenue_format, summary.vehicleRevenue)

        binding.tvEmptyHistory.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
        binding.closureHistoryContainer.removeAllViews()

        for (batch in history.take(10)) {
            val card = layoutInflater.inflate(R.layout.item_audit_closure, binding.closureHistoryContainer, false)
            val tvDate = card.findViewById<TextView>(R.id.tv_closure_date)
            val tvRevenue = card.findViewById<TextView>(R.id.tv_closure_revenue)
            val tvCount = card.findViewById<TextView>(R.id.tv_closure_count)
            val tvVerified = card.findViewById<TextView>(R.id.tv_closure_verified)

            tvDate.text = dateFormat.format(Date(batch.closedAt))
            tvRevenue.text = getString(R.string.audit_revenue_format, batch.totalRevenue)
            tvCount.text = getString(R.string.audit_exit_count, batch.exitCount)
            tvVerified.text = if (batch.isVerified) {
                getString(R.string.audit_verified)
            } else {
                getString(R.string.audit_pending_verify)
            }
            tvVerified.setTextColor(
                if (batch.isVerified) {
                    MaterialColors.getColor(binding.root, R.attr.semanticSuccessCustom)
                } else {
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError)
                }
            )

            card.setOnClickListener { showClosureDetailDialog(batch) }
            binding.closureHistoryContainer.addView(card)
        }

        binding.btnExecuteClosure.alpha = if (summary.pendingClosureRecords > 0) 1f else 0.4f
    }

    private fun showClosureConfirmDialog() {
        val branchId = sessionManager.getSelectedBranchId() ?: return
        val pending = binding.tvPendingCount.text.toString()

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
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return buildString {
            append(getString(R.string.audit_result_date, dateFormat.format(Date(report.closure.closedAt))))
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
                append("\n\n⚠ ")
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
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return buildString {
            append(getString(R.string.audit_result_date, dateFormat.format(Date(batch.closedAt))))
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
}

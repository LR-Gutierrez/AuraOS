package com.beta.auraos.ui.branches

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import android.content.res.ColorStateList
import com.beta.auraos.R
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.databinding.ActivityBranchListBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class BranchListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBranchListBinding
    private lateinit var viewModel: BranchViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        binding = ActivityBranchListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, BranchViewModelFactory())[BranchViewModel::class.java]

        setupViews()
        setupObservers()
        setupWindowInsets()

        viewModel.loadBranches()
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnNewBranch.setOnClickListener {
            BranchFormActivity.open(this, branchId = null)
        }
    }

    private fun setupObservers() {
        viewModel.branches.observe(this) { branches ->
            renderBranchList(branches)
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
        val tvAddress = card.findViewById<TextView>(R.id.tv_branch_address)
        val tvCapacity = card.findViewById<TextView>(R.id.tv_branch_capacity)
        val tvRates = card.findViewById<TextView>(R.id.tv_branch_rates)
        val btnDefault = card.findViewById<android.widget.ImageButton>(R.id.btn_default_branch)
        val btnEdit = card.findViewById<View>(R.id.btn_edit_branch)
        val btnDelete = card.findViewById<View>(R.id.btn_delete_branch)

        tvName.text = branch.name
        tvAddress.text = branch.address ?: "Sin dirección"
        tvAddress.visibility = if (branch.address != null) View.VISIBLE else View.GONE

        tvCapacity.text = "M:${branch.motorcycleCapacity}  L:${branch.lightVehicleCapacity}  P:${branch.heavyVehicleCapacity}"
        tvRates.text = "${branch.currency} ${branch.motorcycleRate} / ${branch.lightVehicleRate} / ${branch.heavyVehicleRate}"

        if (isDefault) {
            val gold = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
            btnDefault.imageTintList = ColorStateList.valueOf(gold)
        } else {
            val gray = MaterialColors.getColor(card, R.attr.textSecondaryCustom)
            btnDefault.imageTintList = ColorStateList.valueOf(gray)
        }

        btnDefault.setOnClickListener {
            sessionManager.saveSelectedBranchId(branch.id)
            renderBranchList(viewModel.branches.value ?: emptyList())
            Toast.makeText(this, "Sucursal predeterminada: ${branch.name}", Toast.LENGTH_SHORT).show()
        }

        btnEdit.setOnClickListener {
            BranchFormActivity.open(this, branchId = branch.id)
        }

        btnDelete.setOnClickListener {
            confirmDeleteBranch(branch)
        }

        card.setOnClickListener {
            BranchFormActivity.open(this, branchId = branch.id)
        }

        return card
    }

    private fun confirmDeleteBranch(branch: Branch) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar sucursal")
            .setMessage("¿Estás seguro de eliminar \"${branch.name}\"? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteBranch(branch.id)
            }
            .setNegativeButton("Cancelar", null)
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

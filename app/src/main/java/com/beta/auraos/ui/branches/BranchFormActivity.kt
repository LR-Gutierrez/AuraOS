package com.beta.auraos.ui.branches

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import com.beta.auraos.databinding.ActivityBranchFormBinding

class BranchFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBranchFormBinding
    private lateinit var viewModel: BranchViewModel

    private var branchId: String? = null
    private var isEditMode = false

    companion object {
        private const val EXTRA_BRANCH_ID = "extra_branch_id"

        fun open(context: Context, branchId: String?) {
            val intent = Intent(context, BranchFormActivity::class.java)
            if (branchId != null) {
                intent.putExtra(EXTRA_BRANCH_ID, branchId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityBranchFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        branchId = intent.getStringExtra(EXTRA_BRANCH_ID)
        isEditMode = branchId != null

        viewModel = ViewModelProvider(this, BranchViewModelFactory())[BranchViewModel::class.java]

        setupViews()
        setupObservers()
        setupWindowInsets()

        if (isEditMode) {
            binding.tvAppTitle.text = getString(R.string.branch_edit_title)
            binding.btnSave.text = getString(R.string.branch_update_action)
            viewModel.loadBranch(branchId!!)
        }
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        val currencies = arrayOf("USD", "MXN", "GTQ")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCurrency.adapter = adapter

        binding.btnSave.setOnClickListener { validateAndSave() }
    }

    private fun setupObservers() {
        viewModel.selectedBranch.observe(this) { branch ->
            if (branch != null && isEditMode) {
                populateForm(branch)
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.operationResult.observe(this) { result ->
            if (result != null) {
                Toast.makeText(this, result.message, if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                viewModel.clearOperationResult()
                if (result.success) {
                    finish()
                }
            }
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun populateForm(branch: com.beta.auraos.data.model.Branch) {
        binding.etName.setText(branch.name)
        if (branch.address != null) binding.etAddress.setText(branch.address)
        binding.etMotoCapacity.setText(branch.motorcycleCapacity.toString())
        binding.etLightCapacity.setText(branch.lightVehicleCapacity.toString())
        binding.etHeavyCapacity.setText(branch.heavyVehicleCapacity.toString())
        binding.etMotoRate.setText(branch.motorcycleRate.toString())
        binding.etLightRate.setText(branch.lightVehicleRate.toString())
        binding.etHeavyRate.setText(branch.heavyVehicleRate.toString())

        binding.etMotoOvernight.setText(branch.motorcycleOvernightRate.toString())
        binding.etLightOvernight.setText(branch.lightVehicleOvernightRate.toString())
        binding.etHeavyOvernight.setText(branch.heavyVehicleOvernightRate.toString())

        binding.etOpenWeekday.setText(branch.openTimeWeekday)
        binding.etCloseWeekday.setText(branch.closeTimeWeekday)
        binding.etOpenWeekend.setText(branch.openTimeWeekend)
        binding.etCloseWeekend.setText(branch.closeTimeWeekend)

        val currencies = arrayOf("USD", "MXN", "GTQ")
        val currencyIndex = currencies.indexOf(branch.currency)
        if (currencyIndex >= 0) {
            binding.spinnerCurrency.setSelection(currencyIndex)
        }
    }

    private fun validateAndSave() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = getString(R.string.field_required_plain)
            binding.etName.requestFocus()
            return
        }

        val address = binding.etAddress.text.toString().trim().ifEmpty { null }

        val motoCapacity = binding.etMotoCapacity.text.toString().toIntOrNull() ?: 0
        val lightCapacity = binding.etLightCapacity.text.toString().toIntOrNull() ?: 0
        val heavyCapacity = binding.etHeavyCapacity.text.toString().toIntOrNull() ?: 0

        val motoRate = binding.etMotoRate.text.toString().toDoubleOrNull() ?: 0.0
        val lightRate = binding.etLightRate.text.toString().toDoubleOrNull() ?: 0.0
        val heavyRate = binding.etHeavyRate.text.toString().toDoubleOrNull() ?: 0.0

        val motoOvernight = binding.etMotoOvernight.text.toString().toDoubleOrNull() ?: 0.0
        val lightOvernight = binding.etLightOvernight.text.toString().toDoubleOrNull() ?: 0.0
        val heavyOvernight = binding.etHeavyOvernight.text.toString().toDoubleOrNull() ?: 0.0

        val openWeekday = binding.etOpenWeekday.text.toString().trim().ifEmpty { "07:00" }
        val closeWeekday = binding.etCloseWeekday.text.toString().trim().ifEmpty { "23:00" }
        val openWeekend = binding.etOpenWeekend.text.toString().trim().ifEmpty { "09:00" }
        val closeWeekend = binding.etCloseWeekend.text.toString().trim().ifEmpty { "22:00" }

        val currency = binding.spinnerCurrency.selectedItem.toString()

        if (isEditMode) {
            val input = UpdateBranchInput(
                name = name,
                address = address,
                motorcycleCapacity = motoCapacity,
                lightVehicleCapacity = lightCapacity,
                heavyVehicleCapacity = heavyCapacity,
                motorcycleRate = motoRate,
                lightVehicleRate = lightRate,
                heavyVehicleRate = heavyRate,
                motorcycleOvernightRate = motoOvernight,
                lightVehicleOvernightRate = lightOvernight,
                heavyVehicleOvernightRate = heavyOvernight,
                openTimeWeekday = openWeekday,
                closeTimeWeekday = closeWeekday,
                openTimeWeekend = openWeekend,
                closeTimeWeekend = closeWeekend,
                currency = currency
            )
            viewModel.updateBranch(branchId!!, input)
        } else {
            val input = CreateBranchInput(
                name = name,
                address = address,
                motorcycleCapacity = motoCapacity,
                lightVehicleCapacity = lightCapacity,
                heavyVehicleCapacity = heavyCapacity,
                motorcycleRate = motoRate,
                lightVehicleRate = lightRate,
                heavyVehicleRate = heavyRate,
                motorcycleOvernightRate = motoOvernight,
                lightVehicleOvernightRate = lightOvernight,
                heavyVehicleOvernightRate = heavyOvernight,
                openTimeWeekday = openWeekday,
                closeTimeWeekday = closeWeekday,
                openTimeWeekend = openWeekend,
                closeTimeWeekend = closeWeekend,
                currency = currency
            )
            viewModel.createBranch(input)
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
}

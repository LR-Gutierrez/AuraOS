package com.beta.auraos.ui.branches

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import com.beta.auraos.AuraApplication
import com.beta.auraos.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import com.beta.auraos.databinding.ActivityBranchFormBinding
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class BranchFormActivity : BaseActivity() {

    private lateinit var binding: ActivityBranchFormBinding
    private lateinit var viewModel: BranchViewModel

    private var branchId: String? = null
    private var isEditMode = false

    companion object {
        private const val EXTRA_BRANCH_ID = "extra_branch_id"
        private const val DEFAULT_OPEN_WEEKDAY = "07:00"
        private const val DEFAULT_CLOSE_WEEKDAY = "23:00"
        private const val DEFAULT_OPEN_WEEKEND = "09:00"
        private const val DEFAULT_CLOSE_WEEKEND = "22:00"

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
        val canEdit = AuraApplication.instance.sessionManager.isAdmin()
        if (!canEdit && !isEditMode) {
            finish()
            return
        }

        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        viewModel = ViewModelProvider(this, BranchViewModelFactory(db))[BranchViewModel::class.java]

        setupViews()
        setupObservers()
        setupWindowInsets()

        if (isEditMode) {
            binding.tvAppTitle.text = getString(R.string.branch_edit_title)
            binding.btnSave.text = getString(R.string.branch_update_action)
            viewModel.loadBranch(branchId!!)
        }
        if (!canEdit) showReadOnly()
    }

    /** An operator can look up a branch's capacity, rates and hours but not change them. */
    private fun showReadOnly() {
        binding.tvAppTitle.text = getString(R.string.branch_view_title)
        binding.btnSave.visibility = View.GONE
        disableInputs(binding.scrollView)
    }

    private fun disableInputs(view: View) {
        if (view === binding.btnSave) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) disableInputs(view.getChildAt(i))
        } else {
            view.isEnabled = false
        }
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        val currencies = arrayOf("USD", "MXN", "GTQ")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCurrency.adapter = adapter

        setupCapacityStepper(
            decrementBtn = binding.btnDecrementMoto,
            incrementBtn = binding.btnIncrementMoto,
            valueView = binding.tvMotoCapacityValue
        )
        setupCapacityStepper(
            decrementBtn = binding.btnDecrementLight,
            incrementBtn = binding.btnIncrementLight,
            valueView = binding.tvLightCapacityValue
        )
        setupCapacityStepper(
            decrementBtn = binding.btnDecrementHeavy,
            incrementBtn = binding.btnIncrementHeavy,
            valueView = binding.tvHeavyCapacityValue
        )

        setupTimeBlock(binding.tvOpenWeekdayBlock, DEFAULT_OPEN_WEEKDAY)
        setupTimeBlock(binding.tvCloseWeekdayBlock, DEFAULT_CLOSE_WEEKDAY)
        setupTimeBlock(binding.tvOpenWeekendBlock, DEFAULT_OPEN_WEEKEND)
        setupTimeBlock(binding.tvCloseWeekendBlock, DEFAULT_CLOSE_WEEKEND)

        binding.switchApiSync.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(
                this,
                if (isChecked) "Real-time API sync enabled"
                else "Real-time API sync disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSave.setOnClickListener { validateAndSave() }
    }

    private fun setupCapacityStepper(
        decrementBtn: View,
        incrementBtn: View,
        valueView: TextView
    ) {
        decrementBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            if (current > 0) {
                valueView.setText((current - 1).toString())
            }
        }
        incrementBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            valueView.setText((current + 1).toString())
        }
    }

    private fun setupTimeBlock(block: TextView, defaultTime: String) {
        block.text = defaultTime
        block.setOnClickListener {
            val parts = block.text.toString().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(getString(R.string.branch_time_picker_title))
                .build()

            picker.addOnPositiveButtonClickListener {
                val formatted = String.format("%02d:%02d", picker.hour, picker.minute)
                block.text = formatted
            }

            picker.show(supportFragmentManager, "time_picker")
        }
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

    private fun populateForm(branch: Branch) {
        binding.etName.setText(branch.name)
        if (branch.address != null) binding.etAddress.setText(branch.address)

        binding.tvMotoCapacityValue.setText(branch.motorcycleCapacity.toString())
        binding.tvLightCapacityValue.setText(branch.lightVehicleCapacity.toString())
        binding.tvHeavyCapacityValue.setText(branch.heavyVehicleCapacity.toString())

        binding.etMotoRate.setText(branch.motorcycleRate.toString())
        binding.etLightRate.setText(branch.lightVehicleRate.toString())
        binding.etHeavyRate.setText(branch.heavyVehicleRate.toString())

        binding.etMotoOvernight.setText(branch.motorcycleOvernightRate.toString())
        binding.etLightOvernight.setText(branch.lightVehicleOvernightRate.toString())
        binding.etHeavyOvernight.setText(branch.heavyVehicleOvernightRate.toString())

        binding.tvOpenWeekdayBlock.text = branch.openTimeWeekday
        binding.tvCloseWeekdayBlock.text = branch.closeTimeWeekday
        binding.tvOpenWeekendBlock.text = branch.openTimeWeekend
        binding.tvCloseWeekendBlock.text = branch.closeTimeWeekend

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

        val motoCapacity = binding.tvMotoCapacityValue.text.toString().toIntOrNull() ?: 0
        val lightCapacity = binding.tvLightCapacityValue.text.toString().toIntOrNull() ?: 0
        val heavyCapacity = binding.tvHeavyCapacityValue.text.toString().toIntOrNull() ?: 0

        val motoRate = binding.etMotoRate.text.toString().toDoubleOrNull() ?: 0.0
        val lightRate = binding.etLightRate.text.toString().toDoubleOrNull() ?: 0.0
        val heavyRate = binding.etHeavyRate.text.toString().toDoubleOrNull() ?: 0.0

        val motoOvernight = binding.etMotoOvernight.text.toString().toDoubleOrNull() ?: 0.0
        val lightOvernight = binding.etLightOvernight.text.toString().toDoubleOrNull() ?: 0.0
        val heavyOvernight = binding.etHeavyOvernight.text.toString().toDoubleOrNull() ?: 0.0

        val openWeekday = binding.tvOpenWeekdayBlock.text.toString().trim().ifEmpty { DEFAULT_OPEN_WEEKDAY }
        val closeWeekday = binding.tvCloseWeekdayBlock.text.toString().trim().ifEmpty { DEFAULT_CLOSE_WEEKDAY }
        val openWeekend = binding.tvOpenWeekendBlock.text.toString().trim().ifEmpty { DEFAULT_OPEN_WEEKEND }
        val closeWeekend = binding.tvCloseWeekendBlock.text.toString().trim().ifEmpty { DEFAULT_CLOSE_WEEKEND }

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

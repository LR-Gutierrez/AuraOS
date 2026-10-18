package com.beta.auraos.ui.exit

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import coil.load
import coil.transform.RoundedCornersTransformation
import com.beta.auraos.BaseActivity
import com.beta.auraos.HomeActivity
import com.beta.auraos.R
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.audit.FeeCalculator
import com.beta.auraos.data.local.ExitSyncManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityExitBinding
import com.beta.auraos.databinding.ItemPcVehicleRowBinding
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PcUi
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.TabNavigator
import com.google.android.material.color.MaterialColors

/** Registro · Salida: pick a vehicle on site, review the charge and log the exit. */
class ExitActivity : BaseActivity() {

    private lateinit var binding: ActivityExitBinding
    private lateinit var viewModel: ExitViewModel
    private lateinit var sessionManager: SessionManager

    private var allEntries: List<VehicleEntry> = emptyList()
    private var currentBranch: Branch? = null
    private var membershipNameMap: Map<String, String> = emptyMap()
    private var selectedId: String? = null
    private var exitingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = AppDatabase.getInstance(this)
        val factory = ExitViewModelFactory(
            VehicleEntryRepository(db),
            BranchRepository(ApiClient.createApi<BranchApi>(), db),
            MembershipRepository(db),
            AuditEngine(applicationContext),
            ExitSyncManager(db),
        )
        viewModel = ViewModelProvider(this, factory)[ExitViewModel::class.java]

        binding = ActivityExitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedId = savedInstanceState?.getString(STATE_SELECTED) ?: intent.getStringExtra(EXTRA_ENTRY_ID)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_register)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        binding.header.segment.check(R.id.seg_exit)
        binding.header.segEntry.setOnClickListener {
            TabNavigator.open(this, Intent(this, HomeActivity::class.java), R.id.nav_register, R.id.nav_register)
        }

        PcUi.underline(binding.btnViewPhotos)
        PcUi.underline(binding.btnClearSelection)
        binding.btnClearSelection.setOnClickListener { select(null) }
        binding.btnCheckout.setOnClickListener {
            val id = selectedId ?: return@setOnClickListener
            exitingId = id
            render()
            viewModel.registerExit(id)
        }
        binding.done.btnDoneAgain.text = getString(R.string.pc_pick_another)
        binding.done.btnDoneAgain.setOnClickListener {
            binding.done.root.visibility = View.GONE
            render()
        }

        binding.etSearchPlate.filters = arrayOf(InputFilter.AllCaps())
        binding.etSearchPlate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = renderList()
        })

        viewModel.uiState.observe(this) { state ->
            binding.loadingOverlay.visibility = if (state is ExitUiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is ExitUiState.Success -> {
                    allEntries = state.entries
                    membershipNameMap = state.membershipNameMap
                    if (selectedId != null && allEntries.none { it.id == selectedId }) selectedId = null
                    render()
                }
                is ExitUiState.Error -> Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                is ExitUiState.Loading -> Unit
            }
        }

        viewModel.branch.observe(this) { branch ->
            currentBranch = branch
            render()
        }

        viewModel.lastExit.observe(this) { exited ->
            if (exited == null) return@observe
            viewModel.consumeExit()
            exitingId = null
            selectedId = null
            showDone(exited)
        }

        loadEntries()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selectedId)
    }

    private fun loadEntries() {
        val branchId = sessionManager.getSelectedBranchId()
        if (branchId == null) {
            Toast.makeText(this, R.string.exit_no_branch, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.loadEntries(branchId)
    }

    private fun select(id: String?) {
        selectedId = id
        binding.done.root.visibility = View.GONE
        render()
        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun render() {
        val capacity = currentBranch?.let {
            it.lightVehicleCapacity + it.heavyVehicleCapacity + it.motorcycleCapacity
        } ?: 0
        binding.header.tvBranchMeta.text = getString(
            R.string.pc_branch_meta, branchName(), ParkingFormat.pad2(allEntries.size), capacity
        )
        val selected = allEntries.find { it.id == selectedId }
        val showDone = binding.done.root.visibility == View.VISIBLE
        binding.listSection.visibility = if (selected == null && !showDone) View.VISIBLE else View.GONE
        binding.checkoutSection.visibility = if (selected != null && !showDone) View.VISIBLE else View.GONE
        if (selected != null) renderCheckout(selected) else renderList()
    }

    private fun renderList() {
        val query = binding.etSearchPlate.text?.toString()?.trim()?.uppercase().orEmpty()
        val results = if (query.isEmpty()) allEntries else allEntries.filter {
            it.plate.uppercase().contains(query) || resolvePlateDisplayName(it).uppercase().contains(query)
        }
        binding.tvResultLabel.text = if (query.isEmpty()) {
            resources.getQuantityString(R.plurals.pc_on_site_count, allEntries.size, ParkingFormat.pad2(allEntries.size), branchName())
        } else {
            resources.getQuantityString(R.plurals.pc_results, results.size, results.size, branchName())
        }
        binding.tvEmptyState.visibility =
            if (results.isEmpty() && viewModel.uiState.value is ExitUiState.Success) View.VISIBLE else View.GONE

        val list = binding.llResults
        list.removeAllViews()
        results.sortedByDescending { it.createdAt }.forEach { entry ->
            val item = ItemPcVehicleRowBinding.inflate(layoutInflater, list, true)
            item.tvPlate.text = resolvePlateDisplayName(entry)
            item.tvMeta.text = getString(R.string.pc_meta, kindLabel(entry), ParkingFormat.duration(entry.createdAt))
            item.tvAmount.text = ParkingFormat.money(feeFor(entry), currentBranch?.currency)
            item.tvSince.text = getString(R.string.pc_since, ParkingFormat.hhmm(entry.createdAt))
            item.root.setOnClickListener { select(entry.id) }
        }
    }

    private fun renderCheckout(entry: VehicleEntry) {
        binding.tvCoPlate.text = resolvePlateDisplayName(entry)
        binding.tvCoType.text = kindLabel(entry)
        binding.kvIn.tvKey.setText(R.string.pc_in)
        binding.kvIn.tvValue.text = ParkingFormat.hhmm(entry.createdAt)
        binding.kvDuration.tvKey.setText(R.string.pc_time)
        binding.kvDuration.tvValue.text = ParkingFormat.duration(entry.createdAt)
        binding.kvPhotos.tvKey.setText(R.string.pc_photos)
        binding.kvPhotos.tvValue.text = getString(R.string.pc_photos_of, photoCount(entry))
        binding.kvOut.tvKey.setText(R.string.pc_out)
        binding.kvOut.tvValue.text = ParkingFormat.hhmm()
        binding.tvCoAmount.text = ParkingFormat.money(feeFor(entry), currentBranch?.currency)

        val busy = exitingId == entry.id
        val isMember = entry.membershipId != null
        binding.btnCheckout.text = getString(
            when {
                busy -> R.string.exit_registering
                isMember -> R.string.pc_log_exit
                else -> R.string.pc_charge_and_exit
            }
        )
        binding.tvCoAmountNote.visibility = if (isMember) View.VISIBLE else View.GONE
        binding.btnCheckout.isEnabled = !busy
        binding.btnViewPhotos.visibility = if (photoCount(entry) > 0) View.VISIBLE else View.GONE
        binding.btnViewPhotos.setOnClickListener { showPhotosDialog(entry) }
    }

    private fun showDone(exited: VehicleEntry) {
        binding.done.tvDoneTitle.setText(R.string.pc_exit_done)
        binding.done.tvDonePlate.text = resolvePlateDisplayName(exited)
        val charge = if (exited.membershipId != null) {
            getString(R.string.pc_member_no_charge)
        } else {
            ParkingFormat.money(exited.fee, currentBranch?.currency)
        }
        binding.done.tvDoneMeta.text = getString(
            R.string.pc_exit_done_meta, charge, ParkingFormat.duration(exited.createdAt)
        )
        binding.done.root.visibility = View.VISIBLE
        binding.etSearchPlate.setText("")
        render()
        binding.scrollView.smoothScrollTo(0, 0)
    }

    private fun branchName(): String = currentBranch?.name ?: ""

    private fun kindLabel(entry: VehicleEntry): String =
        if (entry.membershipId != null) getString(R.string.pc_member)
        else ParkingFormat.vehicleType(this, entry.vehicleType)

    // Same rule ExitViewModel records on exit, so the screen never promises a different charge.
    private fun feeFor(entry: VehicleEntry): Double? =
        if (entry.membershipId != null) 0.0
        else entry.fee ?: FeeCalculator.exitFee(entry, currentBranch)

    private fun photoCount(entry: VehicleEntry): Int = listOf(
        entry.platePhotoUrl, entry.frontPhotoUrl, entry.rearPhotoUrl, entry.leftPhotoUrl, entry.rightPhotoUrl
    ).count { !it.isNullOrBlank() }

    private fun showPhotosDialog(entry: VehicleEntry) {
        val density = resources.displayMetrics.density
        val scroll = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), 0)
        }

        val secondaryText = MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)

        val photoFields = listOf(
            getString(R.string.pc_slot_plate) to entry.platePhotoUrl,
            getString(R.string.pc_slot_front) to entry.frontPhotoUrl,
            getString(R.string.pc_slot_rear) to entry.rearPhotoUrl,
            getString(R.string.pc_slot_left) to entry.leftPhotoUrl,
            getString(R.string.pc_slot_right) to entry.rightPhotoUrl
        )

        for ((label, url) in photoFields) {
            val fullUrl = PhotoUrlBuilder.build(url) ?: continue

            val labelText = TextView(this).apply {
                text = label
                setTextColor(secondaryText)
                textSize = 11f
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
            }
            container.addView(labelText)

            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (120 * density).toInt()
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(fullUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(8f * density))
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_gallery)
                }
            }
            container.addView(iv)
        }

        scroll.addView(container)

        AlertDialog.Builder(this)
            .setTitle(resolvePlateDisplayName(entry))
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun resolvePlateDisplayName(entry: VehicleEntry): String {
        if (entry.plate == "NFC" && entry.membershipId != null) {
            membershipNameMap[entry.membershipId]?.let { return it }
        }
        return entry.plate
    }

    companion object {
        /** Opens straight into the checkout panel for this entry. */
        const val EXTRA_ENTRY_ID = "extra_entry_id"
        private const val STATE_SELECTED = "state_selected"
    }
}

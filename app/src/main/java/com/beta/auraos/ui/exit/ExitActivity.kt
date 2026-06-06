package com.beta.auraos.ui.exit

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.beta.auraos.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import coil.load
import coil.transform.RoundedCornersTransformation
import com.beta.auraos.R
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.audit.FeeCalculator
import com.beta.auraos.data.local.ExitSyncManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityExitBinding
import com.beta.auraos.databinding.ItemExitVehicleBinding
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.formatDateTime
import com.google.android.material.color.MaterialColors

class ExitActivity : BaseActivity() {

    private lateinit var binding: ActivityExitBinding
    private lateinit var viewModel: ExitViewModel
    private lateinit var sessionManager: SessionManager

    private var allEntries: List<VehicleEntry> = emptyList()
    private var currentBranch: Branch? = null
    private var membershipNameMap: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        val vehicleRepo = VehicleEntryRepository(db)
        val branchApi = ApiClient.createApi<BranchApi>()
        val branchRepo = BranchRepository(branchApi, db)
        val membershipRepo = MembershipRepository(db)
        val auditEngine = AuditEngine(applicationContext)
        val exitSyncManager = ExitSyncManager(db)
        val factory = ExitViewModelFactory(vehicleRepo, branchRepo, membershipRepo, auditEngine, exitSyncManager)
        viewModel = ViewModelProvider(this, factory)[ExitViewModel::class.java]

        binding = ActivityExitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }

        binding.etSearchPlate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.lowercase() ?: ""
                val filtered = if (query.isEmpty()) {
                    allEntries
                } else {
                    allEntries.filter { entry ->
                        entry.plate.lowercase().contains(query) ||
                        resolvePlateDisplayName(entry).lowercase().contains(query)
                    }
                }
                renderEntries(filtered, null)
            }
        })

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is ExitUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is ExitUiState.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    allEntries = state.entries
                    membershipNameMap = state.membershipNameMap
                    renderEntries(state.entries, state.exitingId)
                }
                is ExitUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.branch.observe(this) { branch ->
            currentBranch = branch
            val entries = viewModel.uiState.value
            if (entries is ExitUiState.Success) {
                renderEntries(entries.entries, entries.exitingId)
            }
        }

        loadEntries()
    }

    private fun loadEntries() {
        val branchId = sessionManager.getSelectedBranchId()
        if (branchId == null) {
            Toast.makeText(this, R.string.exit_no_branch, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        viewModel.loadEntries(branchId)
    }

    private fun renderEntries(entries: List<VehicleEntry>, exitingId: String?) {
        binding.contentContainer.removeViews(3, binding.contentContainer.childCount - 3)

        if (entries.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        for (entry in entries) {
            val itemBinding = ItemExitVehicleBinding.inflate(layoutInflater, binding.contentContainer, false)
            val card = itemBinding.root

            val params = card.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
                card.layoutParams = params
            }

            itemBinding.tvPlate.text = resolvePlateDisplayName(entry)

            val typeLabel = entry.vehicleType.replaceFirstChar { it.uppercase() }
            val timeLabel = formatDateTime(entry.createdAt)
            val vipLabel = if (entry.isVip) " \u2605 VIP" else ""
            itemBinding.tvEntryInfo.text = "$typeLabel  \u2022  $timeLabel$vipLabel"

            val duration = entry.duration ?: FeeCalculator.computeDuration(entry.createdAt)
            itemBinding.tvDurationValue.text = duration

            if (entry.id == exitingId) {
                itemBinding.tvCurrentFeeValue.text = getString(R.string.exit_registering)
                itemBinding.btnProcessCheckout.visibility = View.GONE
            } else {
                val fee = entry.fee ?: FeeCalculator.computeFee(entry.createdAt, entry.vehicleType, currentBranch)
                itemBinding.tvCurrentFeeValue.text = if (fee != null) {
                    "$ ${String.format("%.2f", fee)}"
                } else {
                    "--"
                }
            }

            val photoUrl = PhotoUrlBuilder.build(entry.platePhotoUrl)
            if (photoUrl != null) {
                itemBinding.ivThumbnail.load(photoUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(8f * resources.displayMetrics.density))
                    placeholder(android.R.drawable.ic_menu_camera)
                    error(android.R.drawable.ic_menu_camera)
                }
            }

            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(ContextCompat.getColor(this@ExitActivity, R.color.branch_blue_manage))
            }.let { itemBinding.btnProcessCheckout.background = it }

            if (entry.id != exitingId) {
                itemBinding.btnProcessCheckout.setOnClickListener {
                    showExitPhotoDialog(entry)
                }
            }

            binding.contentContainer.addView(card)
        }
    }

    private fun showExitPhotoDialog(entry: VehicleEntry) {
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

        val primaryText = MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom)
        val secondaryText = MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)

        val msgText = TextView(this).apply {
            text = getString(R.string.exit_confirm_message, resolvePlateDisplayName(entry))
            setTextColor(primaryText)
            textSize = 14f
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(msgText)

        val photoFields = listOf(
            "Placa" to entry.platePhotoUrl,
            "Frontal" to entry.frontPhotoUrl,
            "Trasera" to entry.rearPhotoUrl,
            "Izquierda" to entry.leftPhotoUrl,
            "Derecha" to entry.rightPhotoUrl
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
            .setTitle(getString(R.string.exit_confirm_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.exit_confirm_action)) { _, _ ->
                viewModel.registerExit(entry.id)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun resolvePlateDisplayName(entry: VehicleEntry): String {
        if (entry.plate == "NFC" && entry.membershipId != null) {
            membershipNameMap[entry.membershipId]?.let { return it }
        }
        return entry.plate
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

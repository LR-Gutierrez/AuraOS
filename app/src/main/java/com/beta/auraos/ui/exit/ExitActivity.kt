package com.beta.auraos.ui.exit

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.util.formatDateTime
import com.beta.auraos.databinding.ActivityExitBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ExitActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExitBinding
    private lateinit var viewModel: ExitViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val repository = VehicleEntryRepository()
        val factory = ExitViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ExitViewModel::class.java]

        binding = ActivityExitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is ExitUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is ExitUiState.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    renderEntries(state.entries, state.exitingId)
                }
                is ExitUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
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
        binding.contentContainer.removeViews(1, binding.contentContainer.childCount - 1)

        if (entries.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        for (entry in entries) {
            val card = createEntryCard(entry, entry.id == exitingId)
            binding.contentContainer.addView(card)

            val layoutParams = card.layoutParams as LinearLayout.LayoutParams
            if (card !== binding.contentContainer.getChildAt(1)) {
                layoutParams.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }

            card.setOnClickListener {
                showExitConfirmDialog(entry)
            }
        }
    }

    private fun createEntryCard(entry: VehicleEntry, isExiting: Boolean): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(
                        this, R.attr.cardGlassFillColorCustom
                    )
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            setContentPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            foreground = {
                val tv = android.util.TypedValue()
                this@ExitActivity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true
                )
                ContextCompat.getDrawable(this@ExitActivity, tv.resourceId)
            }()
        }

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val plateText = TextView(this).apply {
            text = entry.plate
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textPrimaryCustom
                )
            )
            textSize = 18f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        container.addView(plateText)

        val typeLabel = entry.vehicleType.replaceFirstChar { it.uppercase() }
        val timeLabel = formatDateTime(entry.createdAt)
        val vipLabel = if (entry.isVip) " \u2605 VIP" else ""

        val infoText = TextView(this).apply {
            text = "$typeLabel  \u2022  $timeLabel$vipLabel"
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textSecondaryCustom
                )
            )
            textSize = 13f
        }
        container.addView(infoText)

        if (isExiting) {
            val loadingText = TextView(this).apply {
                text = getString(R.string.exit_registering)
                setTextColor(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                )
                textSize = 12f
            }
            container.addView(loadingText)
        }

        card.addView(container)
        return card
    }

    private fun showExitConfirmDialog(entry: VehicleEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_confirm_title))
            .setMessage(getString(R.string.exit_confirm_message, entry.plate))
            .setPositiveButton(getString(R.string.exit_confirm_action)) { _, _ ->
                viewModel.registerExit(entry.id)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
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

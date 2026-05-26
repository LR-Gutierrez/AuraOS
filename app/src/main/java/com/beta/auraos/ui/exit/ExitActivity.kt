package com.beta.auraos.ui.exit

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
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
import coil.load
import coil.transform.RoundedCornersTransformation
import com.beta.auraos.R
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.util.PhotoUrlBuilder
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
                showExitPhotoDialog(entry)
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

        val topRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val photoUrl = PhotoUrlBuilder.build(entry.platePhotoUrl)
        val thumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (56 * density).toInt(),
                (56 * density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (photoUrl != null) {
                load(photoUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(8f * density))
                    placeholder(android.R.drawable.ic_menu_camera)
                    error(android.R.drawable.ic_menu_camera)
                }
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
        topRow.addView(thumbnail)

        val infoCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
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
        infoCol.addView(plateText)

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
        infoCol.addView(infoText)

        if (isExiting) {
            val loadingText = TextView(this).apply {
                text = getString(R.string.exit_registering)
                setTextColor(
                    MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
                )
                textSize = 12f
            }
            infoCol.addView(loadingText)
        }

        topRow.addView(infoCol)
        container.addView(topRow)
        card.addView(container)
        return card
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
                setTextColor(
                    MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)
                )
                textSize = 11f
                setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
            }
            container.addView(labelText)

            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (180 * density).toInt()
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
            .setMessage(getString(R.string.exit_confirm_message, entry.plate))
            .setView(scroll)
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

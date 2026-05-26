package com.beta.auraos

import android.app.DatePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityAnalyticsBinding
import com.beta.auraos.ui.history.HistoryFilter
import com.beta.auraos.ui.history.HistoryUiState
import com.beta.auraos.ui.history.HistoryViewModel
import com.beta.auraos.ui.history.HistoryViewModelFactory
import com.beta.auraos.ui.history.SortField
import com.beta.auraos.ui.history.SortOrder
import com.beta.auraos.ui.history.HistoryEntry
import com.beta.auraos.util.BottomNavIndicator
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.TabNavigator
import com.beta.auraos.util.formatDateTime
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var viewModel: HistoryViewModel
    private lateinit var sessionManager: SessionManager

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val repository = VehicleEntryRepository()
        val factory = HistoryViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[HistoryViewModel::class.java]

        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBottomNav()
        setupFilterButtons()
        setupDateRangeButtons()
        setupSearch()
        setupSortChips()
        setupLoadMore()

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is HistoryUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is HistoryUiState.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    bindData(state)
                }
                is HistoryUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        loadHistory()
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_analytics

        BottomNavIndicator.attach(
            bottomNav = binding.bottomNavigation,
            indicator = binding.bottomNavIndicator,
            initialItemId = R.id.nav_analytics,
        )

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_analytics -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = R.id.nav_analytics,
                    )
                    true
                }
                else -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = item.itemId,
                    )
                    TabNavigator.navigate(this, item.itemId)
                    true
                }
            }
        }
    }

    private fun setupFilterButtons() {
        updateFilterSelection(HistoryFilter.TODAY)
        updateChipSelection()

        binding.btnFilterToday.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility = View.GONE
            viewModel.setFilter(HistoryFilter.TODAY, branchId)
            updateFilterSelection(HistoryFilter.TODAY)
            updateChipSelection()
        }

        binding.btnFilterAll.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility = View.GONE
            viewModel.setFilter(HistoryFilter.ALL, branchId)
            updateFilterSelection(HistoryFilter.ALL)
            updateChipSelection()
        }

        binding.btnFilterRange.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility =
                if (binding.layoutDateRange.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            viewModel.setFilter(HistoryFilter.RANGE, branchId)
            updateFilterSelection(HistoryFilter.RANGE)
            updateChipSelection()
        }
    }

    private fun setupDateRangeButtons() {
        binding.btnDateFrom.setOnClickListener {
            showDatePicker { year, month, day ->
                val cal = Calendar.getInstance().apply { set(year, month, day) }
                val from = dateFormat.format(cal.time)
                binding.btnDateFrom.text = from
                val to = binding.btnDateTo.text.toString()
                if (to != getString(R.string.history_filter_to) && to.isNotBlank()) {
                    val branchId = sessionManager.getSelectedBranchId() ?: return@showDatePicker
                    viewModel.setDateRange(from, to, branchId)
                }
            }
        }

        binding.btnDateTo.setOnClickListener {
            showDatePicker { year, month, day ->
                val cal = Calendar.getInstance().apply { set(year, month, day) }
                val to = dateFormat.format(cal.time)
                binding.btnDateTo.text = to
                val from = binding.btnDateFrom.text.toString()
                if (from != getString(R.string.history_filter_from) && from.isNotBlank()) {
                    val branchId = sessionManager.getSelectedBranchId() ?: return@showDatePicker
                    viewModel.setDateRange(from, to, branchId)
                }
            }
        }
    }

    private fun showDatePicker(onSelected: (year: Int, month: Int, day: Int) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth -> onSelected(year, month, dayOfMonth) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun setupSortChips() {
        updateChipSelection()

        binding.btnFilterRate.setOnClickListener {
            viewModel.toggleSort(SortField.FEE)
            updateChipSelection()
        }
        binding.btnFilterDuration.setOnClickListener {
            viewModel.toggleSort(SortField.DURATION)
            updateChipSelection()
        }
    }

    private fun updateChipSelection() {
        val state = viewModel.uiState.value
        if (state !is HistoryUiState.Success) return

        val order = state.sortOrder
        val field = state.sortField

        val rateSelected = field == SortField.FEE
        val durationSelected = field == SortField.DURATION

        binding.btnFilterRate.isSelected = rateSelected
        binding.btnFilterDuration.isSelected = durationSelected

        val arrow = if (order == SortOrder.DESC) " ↓" else " ↑"
        binding.btnFilterRate.text = getString(R.string.history_sort_fee) + (if (rateSelected) arrow else "")
        binding.btnFilterDuration.text = getString(R.string.history_sort_duration) + (if (durationSelected) arrow else "")
    }

    private fun setupLoadMore() {
        binding.btnLoadMore.setOnClickListener {
            viewModel.loadMore()
        }
    }

    private fun updateFilterSelection(selected: HistoryFilter) {
        binding.btnFilterToday.isSelected = selected == HistoryFilter.TODAY
        binding.btnFilterAll.isSelected = selected == HistoryFilter.ALL
        binding.btnFilterRange.isSelected = selected == HistoryFilter.RANGE
    }

    private fun loadHistory() {
        val branchId = sessionManager.getSelectedBranchId()
        if (branchId == null) {
            Toast.makeText(this, R.string.exit_no_branch, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        viewModel.loadHistory(branchId)
    }

    private fun bindData(state: HistoryUiState.Success) {
        binding.tvTodayCount.text = state.summary.count.toString()
        binding.tvTodayRevenue.text = getString(R.string.history_revenue_amount, state.summary.revenue)
        binding.tvAvgDuration.text = state.summary.avgDurationLabel

        binding.tvTrendText.text = getString(R.string.history_metric_trend, 0)
        binding.tvPeakText.text = getString(R.string.history_metric_peak, 0.0)

        // Se elimina o comenta la línea de tvTargetText porque la meta es estática en el XML actual
        // binding.tvTargetText.text = getString(R.string.history_metric_target, "4h")

        when (state.filter) {
            HistoryFilter.TODAY -> {
                binding.tvFilterLabel.visibility = View.GONE
            }
            HistoryFilter.ALL -> {
                binding.tvFilterLabel.visibility = View.GONE
            }
            HistoryFilter.RANGE -> {
                binding.tvFilterLabel.visibility = View.VISIBLE
                val from = state.filterFrom ?: "..."
                val to = state.filterTo ?: "..."
                binding.tvFilterLabel.text = "$from → $to"
            }
        }

        // Mantener todas las vistas estáticas del XML (0-9) e insertar cards dinámicamente
        val headerIndex = 7
        val staticChildCount = 10
        val extraViewCount = binding.contentContainer.childCount - staticChildCount
        if (extraViewCount > 0) {
            binding.contentContainer.removeViews(staticChildCount, extraViewCount)
        }

        if (state.totalFiltered > HistoryViewModel.PAGE_SIZE) {
            binding.tvShowingCount.visibility = View.VISIBLE
            binding.tvShowingCount.text = getString(R.string.history_showing, state.shownCount, state.totalFiltered)
        } else {
            binding.tvShowingCount.visibility = View.GONE
        }

        binding.btnLoadMore.visibility = if (state.hasMore) View.VISIBLE else View.GONE

        binding.tvEmptyState.visibility = if (state.entries.isEmpty()) View.VISIBLE else View.GONE
        if (state.entries.isEmpty()) return

        var insertIdx = 7
        for (historyEntry in state.entries) {
            val card = createHistoryCard(historyEntry)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            card.layoutParams = lp
            binding.contentContainer.addView(card, insertIdx++)
        }
    }

    private fun createHistoryCard(entry: HistoryEntry): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val e = entry.entry

        val photoFields = listOfNotNull(
            "Placa" to e.platePhotoUrl,
            "Frontal" to e.frontPhotoUrl,
            "Trasera" to e.rearPhotoUrl,
            "Izquierda" to e.leftPhotoUrl,
            "Derecha" to e.rightPhotoUrl
        ).mapNotNull { (label, url) ->
            val fullUrl = PhotoUrlBuilder.build(url)
            if (fullUrl != null) label to fullUrl else null
        }

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(this, R.attr.cardGlassFillColorCustom)
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            val strokeColorInt = MaterialColors.getColor(this, R.attr.dividerColorCustom)
            strokeColor = strokeColorInt
            strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thin)
            setContentPadding(padding, padding, padding, padding)
            isClickable = photoFields.isNotEmpty()
            isFocusable = photoFields.isNotEmpty()
            if (photoFields.isNotEmpty()) {
                foreground = {
                    val tv = android.util.TypedValue()
                    this@AnalyticsActivity.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground, tv, true
                    )
                    ContextCompat.getDrawable(this@AnalyticsActivity, tv.resourceId)
                }()
                setOnClickListener { showPhotoDialog(entry.entry.plate, photoFields) }
            }
        }

        val cardContent = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        // Top row: thumbnail + info
        val topRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Photo thumbnail
        val thumbUrl = PhotoUrlBuilder.build(e.platePhotoUrl)
        val thumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (48 * density).toInt(),
                (48 * density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (thumbUrl != null) {
                load(thumbUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(6f * density))
                    placeholder(android.R.drawable.ic_menu_camera)
                    error(android.R.drawable.ic_menu_camera)
                }
            } else {
                setImageResource(android.R.drawable.ic_menu_camera)
            }
        }
        topRow.addView(thumbnail)

        // Info column
        val infoCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), 0, 0, 0)
        }

        val vehicleTypeStr = e.vehicleType.replaceFirstChar { it.uppercase() }
        val vipStr = if (e.isVip) " \u2605 VIP" else ""

        val plateText = TextView(this).apply {
            text = "${e.plate}  $vipStr"
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 16f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        infoCol.addView(plateText)

        val typeText = TextView(this).apply {
            text = vehicleTypeStr
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 12f
        }
        infoCol.addView(typeText)

        topRow.addView(infoCol)
        cardContent.addView(topRow)

        // Bottom row: duration, entry/exit, fee in grid
        val bottomRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (12 * density).toInt() }
            orientation = LinearLayout.HORIZONTAL
        }

        // Duration column
        val durationCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }
        val durationLabel = TextView(this).apply {
            text = getString(R.string.history_stay_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 10f
            alpha = 0.5f
        }
        durationCol.addView(durationLabel)
        val durationVal = TextView(this).apply {
            text = entry.durationLabel
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 16f
        }
        durationCol.addView(durationVal)
        bottomRow.addView(durationCol)

        // Entry/Exit column
        val entryExitCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
        }
        val entryExitLabel = TextView(this).apply {
            text = getString(R.string.history_entry_exit_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 10f
            alpha = 0.5f
        }
        entryExitCol.addView(entryExitLabel)

        val entryStr = formatDateTime(e.createdAt).substringAfter(" ")
        val exitStr = formatDateTime(e.exitedAt).substringAfter(" ")
        val entryExitVal = TextView(this).apply {
            text = "$entryStr — $exitStr"
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 14f
        }
        entryExitCol.addView(entryExitVal)
        bottomRow.addView(entryExitCol)

        // Fee column
        val feeCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
        }
        val feeLabel = TextView(this).apply {
            text = getString(R.string.history_paid_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 10f
            alpha = 0.5f
        }
        feeCol.addView(feeLabel)
        val feeVal = TextView(this).apply {
            text = getString(R.string.history_fee, entry.fee)
            setTextColor(
                MaterialColors.getColor(binding.root, R.attr.semanticSuccessCustom)
            )
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        feeCol.addView(feeVal)
        bottomRow.addView(feeCol)

        cardContent.addView(bottomRow)
        card.addView(cardContent)
        return card
    }

    private fun showPhotoDialog(plate: String, photos: List<Pair<String, String>>) {
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

        for ((label, url) in photos) {
            val labelText = TextView(this).apply {
                text = label
                setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
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
                load(url) {
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
            .setTitle("Fotos - $plate")
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val bottomNavH = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = bottomNavH + navBarInsets.bottom)
            insets
        }
    }
}
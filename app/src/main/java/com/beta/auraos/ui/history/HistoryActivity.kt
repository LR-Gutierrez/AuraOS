package com.beta.auraos.ui.history

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityHistoryBinding
import com.beta.auraos.util.formatDateTime
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
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

        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }

        setupFilterButtons()
        setupDateRangeButtons()
        setupSearch()
        setupSortButtons()
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

    private fun setupFilterButtons() {
        updateFilterSelection(HistoryFilter.TODAY)

        binding.btnFilterToday.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility = View.GONE
            viewModel.setFilter(HistoryFilter.TODAY, branchId)
            updateFilterSelection(HistoryFilter.TODAY)
        }

        binding.btnFilterAll.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility = View.GONE
            viewModel.setFilter(HistoryFilter.ALL, branchId)
            updateFilterSelection(HistoryFilter.ALL)
        }

        binding.btnFilterRange.setOnClickListener {
            val branchId = sessionManager.getSelectedBranchId() ?: return@setOnClickListener
            binding.layoutDateRange.visibility =
                if (binding.layoutDateRange.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            viewModel.setFilter(HistoryFilter.RANGE, branchId)
            updateFilterSelection(HistoryFilter.RANGE)
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

    private fun setupSortButtons() {
        updateSortSelection(SortField.DATE)

        binding.btnSortDate.setOnClickListener {
            viewModel.toggleSort(SortField.DATE)
            updateSortSelection(SortField.DATE)
        }
        binding.btnSortFee.setOnClickListener {
            viewModel.toggleSort(SortField.FEE)
            updateSortSelection(SortField.FEE)
        }
        binding.btnSortDuration.setOnClickListener {
            viewModel.toggleSort(SortField.DURATION)
            updateSortSelection(SortField.DURATION)
        }
    }

    private fun updateSortSelection(field: SortField) {
        val state = viewModel.uiState.value
        val order = if (state is HistoryUiState.Success) state.sortOrder else SortOrder.DESC

        binding.btnSortDate.isSelected = field == SortField.DATE
        binding.btnSortFee.isSelected = field == SortField.FEE
        binding.btnSortDuration.isSelected = field == SortField.DURATION

        val arrow = if (order == SortOrder.DESC) " ↓" else " ↑"
        binding.btnSortDate.text = getString(R.string.history_sort_date) + (if (field == SortField.DATE) arrow else "")
        binding.btnSortFee.text = getString(R.string.history_sort_fee) + (if (field == SortField.FEE) arrow else "")
        binding.btnSortDuration.text = getString(R.string.history_sort_duration) + (if (field == SortField.DURATION) arrow else "")
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

        binding.contentContainer.removeViews(6, binding.contentContainer.childCount - 6)

        when (state.filter) {
            HistoryFilter.TODAY -> {
                binding.todaySummaryLabel.setText(R.string.history_today_count)
                binding.todayRevenueLabel.setText(R.string.history_today_revenue)
                binding.tvFilterLabel.visibility = View.GONE
            }
            HistoryFilter.ALL -> {
                binding.todaySummaryLabel.setText(R.string.history_all_count)
                binding.todayRevenueLabel.setText(R.string.history_all_revenue)
                binding.tvFilterLabel.visibility = View.GONE
            }
            HistoryFilter.RANGE -> {
                binding.todaySummaryLabel.setText(R.string.history_range_count)
                binding.todayRevenueLabel.setText(R.string.history_range_revenue)
                binding.tvFilterLabel.visibility = View.VISIBLE
                val from = state.filterFrom ?: "..."
                val to = state.filterTo ?: "..."
                binding.tvFilterLabel.text = "$from → $to"
            }
        }

        if (state.totalFiltered > HistoryViewModel.PAGE_SIZE) {
            binding.tvShowingCount.visibility = View.VISIBLE
            binding.tvShowingCount.text = getString(R.string.history_showing, state.shownCount, state.totalFiltered)
        } else {
            binding.tvShowingCount.visibility = View.GONE
        }

        binding.btnLoadMore.visibility = if (state.hasMore) View.VISIBLE else View.GONE

        if (state.entries.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        for (historyEntry in state.entries) {
            val card = createHistoryCard(historyEntry)
            binding.contentContainer.addView(card)

            val lp = card.layoutParams as LinearLayout.LayoutParams
            if (card !== binding.contentContainer.getChildAt(6)) {
                lp.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
        }
    }

    private fun createHistoryCard(entry: HistoryEntry): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val e = entry.entry

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(this, R.attr.cardGlassFillColorCustom)
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            setContentPadding(padding, padding, padding, padding)
        }

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val plateText = TextView(this).apply {
            text = e.plate
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 18f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        container.addView(plateText)

        val vehicleTypeStr = e.vehicleType.replaceFirstChar { it.uppercase() }
        val vipStr = if (e.isVip) " \u2605 VIP" else ""
        val entryStr = formatDateTime(e.createdAt)
        val exitStr = formatDateTime(e.exitedAt)

        val datesText = TextView(this).apply {
            text = "$vehicleTypeStr$vipStr"
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 13f
        }
        container.addView(datesText)

        val timelineText = TextView(this).apply {
            text = getString(R.string.history_timeline, entryStr, exitStr)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 12f
        }
        container.addView(timelineText)

        val bottomRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * density).toInt() }
        }

        val durationLabel = TextView(this).apply {
            text = getString(R.string.history_duration, entry.durationLabel)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            textSize = 12f
        }
        bottomRow.addView(durationLabel)

        val feeLabel = TextView(this).apply {
            text = getString(R.string.history_fee, entry.fee)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.semanticSuccessCustom))
            textSize = 14f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginStart = (8 * density).toInt() }
        }
        bottomRow.addView(feeLabel)

        container.addView(bottomRow)
        card.addView(container)
        return card
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

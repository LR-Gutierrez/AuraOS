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
import com.beta.auraos.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.ui.branches.BranchListActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.beta.auraos.databinding.ActivityAnalyticsBinding
import com.beta.auraos.ui.history.ActivityType
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
import com.beta.auraos.util.formatDate
import com.beta.auraos.util.formatDateTime
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : BaseActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var viewModel: HistoryViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var branchRepository: BranchRepository

    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null
    private var membershipNameMap: Map<String, String> = emptyMap()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        val api = ApiClient.createApi<BranchApi>()
        branchRepository = BranchRepository(api, db)

        val repository = VehicleEntryRepository(db)
        val membershipRepository = MembershipRepository(db)
        val factory = HistoryViewModelFactory(repository, membershipRepository)
        viewModel = ViewModelProvider(this, factory)[HistoryViewModel::class.java]

        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBranchSelector()
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

        loadBranches()
    }

    private fun setupBranchSelector() {
        binding.btnBranchSelector.setOnClickListener { showBranchSelector() }
        binding.tvBranchName.setOnClickListener { showBranchSelector() }
    }

    private fun loadBranches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = branchRepository.getBranches()
            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        branches = result.data
                        restoreOrSelectBranch()
                    }
                    is Result.Error -> {
                        binding.tvBranchName.text = getString(R.string.branch_load_error)
                    }
                }
            }
        }
    }

    private fun restoreOrSelectBranch() {
        val savedId = sessionManager.getSelectedBranchId()
        val saved = if (savedId != null) branches.find { it.id == savedId } else null

        if (saved != null) {
            selectedBranch = saved
            binding.tvBranchName.text = saved.name
            loadHistory()
        } else if (branches.isNotEmpty()) {
            selectedBranch = branches.first()
            sessionManager.saveSelectedBranchId(selectedBranch!!.id)
            binding.tvBranchName.text = selectedBranch!!.name
            loadHistory()
        } else {
            binding.tvBranchName.text = getString(R.string.branch_no_branch)
        }
    }

    private fun showBranchSelector() {
        if (branches.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_branches_title))
                .setMessage(getString(R.string.dialog_no_branches_message))
                .setPositiveButton(getString(R.string.dialog_setup)) { _, _ ->
                    startActivity(android.content.Intent(this, BranchListActivity::class.java))
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
            return
        }

        val names = branches.map { it.name }.toTypedArray()
        val selectedIndex = if (selectedBranch != null) branches.indexOf(selectedBranch) else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_branch_title))
            .setSingleChoiceItems(names, if (selectedIndex >= 0) selectedIndex else 0) { dialog, which ->
                val branch = branches[which]
                selectedBranch = branch
                sessionManager.saveSelectedBranchId(branch.id)
                binding.tvBranchName.text = branch.name
                dialog.dismiss()
                loadHistory()
            }
            .setPositiveButton(getString(R.string.dialog_manage_branches)) { _, _ ->
                startActivity(android.content.Intent(this, BranchListActivity::class.java))
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
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
                R.id.nav_profile -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = R.id.nav_profile,
                    )
                    TabNavigator.navigate(this, R.id.nav_profile)
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
        if (branchId != null) {
            viewModel.loadHistory(branchId)
        }
    }

    private fun bindTrendText(tv: TextView, trend: Int) {
        if (trend == 0) {
            tv.text = getString(R.string.history_metric_trend, 0)
            tv.setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
        } else if (trend > 0) {
            tv.text = getString(R.string.analytics_trend_up, trend)
            tv.setTextColor(MaterialColors.getColor(binding.root, R.attr.semanticSuccessCustom))
        } else {
            tv.text = getString(R.string.analytics_trend_down, trend)
            tv.setTextColor(MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError))
        }
    }

    private fun bindData(state: HistoryUiState.Success) {
        membershipNameMap = state.membershipNameMap
        binding.tvTodayCount.text = state.summary.outboundCount.toString()
        binding.tvTodayRevenue.text = getString(R.string.history_revenue_amount, state.summary.revenue)
        binding.tvAvgDuration.text = state.summary.avgDurationLabel
        binding.tvAvgDurationTarget.text = getString(R.string.history_metric_target, state.summary.avgDurationLabel)

        binding.tvInboundCount.text = state.summary.inboundCount.toString()
        binding.tvOccupancyCount.text = state.summary.occupancyCount.toString()
        binding.tvOutboundCount.text = state.summary.outboundCount.toString()

        bindTrendText(binding.tvInboundTrend, state.summary.inboundTrend)
        bindTrendText(binding.tvOccupancyTrend, state.summary.occupancyTrend)
        bindTrendText(binding.tvOutboundTrend, state.summary.outboundTrend)

        binding.tvTrendText.text = getString(R.string.history_metric_trend, state.summary.outboundTrend)

        val peakLabel = state.summary.peakHourLabel
        val peakRevenue = state.summary.peakHourRevenue
        binding.tvPeakText.text = if (peakLabel.isNotBlank()) {
            "$peakLabel — $${"%.2f".format(peakRevenue)}"
        } else {
            getString(R.string.history_metric_peak, peakRevenue)
        }

        val staticChildCount = 10
        val dynamicCardCount = binding.contentContainer.childCount - staticChildCount
        if (dynamicCardCount > 0) {
            binding.contentContainer.removeViews(7, dynamicCardCount)
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

    private fun resolvePlateDisplayName(e: com.beta.auraos.data.model.VehicleEntry): String {
        return if (e.plate == "NFC" && !e.membershipId.isNullOrBlank()) {
            membershipNameMap[e.membershipId] ?: e.plate
        } else e.plate
    }

    private fun createHistoryCard(entry: HistoryEntry): View {
        val density = resources.displayMetrics.density
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        val e = entry.entry
        val type = entry.activityType

        val photoFields = listOfNotNull(
            getString(R.string.cd_plate_photo) to e.platePhotoUrl,
            getString(R.string.photo_label_front) to e.frontPhotoUrl,
            getString(R.string.photo_label_rear) to e.rearPhotoUrl,
            getString(R.string.photo_label_left) to e.leftPhotoUrl,
            getString(R.string.photo_label_right) to e.rightPhotoUrl
        ).mapNotNull { (label, url) ->
            val fullUrl = PhotoUrlBuilder.build(url)
            if (fullUrl != null) label to fullUrl else null
        }

        val displayName = resolvePlateDisplayName(e)

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(this, R.attr.branchSurfaceCustom)
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            strokeColor = MaterialColors.getColor(this, R.attr.dividerColorCustom)
            strokeWidth = resources.getDimensionPixelSize(R.dimen.stroke_thin)
            setContentPadding(0, 0, 0, 0)
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
                setOnClickListener { showPhotoDialog(displayName, photoFields) }
            }
        }

        val cardContent = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val topRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val thumbUrl = PhotoUrlBuilder.build(e.platePhotoUrl)
        val thumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.thumbnail_size),
                resources.getDimensionPixelSize(R.dimen.thumbnail_size)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (thumbUrl != null) {
                load(thumbUrl) {
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
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_small_plus), 0, 0, 0
            )
        }

        val vehicleTypeStr = e.vehicleType.replaceFirstChar { it.uppercase() }
        val vipStr = if (e.isVip) " \u2605 VIP" else ""

        val plateText = TextView(this).apply {
            text = "$displayName  $vipStr"
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 16f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        infoCol.addView(plateText)

        val secondaryText = when (type) {
            ActivityType.INBOUND -> getString(R.string.traffic_card_inbound_subtitle)
            ActivityType.OCCUPIED -> "${vehicleTypeStr} • ${getString(R.string.traffic_card_occupied_subtitle)}"
            ActivityType.OUTBOUND -> vehicleTypeStr
        }
        val secondaryColor = MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)

        val typeText = TextView(this).apply {
            text = secondaryText
            setTextColor(secondaryColor)
            textSize = 12f
        }
        infoCol.addView(typeText)
        topRow.addView(infoCol)
        cardContent.addView(topRow)

        val bottomRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small_plus)
            }
            orientation = LinearLayout.HORIZONTAL
        }

        val durationCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
                ).also { it.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small) }
            orientation = LinearLayout.VERTICAL
        }

        val durationLabel = TextView(this).apply {
            text = getString(R.string.history_stay_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_size_overline))
            alpha = 0.5f
            letterSpacing = 0.08f
        }
        durationCol.addView(durationLabel)

        val displayDuration = when (type) {
            ActivityType.INBOUND -> getString(R.string.traffic_card_pending_text)
            else -> entry.durationLabel
        }
        val durationVal = TextView(this).apply {
            text = displayDuration
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            textSize = 16f
            typeface = Typeface.create("sans-serif-mono", Typeface.BOLD)
        }
        durationCol.addView(durationVal)
        bottomRow.addView(durationCol)

        val entryExitCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.5f
                ).also { it.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small) }
            orientation = LinearLayout.VERTICAL
        }
        val entryExitLabel = TextView(this).apply {
            text = getString(R.string.history_entry_exit_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_size_overline))
            alpha = 0.5f
            letterSpacing = 0.08f
        }
        entryExitCol.addView(entryExitLabel)

        val entryDate = formatDate(e.createdAt)
        val entryTime = formatDateTime(e.createdAt).substringAfter(" ")
        val dateText = TextView(this).apply {
            text = entryDate
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_size_caption))
            alpha = 0.7f
        }
        entryExitCol.addView(dateText)

        val displayExit = when (type) {
            ActivityType.INBOUND -> getString(R.string.traffic_card_pending_text)
            ActivityType.OCCUPIED -> getString(R.string.traffic_card_occupied_status)
            ActivityType.OUTBOUND -> formatDateTime(e.exitedAt).substringAfter(" ")
        }
        val entryExitVal = TextView(this).apply {
            text = "$entryTime — $displayExit"
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textPrimaryCustom))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_size_small))
        }
        entryExitCol.addView(entryExitVal)
        bottomRow.addView(entryExitCol)

        val feeCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.8f
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.END
        }
        val feeLabel = TextView(this).apply {
            text = getString(R.string.history_paid_label)
            setTextColor(MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.text_size_overline))
            alpha = 0.5f
            letterSpacing = 0.08f
        }
        feeCol.addView(feeLabel)

        val (displayFee, feeColor) = when (type) {
            ActivityType.INBOUND -> getString(R.string.traffic_card_pending_text) to
                MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)
            ActivityType.OCCUPIED -> getString(R.string.traffic_card_pending_text) to
                MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)
            ActivityType.OUTBOUND -> getString(R.string.history_fee, entry.fee) to
                MaterialColors.getColor(binding.root, R.attr.semanticSuccessCustom)
        }
        val feeVal = TextView(this).apply {
            text = displayFee
            setTextColor(feeColor)
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
                    (120 * density).toInt()
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
            .setTitle(getString(R.string.photos_title, plate))
            .setView(scroll)
            .setPositiveButton(getString(R.string.dialog_close), null)
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
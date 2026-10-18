package com.beta.auraos

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import coil.load
import coil.transform.RoundedCornersTransformation
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.databinding.ActivityAnalyticsBinding
import com.beta.auraos.databinding.ItemPcShareRowBinding
import com.beta.auraos.databinding.ItemPcVehicleRowBinding
import com.beta.auraos.ui.analytics.AnalyticsFilter
import com.beta.auraos.ui.analytics.AnalyticsPeriod
import com.beta.auraos.ui.analytics.AnalyticsState
import com.beta.auraos.ui.analytics.AnalyticsViewModel
import com.beta.auraos.ui.analytics.AnalyticsViewModelFactory
import com.beta.auraos.ui.analytics.Breakdown
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PcUi
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.TabNavigator
import com.google.android.material.color.MaterialColors
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AnalyticsActivity : BaseActivity() {

    private lateinit var binding: ActivityAnalyticsBinding
    private lateinit var viewModel: AnalyticsViewModel
    private lateinit var sessionManager: SessionManager

    private var current: AnalyticsState? = null
    private var selectedBar: Int? = null
    private var shownExits = PAGE_SIZE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = AppDatabase.getInstance(this)
        val factory = AnalyticsViewModelFactory(
            VehicleEntryRepository(db),
            BranchRepository(ApiClient.createApi<BranchApi>(), db),
            MembershipRepository(db),
        )
        viewModel = ViewModelProvider(this, factory)[AnalyticsViewModel::class.java]

        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_analytics)
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        binding.btnPeriod.setOnClickListener { showPeriodMenu() }
        binding.btnBranch.setOnClickListener { showBranchMenu() }
        binding.btnType.setOnClickListener { showTypeMenu() }
        binding.chart.onBarSelected = { index ->
            selectedBar = index
            current?.let { bindSelection(it) }
        }
        PcUi.underline(binding.btnLoadMore)
        binding.btnLoadMore.setOnClickListener {
            shownExits += PAGE_SIZE
            current?.let { bindExits(it) }
        }

        binding.btnPeriod.text = periodShort(AnalyticsPeriod.DAYS_7)
        binding.btnBranch.text = getString(R.string.pc_all_branches)
        binding.btnType.text = typeLabel(null)

        viewModel.loading.observe(this) {
            binding.loadingOverlay.visibility = if (it) View.VISIBLE else View.GONE
        }
        viewModel.state.observe(this) { bind(it) }
        viewModel.load(sessionManager.getSelectedBranchId())
    }

    private fun applyFilter(filter: AnalyticsFilter) {
        selectedBar = null
        shownExits = PAGE_SIZE
        viewModel.setFilter(filter)
    }

    private fun showPeriodMenu() {
        val state = current ?: return
        val popup = PopupMenu(this, binding.btnPeriod)
        AnalyticsPeriod.entries.forEachIndexed { i, period ->
            popup.menu.add(0, i, i, periodShort(period))
        }
        popup.setOnMenuItemClickListener { item ->
            applyFilter(state.filter.copy(period = AnalyticsPeriod.entries[item.itemId]))
            true
        }
        popup.show()
    }

    private fun showBranchMenu() {
        val state = current ?: return
        val popup = PopupMenu(this, binding.btnBranch)
        popup.menu.add(0, 0, 0, R.string.pc_all_branches)
        state.branches.forEachIndexed { i, b -> popup.menu.add(0, i + 1, i + 1, b.name) }
        popup.setOnMenuItemClickListener { item ->
            val branchId = if (item.itemId == 0) null else state.branches[item.itemId - 1].id
            applyFilter(state.filter.copy(branchId = branchId))
            true
        }
        popup.show()
    }

    private fun showTypeMenu() {
        val state = current ?: return
        val types = listOf(null, "light", "heavy", "motorcycle")
        val popup = PopupMenu(this, binding.btnType)
        types.forEachIndexed { i, t -> popup.menu.add(0, i, i, typeLabel(t)) }
        popup.setOnMenuItemClickListener { item ->
            applyFilter(state.filter.copy(vehicleType = types[item.itemId]))
            true
        }
        popup.show()
    }

    private fun periodShort(p: AnalyticsPeriod): String = getString(
        when (p) {
            AnalyticsPeriod.TODAY -> R.string.pc_period_today
            AnalyticsPeriod.DAYS_7 -> R.string.pc_period_7d
            AnalyticsPeriod.DAYS_30 -> R.string.pc_period_30d
            AnalyticsPeriod.MONTHS_12 -> R.string.pc_period_12m
        }
    )

    private fun periodLong(p: AnalyticsPeriod): String = getString(
        when (p) {
            AnalyticsPeriod.TODAY -> R.string.pc_period_label_today
            AnalyticsPeriod.DAYS_7 -> R.string.pc_period_label_7d
            AnalyticsPeriod.DAYS_30 -> R.string.pc_period_label_30d
            AnalyticsPeriod.MONTHS_12 -> R.string.pc_period_label_12m
        }
    )

    private fun typeLabel(type: String?): String =
        if (type == null) getString(R.string.pc_all_types) else ParkingFormat.vehicleType(this, type)

    private fun bind(state: AnalyticsState) {
        current = state
        val f = state.filter
        binding.btnPeriod.text = periodShort(f.period)
        binding.btnBranch.text = state.branches.find { it.id == f.branchId }?.name
            ?: getString(R.string.pc_all_branches)
        binding.btnType.text = typeLabel(f.vehicleType)

        binding.tvTotalLabel.text = getString(R.string.pc_revenue_label, periodLong(f.period))
        binding.tvTotal.text = ParkingFormat.money(state.total, state.currency)
        binding.tvTotalSub.text = state.changePercent?.let {
            getString(R.string.pc_change_vs_previous, ParkingFormat.percentChange(it))
        } ?: getString(R.string.pc_no_previous)

        binding.tvVehicles.text = ParkingFormat.pad2(state.vehicles)
        binding.tvAvgTicket.text = ParkingFormat.money(
            if (state.vehicles > 0) state.total / state.vehicles else 0.0, state.currency
        )

        bindSelection(state)
        bindBreakdowns(state)
        bindExits(state)
    }

    private fun bindSelection(state: AnalyticsState) {
        val bars = state.bars
        if (bars.isEmpty()) return
        val sel = (selectedBar ?: bars.lastIndex).coerceIn(0, bars.lastIndex)
        binding.chart.setData(bars.map { it.value }, bars.indices.map { barLabel(state, it) }, sel)
        binding.tvSelLabel.text = barFullLabel(state.filter.period, bars[sel].start)
        binding.tvSelValue.text = ParkingFormat.money(bars[sel].value, state.currency)
    }

    private fun barLabel(state: AnalyticsState, i: Int): String {
        val start = Date(state.bars[i].start)
        return when (state.filter.period) {
            AnalyticsPeriod.TODAY -> if (i % 6 == 0) ParkingFormat.hourLabel(start) else ""
            AnalyticsPeriod.DAYS_7 -> ParkingFormat.dateLabel(start, "EEEEE").uppercase()
            AnalyticsPeriod.DAYS_30 -> if (i % 7 == 0) ParkingFormat.dateLabel(start, "d") else ""
            AnalyticsPeriod.MONTHS_12 -> ParkingFormat.dateLabel(start, "MMMMM").uppercase()
        }
    }

    private fun barFullLabel(period: AnalyticsPeriod, start: Long): String {
        val d = Date(start)
        return when (period) {
            AnalyticsPeriod.TODAY -> getString(
                R.string.pc_hour_range, ParkingFormat.hhmm(d), ParkingFormat.hhmm(Date(start + 3_600_000))
            )
            AnalyticsPeriod.DAYS_7 -> ParkingFormat.dateLabel(d, "EEE d MMM")
            AnalyticsPeriod.DAYS_30 -> ParkingFormat.dateLabel(d, ParkingFormat.dayMonthPattern())
            AnalyticsPeriod.MONTHS_12 -> ParkingFormat.dateLabel(d, "MMMM yyyy")
        }
    }

    private fun bindBreakdowns(state: AnalyticsState) {
        val container = binding.llBreakdowns
        container.removeAllViews()
        fun group(titleRes: Int, rows: List<Breakdown>, label: (Breakdown) -> String) {
            if (rows.isEmpty()) return
            val title = TextView(this).apply {
                setTextAppearance(R.style.TextAppearance_Pc_Section)
                setText(titleRes)
            }
            ViewCompat.setAccessibilityHeading(title, true)
            container.addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (24 * resources.displayMetrics.density).toInt() })
            rows.forEach { row ->
                val item = ItemPcShareRowBinding.inflate(layoutInflater, container, true)
                val pct = (row.share * 100).roundToInt()
                item.tvLabel.text = label(row)
                item.tvValue.text = getString(R.string.pc_share_value, ParkingFormat.money(row.amount, state.currency), pct)
                item.pbShare.progress = pct
            }
        }
        group(R.string.pc_by_branch, state.byBranch) { it.label }
        group(R.string.pc_by_type, state.byType) { ParkingFormat.vehicleType(this, it.label) }
    }

    private fun bindExits(state: AnalyticsState) {
        val list = binding.llExits
        list.removeAllViews()
        state.exits.take(shownExits).forEach { entry ->
            val item = ItemPcVehicleRowBinding.inflate(layoutInflater, list, true)
            val name = displayName(entry, state.memberNames)
            item.tvPlate.text = name
            val kind = if (entry.membershipId != null) getString(R.string.pc_member)
            else ParkingFormat.vehicleType(this, entry.vehicleType)
            val stay = entry.duration ?: ParkingFormat.parseIso(entry.exitedAt)
                ?.let { ParkingFormat.duration(entry.createdAt, it) } ?: "--"
            item.tvMeta.text = getString(R.string.pc_meta, kind, stay)
            item.tvAmount.text = ParkingFormat.money(entry.fee, state.currency)
            item.tvSince.text = getString(
                R.string.pc_hour_range,
                ParkingFormat.dateTime(ParkingFormat.parseIso(entry.createdAt) ?: Date()),
                ParkingFormat.hhmm(entry.exitedAt),
            )
            val photos = photoFields(entry)
            if (photos.isNotEmpty()) {
                item.root.setOnClickListener { showPhotoDialog(name, photos) }
            } else {
                item.root.isClickable = false
            }
        }
        binding.tvEmptyState.visibility = if (state.exits.isEmpty()) View.VISIBLE else View.GONE
        val remaining = state.exits.size - shownExits
        binding.btnLoadMore.visibility = if (remaining > 0) View.VISIBLE else View.GONE
        if (remaining > 0) {
            binding.btnLoadMore.text = minOf(remaining, PAGE_SIZE).let { resources.getQuantityString(R.plurals.pc_load_more, it, it) }
        }
    }

    private fun displayName(e: VehicleEntry, memberNames: Map<String, String>): String =
        if (e.plate == "NFC" && !e.membershipId.isNullOrBlank()) memberNames[e.membershipId] ?: e.plate
        else e.plate

    private fun photoFields(e: VehicleEntry): List<Pair<String, String>> = listOf(
        getString(R.string.pc_slot_plate) to e.platePhotoUrl,
        getString(R.string.pc_slot_front) to e.frontPhotoUrl,
        getString(R.string.pc_slot_rear) to e.rearPhotoUrl,
        getString(R.string.pc_slot_left) to e.leftPhotoUrl,
        getString(R.string.pc_slot_right) to e.rightPhotoUrl,
    ).mapNotNull { (label, url) -> PhotoUrlBuilder.build(url)?.let { label to it } }

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

    companion object {
        private const val PAGE_SIZE = 20
    }
}

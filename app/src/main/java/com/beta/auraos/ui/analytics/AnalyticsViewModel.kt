package com.beta.auraos.ui.analytics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SyncEventBus
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.util.ParkingFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class AnalyticsPeriod { TODAY, DAYS_7, DAYS_30, MONTHS_12 }

data class AnalyticsFilter(
    val period: AnalyticsPeriod = AnalyticsPeriod.DAYS_7,
    /** null = all branches. */
    val branchId: String? = null,
    /** null = all vehicle types ("light", "heavy", "motorcycle"). */
    val vehicleType: String? = null,
)

/** One bucket of the revenue chart; [start] is its local start time in millis. */
data class RevenueBar(val start: Long, val value: Double)

data class Breakdown(val label: String, val amount: Double, val share: Double)

data class AnalyticsState(
    val filter: AnalyticsFilter,
    val branches: List<Branch>,
    val bars: List<RevenueBar>,
    val total: Double,
    /** Change vs. the previous period of the same length; null when that period has no revenue. */
    val changePercent: Double?,
    val vehicles: Int,
    val byBranch: List<Breakdown>,
    val byType: List<Breakdown>,
    val exits: List<VehicleEntry>,
    val memberNames: Map<String, String>,
    val currency: String?,
)

/**
 * Revenue analytics computed from exited vehicle entries (the same records the cash
 * closure audits), bucketed by hour, day or month in local time.
 */
class AnalyticsViewModel(
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository,
    private val membershipRepository: MembershipRepository,
) : ViewModel() {

    private val _state = MutableLiveData<AnalyticsState>()
    val state: LiveData<AnalyticsState> = _state

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private var filter = AnalyticsFilter()
    private var branches: List<Branch> = emptyList()
    private var history: Map<String, List<VehicleEntry>> = emptyMap()
    private var memberNames: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            SyncEventBus.events.collect { if (branches.isNotEmpty()) reload() }
        }
    }

    fun load(initialBranchId: String?) {
        if (branches.isEmpty()) filter = filter.copy(branchId = initialBranchId)
        viewModelScope.launch { reload() }
    }

    fun setFilter(newFilter: AnalyticsFilter) {
        filter = newFilter
        publish()
    }

    private suspend fun reload() {
        _loading.value = true
        // Local-first: publish what the device already has, then refine with the server.
        if (branches.isEmpty()) {
            branches = withContext(Dispatchers.IO) { branchRepository.getBranchesLocal() }
            if (branches.isNotEmpty()) {
                history = withContext(Dispatchers.IO) {
                    branches.associate { b ->
                        b.id to vehicleEntryRepository.getHistoryEntriesLocal(b.id).filter { it.exitedAt != null }
                    }
                }
                publish()
            }
        }
        branches = withContext(Dispatchers.IO) {
            (branchRepository.getBranches() as? Result.Success)?.data ?: branches
        }
        if (filter.branchId != null && branches.none { it.id == filter.branchId }) {
            filter = filter.copy(branchId = null)
        }
        history = withContext(Dispatchers.IO) {
            branches.associate { branch ->
                val remote = vehicleEntryRepository.getHistoryEntries(branch.id)
                val entries = (remote as? Result.Success)?.data?.entries
                    ?: vehicleEntryRepository.getHistoryEntriesLocal(branch.id)
                branch.id to entries.filter { it.exitedAt != null }
            }
        }
        memberNames = withContext(Dispatchers.IO) {
            (membershipRepository.getMemberships() as? Result.Success)?.data
                ?.associate { it.id to it.memberName }.orEmpty()
        }
        _loading.value = false
        publish()
    }

    private fun publish() {
        val f = filter
        val (bucketStarts, previousStart) = buckets(f.period)
        val periodStart = bucketStarts.first()
        val periodEnd = nextBucket(bucketStarts.last(), f.period)

        val scoped = history
            .filterKeys { f.branchId == null || it == f.branchId }
            .values.flatten()
            .filter { f.vehicleType == null || it.vehicleType == f.vehicleType }
            .mapNotNull { e -> ParkingFormat.parseIso(e.exitedAt)?.time?.let { it to e } }

        val inPeriod = scoped.filter { (t, _) -> t in periodStart until periodEnd }
        val previous = scoped.filter { (t, _) -> t in previousStart until periodStart }

        val bars = bucketStarts.map { start ->
            val end = nextBucket(start, f.period)
            RevenueBar(start, inPeriod.filter { (t, _) -> t in start until end }.sumOf { it.second.fee ?: 0.0 })
        }
        val total = inPeriod.sumOf { it.second.fee ?: 0.0 }
        val previousTotal = previous.sumOf { it.second.fee ?: 0.0 }
        val change = if (previousTotal > 0) (total - previousTotal) / previousTotal * 100 else null

        val byBranch = if (f.branchId == null && branches.size > 1) {
            branches.map { b ->
                val amount = inPeriod.filter { it.second.branchId == b.id }.sumOf { it.second.fee ?: 0.0 }
                Breakdown(b.name, amount, if (total > 0) amount / total else 0.0)
            }
        } else emptyList()

        val byType = if (f.vehicleType == null) {
            inPeriod.groupBy { it.second.vehicleType }.map { (type, list) ->
                val amount = list.sumOf { it.second.fee ?: 0.0 }
                Breakdown(type, amount, if (total > 0) amount / total else 0.0)
            }.sortedByDescending { it.amount }
        } else emptyList()

        val currency = branches.find { it.id == f.branchId }?.currency ?: branches.firstOrNull()?.currency

        _state.value = AnalyticsState(
            filter = f,
            branches = branches,
            bars = bars,
            total = total,
            changePercent = change,
            vehicles = inPeriod.size,
            byBranch = byBranch,
            byType = byType,
            exits = inPeriod.sortedByDescending { it.first }.map { it.second },
            memberNames = memberNames,
            currency = currency,
        )
    }

    /** Bucket starts for the period ending now, plus the start of the previous equal period. */
    private fun buckets(period: AnalyticsPeriod): Pair<List<Long>, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val (count, field) = when (period) {
            AnalyticsPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                24 to Calendar.HOUR_OF_DAY
            }
            AnalyticsPeriod.DAYS_7, AnalyticsPeriod.DAYS_30 -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.add(Calendar.DAY_OF_MONTH, -((if (period == AnalyticsPeriod.DAYS_7) 7 else 30) - 1))
                (if (period == AnalyticsPeriod.DAYS_7) 7 else 30) to Calendar.DAY_OF_MONTH
            }
            AnalyticsPeriod.MONTHS_12 -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -11)
                12 to Calendar.MONTH
            }
        }
        val first = cal.timeInMillis
        val starts = (0 until count).map {
            (cal.clone() as Calendar).apply { timeInMillis = first; add(field, it) }.timeInMillis
        }
        val previous = (cal.clone() as Calendar).apply { timeInMillis = first; add(field, -count) }.timeInMillis
        return starts to previous
    }

    private fun nextBucket(start: Long, period: AnalyticsPeriod): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        when (period) {
            AnalyticsPeriod.TODAY -> cal.add(Calendar.HOUR_OF_DAY, 1)
            AnalyticsPeriod.DAYS_7, AnalyticsPeriod.DAYS_30 -> cal.add(Calendar.DAY_OF_MONTH, 1)
            AnalyticsPeriod.MONTHS_12 -> cal.add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis
    }
}

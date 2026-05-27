package com.beta.auraos.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.HistoryResult
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.model.VehicleEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class ActivityType {
    INBOUND,
    OCCUPIED,
    OUTBOUND
}

data class HistoryEntry(
    val entry: VehicleEntry,
    val durationLabel: String,
    val fee: Double,
    val activityType: ActivityType = ActivityType.OUTBOUND
)

enum class HistoryFilter(val label: String) {
    TODAY("Hoy"),
    ALL("Todos"),
    RANGE("Rango")
}

enum class SortField { DATE, FEE, DURATION }
enum class SortOrder { ASC, DESC }

data class FilterSummary(
    val count: Int,
    val revenue: Double,
    val avgDurationLabel: String = "0m",
    val inboundCount: Int = 0,
    val occupancyCount: Int = 0,
    val outboundCount: Int = 0,
    val occupancyPercent: String = "0%",
    val inboundTrend: Int = 0,
    val occupancyTrend: Int = 0,
    val outboundTrend: Int = 0
)

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Success(
        val entries: List<HistoryEntry>,
        val summary: FilterSummary,
        val filter: HistoryFilter = HistoryFilter.TODAY,
        val filterFrom: String? = null,
        val filterTo: String? = null,
        val searchQuery: String = "",
        val sortField: SortField = SortField.DATE,
        val sortOrder: SortOrder = SortOrder.DESC,
        val totalFiltered: Int = 0,
        val shownCount: Int = 0,
        val hasMore: Boolean = false
    ) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

class HistoryViewModel(
    private val repository: VehicleEntryRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<HistoryUiState>(HistoryUiState.Loading)
    val uiState: LiveData<HistoryUiState> = _uiState

    private var currentFilter = HistoryFilter.TODAY
    private var filterFrom: String? = null
    private var filterTo: String? = null
    private var searchQuery = ""
    private var sortField = SortField.DATE
    private var sortOrder = SortOrder.DESC

    private var exitedEntries: List<VehicleEntry> = emptyList()
    private var activeEntries: List<VehicleEntry> = emptyList()
    private var yesterdayExitedCount: Int = 0
    private var yesterdayActiveCount: Int = 0

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pageSize = 20
    private val inboundThresholdMinutes = 30

    companion object {
        const val PAGE_SIZE = 20
    }

    fun loadHistory(branchId: String) {
        _uiState.value = HistoryUiState.Loading
        viewModelScope.launch {
            val historyDeferred = repository.getHistoryEntries(branchId)
            val activeDeferred = repository.getActiveEntries(branchId)

            val yesterday = dateFormat.format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.time
            )
            val yesterdayResult = repository.getHistoryEntries(
                branchId, from = yesterday, to = yesterday
            )
            yesterdayExitedCount = if (yesterdayResult is Result.Success) {
                yesterdayResult.data.entries.size
            } else 0

            val state = when {
                historyDeferred is Result.Success -> {
                    exitedEntries = historyDeferred.data.entries
                    activeEntries = if (activeDeferred is Result.Success) {
                        activeDeferred.data
                    } else {
                        emptyList()
                    }
                    applyFilters()
                }
                historyDeferred is Result.Error -> HistoryUiState.Error(
                    historyDeferred.exception.message ?: "Error al cargar historial"
                )
                else -> HistoryUiState.Error("Error desconocido al cargar datos")
            }
            _uiState.value = state
        }
    }

    fun setFilter(filter: HistoryFilter, branchId: String) {
        currentFilter = filter
        if (filter != HistoryFilter.RANGE) {
            filterFrom = null
            filterTo = null
        }
        _uiState.value = applyFilters()
    }

    fun setDateRange(from: String, to: String, branchId: String) {
        currentFilter = HistoryFilter.RANGE
        filterFrom = from
        filterTo = to
        _uiState.value = applyFilters()
    }

    fun setSearchQuery(query: String) {
        searchQuery = query
        _uiState.value = applyFilters()
    }

    fun toggleSort(field: SortField) {
        if (sortField == field) {
            sortOrder = if (sortOrder == SortOrder.DESC) SortOrder.ASC else SortOrder.DESC
        } else {
            sortField = field
            sortOrder = SortOrder.DESC
        }
        _uiState.value = applyFilters()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state !is HistoryUiState.Success || !state.hasMore) return
        val newShown = state.shownCount + PAGE_SIZE

        val filtered = buildMergedEntries()
        val totalFiltered = filtered.size
        val actualShown = minOf(newShown, totalFiltered)

        _uiState.value = state.copy(
            entries = filtered.take(actualShown),
            shownCount = actualShown,
            hasMore = actualShown < totalFiltered
        )
    }

    private fun buildMergedEntries(): List<HistoryEntry> {
        val merged = mutableListOf<HistoryEntry>()

        val now = System.currentTimeMillis()

        for (entry in activeEntries) {
            if (!matchesDateFilterActive(entry)) continue
            if (!matchesSearch(entry)) continue
            val createdAtMs = parseDateToMillis(entry.createdAt)
            val isRecent = createdAtMs != null && (now - createdAtMs) < inboundThresholdMinutes * 60 * 1000L
            val type = if (isRecent) ActivityType.INBOUND else ActivityType.OCCUPIED
            val durationLabel = if (entry.duration != null) entry.duration else "--"
            merged.add(
                HistoryEntry(
                    entry = entry,
                    durationLabel = durationLabel,
                    fee = 0.0,
                    activityType = type
                )
            )
        }

        for (entry in exitedEntries) {
            if (!matchesDateFilter(entry)) continue
            if (!matchesSearch(entry)) continue
            merged.add(
                HistoryEntry(
                    entry = entry,
                    durationLabel = entry.duration ?: "",
                    fee = entry.fee ?: 0.0,
                    activityType = ActivityType.OUTBOUND
                )
            )
        }

        merged.sortWith(comparator())
        return merged
    }

    private fun applyFilters(): HistoryUiState.Success {
        val merged = buildMergedEntries()

        val totalFiltered = merged.size
        val shownCount = minOf(pageSize, totalFiltered)

        val avgMinutes = merged
            .filter { it.activityType == ActivityType.OUTBOUND }
            .mapNotNull { parseDurationToMinutes(it.entry.duration) }
            .let { minutes -> if (minutes.isEmpty()) 0.0 else minutes.average() }

        val inboundCount = merged.count { it.activityType == ActivityType.INBOUND }
        val occupancyCount = merged.count { it.activityType == ActivityType.OCCUPIED }
        val outboundCount = merged.count { it.activityType == ActivityType.OUTBOUND }
        val totalCapacity = 70
        val occupancyPercent = ((occupancyCount.toFloat() / totalCapacity) * 100).toInt()

        val outboundTrend = if (yesterdayExitedCount > 0) {
            ((outboundCount - yesterdayExitedCount) * 100) / yesterdayExitedCount
        } else if (outboundCount > 0) {
            100
        } else 0

        val summary = FilterSummary(
            count = outboundCount,
            revenue = merged.filter { it.activityType == ActivityType.OUTBOUND }.sumOf { it.fee },
            avgDurationLabel = formatMinutes(avgMinutes.roundToInt()),
            inboundCount = inboundCount,
            occupancyCount = occupancyCount,
            outboundCount = outboundCount,
            occupancyPercent = "$occupancyPercent%",
            inboundTrend = if (inboundCount > 0) 100 else 0,
            occupancyTrend = if (yesterdayActiveCount > 0 && occupancyCount > yesterdayActiveCount) {
                ((occupancyCount - yesterdayActiveCount) * 100) / yesterdayActiveCount
            } else 0,
            outboundTrend = outboundTrend
        )

        return HistoryUiState.Success(
            entries = merged.take(shownCount),
            summary = summary,
            filter = currentFilter,
            filterFrom = filterFrom,
            filterTo = filterTo,
            searchQuery = searchQuery,
            sortField = sortField,
            sortOrder = sortOrder,
            totalFiltered = totalFiltered,
            shownCount = shownCount,
            hasMore = shownCount < totalFiltered
        )
    }

    private fun parseDurationToMinutes(duration: String?): Int? {
        if (duration.isNullOrBlank()) return null
        // Support formats: "2h 15m", "2h15m", "02:15", "45m", "1h"
        val hRegex = Regex("""(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?""")
        val m = hRegex.matchEntire(duration.trim())?.groupValues
        if (m != null && (m[1].isNotEmpty() || m[2].isNotEmpty())) {
            val hours = m[1].toIntOrNull() ?: 0
            val mins = m[2].toIntOrNull() ?: 0
            return hours * 60 + mins
        }
        // Try HH:mm format
        val hm = duration.trim().split(":")
        if (hm.size == 2) {
            val h = hm[0].toIntOrNull() ?: return null
            val min = hm[1].toIntOrNull() ?: return null
            return h * 60 + min
        }
        return null
    }

    private fun formatMinutes(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0m"
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun matchesDateFilter(entry: VehicleEntry): Boolean {
        val exitDate = entry.exitedAt?.take(10) ?: return false
        return when (currentFilter) {
            HistoryFilter.TODAY -> exitDate == dateFormat.format(Date())
            HistoryFilter.ALL -> true
            HistoryFilter.RANGE -> {
                val fromOk = filterFrom == null || exitDate >= filterFrom!!
                val toOk = filterTo == null || exitDate <= filterTo!!
                fromOk && toOk
            }
        }
    }

    private fun matchesDateFilterActive(entry: VehicleEntry): Boolean {
        val entryDate = entry.createdAt.take(10)
        return when (currentFilter) {
            HistoryFilter.TODAY -> entryDate == dateFormat.format(Date())
            HistoryFilter.ALL -> true
            HistoryFilter.RANGE -> {
                val fromOk = filterFrom == null || entryDate >= filterFrom!!
                val toOk = filterTo == null || entryDate <= filterTo!!
                fromOk && toOk
            }
        }
    }

    private fun parseDateToMillis(dateStr: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.parse(dateStr)?.time
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.parse(dateStr)?.time
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun matchesSearch(entry: VehicleEntry): Boolean {
        if (searchQuery.isBlank()) return true
        return entry.plate.contains(searchQuery, ignoreCase = true)
    }

    private fun comparator(): Comparator<HistoryEntry> {
        val cmp: Comparator<HistoryEntry> = when (sortField) {
            SortField.DATE -> compareBy<HistoryEntry> {
                when (it.activityType) {
                    ActivityType.INBOUND, ActivityType.OCCUPIED -> it.entry.createdAt
                    ActivityType.OUTBOUND -> it.entry.exitedAt ?: it.entry.createdAt
                }
            }
            SortField.FEE -> compareBy<HistoryEntry> { it.fee }
            SortField.DURATION -> compareBy<HistoryEntry> { it.durationLabel }
        }
        return if (sortOrder == SortOrder.DESC) cmp.reversed() else cmp
    }
}

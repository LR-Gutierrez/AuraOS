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
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class HistoryEntry(
    val entry: VehicleEntry,
    val durationLabel: String,
    val fee: Double
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
    val avgDurationLabel: String = "0m"
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

    private var allEntries: List<VehicleEntry> = emptyList()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val pageSize = 20

    companion object {
        const val PAGE_SIZE = 20
    }

    fun loadHistory(branchId: String) {
        _uiState.value = HistoryUiState.Loading
        viewModelScope.launch {
            val result = repository.getHistoryEntries(branchId)
            val state = when (result) {
                is Result.Success -> {
                    allEntries = result.data.entries
                    applyFilters()
                }
                is Result.Error -> HistoryUiState.Error(
                    result.exception.message ?: "Error al cargar historial"
                )
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

        val filtered = allEntries
            .filter { matchesDateFilter(it) }
            .filter { matchesSearch(it) }
            .sortedWith(comparator())
            .map { entry ->
                HistoryEntry(
                    entry = entry,
                    durationLabel = entry.duration ?: "",
                    fee = entry.fee ?: 0.0
                )
            }

        val totalFiltered = filtered.size
        val actualShown = minOf(newShown, totalFiltered)

        _uiState.value = state.copy(
            entries = filtered.take(actualShown),
            shownCount = actualShown,
            hasMore = actualShown < totalFiltered
        )
    }

    private fun applyFilters(): HistoryUiState.Success {
        val filtered = allEntries
            .filter { matchesDateFilter(it) }
            .filter { matchesSearch(it) }
            .sortedWith(comparator())

        val totalFiltered = filtered.size
        val shownCount = minOf(pageSize, totalFiltered)

        val historyEntries = filtered.map { entry ->
            HistoryEntry(
                entry = entry,
                durationLabel = entry.duration ?: "",
                fee = entry.fee ?: 0.0
            )
        }

        val avgMinutes = filtered
            .mapNotNull { parseDurationToMinutes(it.duration) }
            .let { minutes -> if (minutes.isEmpty()) 0.0 else minutes.average() }

        val summary = FilterSummary(
            count = totalFiltered,
            revenue = filtered.sumOf { it.fee ?: 0.0 },
            avgDurationLabel = formatMinutes(avgMinutes.roundToInt())
        )

        return HistoryUiState.Success(
            entries = historyEntries.take(shownCount),
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

    private fun matchesSearch(entry: VehicleEntry): Boolean {
        if (searchQuery.isBlank()) return true
        return entry.plate.contains(searchQuery, ignoreCase = true)
    }

    private fun comparator(): Comparator<VehicleEntry> {
        val cmp: Comparator<VehicleEntry> = when (sortField) {
            SortField.DATE -> compareBy<VehicleEntry> { it.exitedAt ?: "" }
            SortField.FEE -> compareBy<VehicleEntry> { it.fee ?: 0.0 }
            SortField.DURATION -> compareBy<VehicleEntry> { it.duration ?: "" }
        }
        return if (sortOrder == SortOrder.DESC) cmp.reversed() else cmp
    }
}

package com.beta.auraos.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SyncEventBus
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.model.VipArrival
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(
        val data: DashboardResponse,
        val isFromCache: Boolean = false
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

/** Occupancy of one branch, for the "Todas las sedes" list. */
data class BranchOccupancy(val branch: Branch, val occupied: Int, val capacity: Int)

/** Everything the Inicio screen shows besides the server dashboard figures. */
data class HomeSnapshot(
    val branches: List<BranchOccupancy>,
    val onSite: List<VehicleEntry>,
    val memberNames: Map<String, String>,
    val entriesToday: Int,
    val exitsToday: Int,
)

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val membershipRepository: MembershipRepository,
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository,
    private val database: AppDatabase,
) : ViewModel() {

    private val _uiState = MutableLiveData<DashboardUiState>(DashboardUiState.Loading)
    val uiState: LiveData<DashboardUiState> = _uiState

    private val _branches = MutableLiveData<List<Branch>?>()
    /** null until loaded; empty when the account has no branches yet. */
    val branches: LiveData<List<Branch>?> = _branches

    private val _home = MutableLiveData<HomeSnapshot>()
    val home: LiveData<HomeSnapshot> = _home

    private var lastBranchId: String? = null

    fun loadBranches() {
        viewModelScope.launch {
            // Local-first: show cached branches at once, then refresh from the server.
            val cached = withContext(Dispatchers.IO) { branchRepository.getBranchesLocal() }
            if (cached.isNotEmpty()) _branches.value = cached
            val result = withContext(Dispatchers.IO) { branchRepository.getBranches() }
            val fresh = (result as? Result.Success)?.data
            if (fresh != null && fresh != cached) _branches.value = fresh
            else if (fresh == null && cached.isEmpty()) _branches.value = emptyList()
        }
    }

    init {
        viewModelScope.launch {
            SyncEventBus.events.collect {
                val bid = lastBranchId
                if (bid != null && _uiState.value !is DashboardUiState.Loading) {
                    loadDashboard(bid)
                }
            }
        }
    }

    fun loadDashboard(branchId: String) {
        lastBranchId = branchId
        _uiState.value = DashboardUiState.Loading
        viewModelScope.launch {
            val dashboardResult = repository.getDashboard(branchId)
            val membershipsResult = withContext(Dispatchers.IO) {
                membershipRepository.getMemberships(branchId = branchId)
            }
            val entriesResult = withContext(Dispatchers.IO) {
                vehicleEntryRepository.getActiveEntries(branchId)
            }

            val membershipNameMap = when (membershipsResult) {
                is Result.Success -> membershipsResult.data.associate { it.id to it.memberName }
                is Result.Error -> emptyMap<String, String>()
            }

            val nfcMemberNames = when (entriesResult) {
                is Result.Success -> entriesResult.data
                    .filter { it.plate == "NFC" && it.membershipId != null }
                    .sortedBy { it.createdAt }
                    .mapNotNull { membershipNameMap[it.membershipId] }
                is Result.Error -> emptyList()
            }

            val isFromCache = repository.lastResultFromCache

            val activeEntries = (entriesResult as? Result.Success)?.data.orEmpty()
            _home.value = buildHomeSnapshot(branchId, activeEntries, membershipNameMap)

            _uiState.value = when (dashboardResult) {
                is Result.Success -> {
                    val data = dashboardResult.data
                    val resolvedArrivals = if (isFromCache) {
                        data.vipArrivals
                    } else {
                        data.vipArrivals.mapIndexed { index, arrival ->
                            if (arrival.guestName == "NFC") {
                                val resolvedName = nfcMemberNames.getOrNull(index) ?: arrival.guestName
                                arrival.copy(guestName = resolvedName)
                            } else {
                                arrival
                            }
                        }
                    }
                    DashboardUiState.Success(
                        data = data.copy(vipArrivals = resolvedArrivals),
                        isFromCache = isFromCache
                    )
                }
                is Result.Error -> DashboardUiState.Error(
                    dashboardResult.exception.message ?: "Error desconocido"
                )
            }
        }
    }

    private suspend fun buildHomeSnapshot(
        branchId: String,
        activeEntries: List<VehicleEntry>,
        memberNames: Map<String, String>,
    ): HomeSnapshot = withContext(Dispatchers.IO) {
        val dao = database.vehicleEntryDao()
        val (from, to) = ParkingFormat.todayUtcRange()
        val branchRows = _branches.value.orEmpty().map { branch ->
            val occupied = if (branch.id == branchId) {
                activeEntries.size
            } else {
                dao.getActiveByBranch(branch.id).size
            }
            BranchOccupancy(
                branch = branch,
                occupied = occupied,
                capacity = branch.lightVehicleCapacity + branch.heavyVehicleCapacity + branch.motorcycleCapacity,
            )
        }
        HomeSnapshot(
            branches = branchRows,
            onSite = activeEntries.sortedByDescending { it.createdAt },
            memberNames = memberNames,
            entriesToday = dao.getEntriesInRange(branchId, from, to).size,
            exitsToday = dao.getExitsInRange(branchId, from, to).size,
        )
    }
}

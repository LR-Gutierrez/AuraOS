package com.beta.auraos.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.SyncEventBus
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

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val membershipRepository: MembershipRepository,
    private val vehicleEntryRepository: VehicleEntryRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<DashboardUiState>(DashboardUiState.Loading)
    val uiState: LiveData<DashboardUiState> = _uiState

    private var lastBranchId: String? = null

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
}

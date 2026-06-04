package com.beta.auraos.ui.exit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class ExitUiState {
    data object Loading : ExitUiState()
    data class Success(
        val entries: List<VehicleEntry>,
        val membershipNameMap: Map<String, String> = emptyMap(),
        val exitingId: String? = null
    ) : ExitUiState()
    data class Error(val message: String) : ExitUiState()
}

class ExitViewModel(
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository,
    private val membershipRepository: MembershipRepository,
    private val auditEngine: AuditEngine?
) : ViewModel() {

    private val _uiState = MutableLiveData<ExitUiState>(ExitUiState.Loading)
    val uiState: LiveData<ExitUiState> = _uiState

    private val _branch = MutableLiveData<Branch?>()
    val branch: LiveData<Branch?> = _branch

    fun loadEntries(branchId: String) {
        _uiState.value = ExitUiState.Loading
        viewModelScope.launch {
            val entriesResult = vehicleEntryRepository.getActiveEntries(branchId)
            val membershipsResult = withContext(Dispatchers.IO) {
                membershipRepository.getMemberships(branchId = branchId)
            }

            val membershipNameMap = when (membershipsResult) {
                is Result.Success -> membershipsResult.data.associate { it.id to it.memberName }
                is Result.Error -> emptyMap()
            }

            _uiState.value = when (entriesResult) {
                is Result.Success -> ExitUiState.Success(
                    entries = entriesResult.data,
                    membershipNameMap = membershipNameMap
                )
                is Result.Error -> ExitUiState.Error(
                    entriesResult.exception.message ?: "Error al cargar ingresos"
                )
            }
        }
        loadBranch(branchId)
    }

    private fun loadBranch(branchId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                branchRepository.getBranch(branchId)
            }
            if (result is Result.Success) {
                _branch.value = result.data
            }
        }
    }

    fun registerExit(entryId: String) {
        val current = _uiState.value
        if (current is ExitUiState.Success) {
            _uiState.value = current.copy(exitingId = entryId)
        }

        viewModelScope.launch {
            val result = vehicleEntryRepository.registerExit(entryId)
            when (result) {
                is Result.Success -> {
                    val exited = result.data
                    val state = _uiState.value as? ExitUiState.Success
                    if (auditEngine != null && state != null) {
                        val entry = state.entries.find { it.id == entryId }
                        if (entry != null) {
                            auditEngine.recordVehicleExit(
                                entry = entry,
                                branch = _branch.value,
                                feePaid = exited.fee,
                                exitTime = nowUtcString()
                            )
                        }
                    }
                    val entries = state?.entries?.filter {
                        it.id != entryId
                    } ?: emptyList()
                    _uiState.value = ExitUiState.Success(entries)
                }
                is Result.Error -> {
                    val state = _uiState.value
                    if (state is ExitUiState.Success) {
                        _uiState.value = state.copy(exitingId = null)
                    }
                    _uiState.value = ExitUiState.Error(
                        result.exception.message ?: "Error al registrar salida"
                    )
                }
            }
        }
    }

    private fun nowUtcString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}

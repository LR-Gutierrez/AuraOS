package com.beta.auraos.ui.exit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ExitUiState {
    data object Loading : ExitUiState()
    data class Success(
        val entries: List<VehicleEntry>,
        val exitingId: String? = null
    ) : ExitUiState()
    data class Error(val message: String) : ExitUiState()
}

class ExitViewModel(
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<ExitUiState>(ExitUiState.Loading)
    val uiState: LiveData<ExitUiState> = _uiState

    private val _branch = MutableLiveData<Branch?>()
    val branch: LiveData<Branch?> = _branch

    fun loadEntries(branchId: String) {
        _uiState.value = ExitUiState.Loading
        viewModelScope.launch {
            val result = vehicleEntryRepository.getActiveEntries(branchId)
            _uiState.value = when (result) {
                is Result.Success -> ExitUiState.Success(result.data)
                is Result.Error -> ExitUiState.Error(
                    result.exception.message ?: "Error al cargar ingresos"
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
                    val entries = (_uiState.value as? ExitUiState.Success)?.entries?.filter {
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
}

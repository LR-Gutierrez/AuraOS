package com.beta.auraos.ui.exit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.model.VehicleEntry
import kotlinx.coroutines.launch

sealed class ExitUiState {
    data object Loading : ExitUiState()
    data class Success(
        val entries: List<VehicleEntry>,
        val exitingId: String? = null
    ) : ExitUiState()
    data class Error(val message: String) : ExitUiState()
}

class ExitViewModel(
    private val repository: VehicleEntryRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<ExitUiState>(ExitUiState.Loading)
    val uiState: LiveData<ExitUiState> = _uiState

    fun loadEntries(branchId: String) {
        _uiState.value = ExitUiState.Loading
        viewModelScope.launch {
            val result = repository.getActiveEntries(branchId)
            _uiState.value = when (result) {
                is Result.Success -> ExitUiState.Success(result.data)
                is Result.Error -> ExitUiState.Error(
                    result.exception.message ?: "Error al cargar ingresos"
                )
            }
        }
    }

    fun registerExit(entryId: String) {
        val current = _uiState.value
        if (current is ExitUiState.Success) {
            _uiState.value = current.copy(exitingId = entryId)
        }

        viewModelScope.launch {
            val result = repository.registerExit(entryId)
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

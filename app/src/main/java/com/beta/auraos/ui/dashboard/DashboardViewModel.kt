package com.beta.auraos.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.model.DashboardResponse
import kotlinx.coroutines.launch

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Success(val data: DashboardResponse) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel(
    private val repository: DashboardRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<DashboardUiState>(DashboardUiState.Loading)
    val uiState: LiveData<DashboardUiState> = _uiState

    fun loadDashboard(branchId: String) {
        _uiState.value = DashboardUiState.Loading
        viewModelScope.launch {
            val result = repository.getDashboard(branchId)
            _uiState.value = when (result) {
                is com.beta.auraos.data.Result.Success -> DashboardUiState.Success(result.data)
                is com.beta.auraos.data.Result.Error -> DashboardUiState.Error(
                    result.exception.message ?: "Error desconocido"
                )
            }
        }
    }
}

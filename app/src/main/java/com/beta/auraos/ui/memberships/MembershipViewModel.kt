package com.beta.auraos.ui.memberships

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.data.model.Membership
import kotlinx.coroutines.launch

sealed class MembershipListUiState {
    data object Loading : MembershipListUiState()
    data class Success(val memberships: List<Membership>) : MembershipListUiState()
    data class Error(val message: String) : MembershipListUiState()
}

sealed class MembershipFormUiState {
    data object Idle : MembershipFormUiState()
    data object Saving : MembershipFormUiState()
    data class Saved(val membership: Membership) : MembershipFormUiState()
    data class Error(val message: String) : MembershipFormUiState()
}

class MembershipViewModel(
    private val repository: MembershipRepository
) : ViewModel() {

    private val _listState = MutableLiveData<MembershipListUiState>(MembershipListUiState.Loading)
    val listState: LiveData<MembershipListUiState> = _listState

    private val _formState = MutableLiveData<MembershipFormUiState>(MembershipFormUiState.Idle)
    val formState: LiveData<MembershipFormUiState> = _formState

    fun loadMemberships(branchId: String?) {
        _listState.value = MembershipListUiState.Loading
        viewModelScope.launch {
            val result = repository.getMemberships(branchId)
            _listState.value = when (result) {
                is Result.Success -> MembershipListUiState.Success(result.data)
                is Result.Error -> MembershipListUiState.Error(
                    result.exception.message ?: "Error al cargar membresías"
                )
            }
        }
    }

    fun createMembership(input: CreateMembershipInput) {
        _formState.value = MembershipFormUiState.Saving
        viewModelScope.launch {
            val result = repository.createMembership(input)
            _formState.value = when (result) {
                is Result.Success -> MembershipFormUiState.Saved(result.data)
                is Result.Error -> MembershipFormUiState.Error(
                    result.exception.message ?: "Error al crear membresía"
                )
            }
        }
    }

    fun deleteMembership(id: String, branchId: String?) {
        viewModelScope.launch {
            val result = repository.deleteMembership(id)
            when (result) {
                is Result.Success -> loadMemberships(branchId)
                is Result.Error -> {
                    _listState.value = MembershipListUiState.Error(
                        result.exception.message ?: "Error al eliminar membresía"
                    )
                }
            }
        }
    }
}

package com.beta.auraos.ui.audit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.audit.AuditPeriodSummary
import com.beta.auraos.data.audit.ClosureBatch
import com.beta.auraos.data.audit.ClosureReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuditUiState {
    data object Loading : AuditUiState()
    data class Summary(
        val periodSummary: AuditPeriodSummary,
        val closureHistory: List<ClosureBatch>
    ) : AuditUiState()
    data class ClosureResult(val report: ClosureReport) : AuditUiState()
    data class Error(val message: String) : AuditUiState()
}

class AuditViewModel(
    private val auditEngine: AuditEngine,
    private val membershipRepository: MembershipRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableLiveData<AuditUiState>(AuditUiState.Loading)
    val uiState: LiveData<AuditUiState> = _uiState

    fun loadSummary(branchId: String) {
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                auditEngine.getPeriodSummary(branchId)
            }
            val history = withContext(Dispatchers.IO) {
                auditEngine.getClosureHistory(branchId)
            }
            _uiState.value = AuditUiState.Summary(
                periodSummary = summary,
                closureHistory = history
            )
        }
    }

    fun executeClosure(branchId: String) {
        viewModelScope.launch {
            _uiState.value = AuditUiState.Loading
            val membershipsResult = withContext(Dispatchers.IO) {
                membershipRepository.getMemberships(branchId = branchId)
            }
            val memberships = when (membershipsResult) {
                is Result.Success -> membershipsResult.data
                is Result.Error -> emptyList()
            }
            val report = withContext(Dispatchers.IO) {
                auditEngine.executeClosure(branchId, userId, memberships)
            }
            _uiState.value = AuditUiState.ClosureResult(report)
            loadSummary(branchId)
        }
    }
}

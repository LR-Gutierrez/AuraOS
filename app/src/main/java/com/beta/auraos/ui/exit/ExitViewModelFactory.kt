package com.beta.auraos.ui.exit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.audit.AuditEngine
import com.beta.auraos.data.local.ExitSyncManager

class ExitViewModelFactory(
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository,
    private val membershipRepository: MembershipRepository,
    private val auditEngine: AuditEngine?,
    private val exitSyncManager: ExitSyncManager?
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExitViewModel::class.java)) {
            return ExitViewModel(
                vehicleEntryRepository,
                branchRepository,
                membershipRepository,
                auditEngine,
                exitSyncManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

package com.beta.auraos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.room.AppDatabase

class DashboardViewModelFactory(
    private val repository: DashboardRepository,
    private val membershipRepository: MembershipRepository,
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository,
    private val database: AppDatabase,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(repository, membershipRepository, vehicleEntryRepository, branchRepository, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

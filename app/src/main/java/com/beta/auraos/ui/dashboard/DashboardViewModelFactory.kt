package com.beta.auraos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.DashboardRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.VehicleEntryRepository

class DashboardViewModelFactory(
    private val repository: DashboardRepository,
    private val membershipRepository: MembershipRepository,
    private val vehicleEntryRepository: VehicleEntryRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            return DashboardViewModel(repository, membershipRepository, vehicleEntryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

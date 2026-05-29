package com.beta.auraos.ui.exit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.VehicleEntryRepository

class ExitViewModelFactory(
    private val vehicleEntryRepository: VehicleEntryRepository,
    private val branchRepository: BranchRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExitViewModel::class.java)) {
            return ExitViewModel(vehicleEntryRepository, branchRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

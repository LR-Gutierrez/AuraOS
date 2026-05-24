package com.beta.auraos.ui.exit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.VehicleEntryRepository

class ExitViewModelFactory(
    private val repository: VehicleEntryRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExitViewModel::class.java)) {
            return ExitViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

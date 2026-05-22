package com.beta.auraos.ui.branches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi

class BranchViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BranchViewModel::class.java)) {
            val api = ApiClient.createApi<BranchApi>()
            val repository = BranchRepository(api)
            return BranchViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

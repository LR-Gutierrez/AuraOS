package com.beta.auraos.ui.memberships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.data.MembershipRepository

class MembershipViewModelFactory(
    private val repository: MembershipRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MembershipViewModel::class.java)) {
            return MembershipViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

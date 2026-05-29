package com.beta.auraos.ui.branches

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BranchViewModel(
    private val repository: BranchRepository,
    private val vehicleEntryRepository: VehicleEntryRepository = VehicleEntryRepository()
) : ViewModel() {

    private val _branches = MutableLiveData<List<Branch>>()
    val branches: LiveData<List<Branch>> = _branches

    private val _branchOccupancy = MutableLiveData<Map<String, Int>>(emptyMap())
    val branchOccupancy: LiveData<Map<String, Int>> = _branchOccupancy

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _operationResult = MutableLiveData<BranchOperationResult?>()
    val operationResult: LiveData<BranchOperationResult?> = _operationResult

    private val _selectedBranch = MutableLiveData<Branch?>()
    val selectedBranch: LiveData<Branch?> = _selectedBranch

    fun loadBranches() {
        if (_isLoading.value == true) return
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getBranches()
            }
            _isLoading.value = false
            when (result) {
                is Result.Success -> _branches.value = result.data
                is Result.Error -> _error.value = result.exception.localizedMessage ?: "Error al cargar sucursales"
            }
        }
    }

    fun loadOccupancyData(branches: List<Branch>) {
        if (branches.isEmpty()) return
        viewModelScope.launch {
            val deferred = branches.map { branch ->
                async(Dispatchers.IO) {
                    val result = vehicleEntryRepository.getAnalyticsSummary(branch.id)
                    val activeCount = when (result) {
                        is Result.Success -> result.data.activeCount
                        is Result.Error -> 0
                    }
                    branch.id to activeCount
                }
            }
            val occupancyMap = deferred.awaitAll().toMap()
            _branchOccupancy.value = occupancyMap
        }
    }

    fun loadBranch(id: String) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getBranch(id)
            }
            _isLoading.value = false
            when (result) {
                is Result.Success -> _selectedBranch.value = result.data
                is Result.Error -> _error.value = result.exception.localizedMessage ?: "Error al cargar sucursal"
            }
        }
    }

    fun createBranch(input: CreateBranchInput) {
        if (_isLoading.value == true) return
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.createBranch(input)
            }
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    _operationResult.value = BranchOperationResult(success = true, message = "Sucursal creada correctamente")
                    loadBranches()
                }
                is Result.Error -> {
                    _operationResult.value = BranchOperationResult(success = false, message = result.exception.localizedMessage ?: "Error al crear sucursal")
                }
            }
        }
    }

    fun updateBranch(id: String, input: UpdateBranchInput) {
        if (_isLoading.value == true) return
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateBranch(id, input)
            }
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    _operationResult.value = BranchOperationResult(success = true, message = "Sucursal actualizada correctamente")
                    loadBranches()
                }
                is Result.Error -> {
                    _operationResult.value = BranchOperationResult(success = false, message = result.exception.localizedMessage ?: "Error al actualizar sucursal")
                }
            }
        }
    }

    fun deleteBranch(id: String) {
        if (_isLoading.value == true) return
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.deleteBranch(id)
            }
            _isLoading.value = false
            when (result) {
                is Result.Success -> {
                    _operationResult.value = BranchOperationResult(success = true, message = "Sucursal eliminada correctamente")
                    loadBranches()
                }
                is Result.Error -> {
                    _operationResult.value = BranchOperationResult(success = false, message = result.exception.localizedMessage ?: "Error al eliminar sucursal")
                }
            }
        }
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    fun clearError() {
        _error.value = null
    }

    data class BranchOperationResult(
        val success: Boolean,
        val message: String
    )
}

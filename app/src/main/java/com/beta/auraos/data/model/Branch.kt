package com.beta.auraos.data.model

data class Branch(
    val id: String,
    val name: String,
    val address: String?,
    val motorcycleCapacity: Int,
    val lightVehicleCapacity: Int,
    val heavyVehicleCapacity: Int,
    val motorcycleRate: Double,
    val lightVehicleRate: Double,
    val heavyVehicleRate: Double,
    val currency: String,
    val createdAt: String,
    val updatedAt: String
)

data class CreateBranchInput(
    val name: String,
    val address: String?,
    val motorcycleCapacity: Int,
    val lightVehicleCapacity: Int,
    val heavyVehicleCapacity: Int,
    val motorcycleRate: Double,
    val lightVehicleRate: Double,
    val heavyVehicleRate: Double,
    val currency: String
)

data class UpdateBranchInput(
    val name: String? = null,
    val address: String? = null,
    val motorcycleCapacity: Int? = null,
    val lightVehicleCapacity: Int? = null,
    val heavyVehicleCapacity: Int? = null,
    val motorcycleRate: Double? = null,
    val lightVehicleRate: Double? = null,
    val heavyVehicleRate: Double? = null,
    val currency: String? = null
)

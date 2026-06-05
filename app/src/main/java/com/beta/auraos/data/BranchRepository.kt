package com.beta.auraos.data

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.BranchEntity
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import com.beta.auraos.data.remote.BranchApi
import java.io.IOException

class BranchRepository(
    private val api: BranchApi,
    private val database: AppDatabase? = null
) {

    suspend fun getBranches(): Result<List<Branch>> {
        return try {
            val response = api.getBranches()
            if (response.isSuccessful) {
                val branches = response.body() ?: emptyList()
                database?.branchDao()?.let { dao ->
                    dao.deleteAll()
                    dao.insertAll(branches.map { it.toEntity() })
                }
                Result.Success(branches)
            } else {
                Result.Error(IOException("Error al cargar sucursales: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            val cached = database?.branchDao()?.getAll()
            if (cached != null && cached.isNotEmpty()) {
                Result.Success(cached.map { it.toModel() })
            } else {
                Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
            }
        }
    }

    private fun Branch.toEntity() = BranchEntity(
        id = id,
        name = name,
        address = address,
        motorcycleCapacity = motorcycleCapacity,
        lightVehicleCapacity = lightVehicleCapacity,
        heavyVehicleCapacity = heavyVehicleCapacity,
        motorcycleRate = motorcycleRate,
        lightVehicleRate = lightVehicleRate,
        heavyVehicleRate = heavyVehicleRate,
        motorcycleOvernightRate = motorcycleOvernightRate,
        lightVehicleOvernightRate = lightVehicleOvernightRate,
        heavyVehicleOvernightRate = heavyVehicleOvernightRate,
        openTimeWeekday = openTimeWeekday,
        closeTimeWeekday = closeTimeWeekday,
        openTimeWeekend = openTimeWeekend,
        closeTimeWeekend = closeTimeWeekend,
        currency = currency,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun BranchEntity.toModel() = Branch(
        id = id,
        name = name,
        address = address,
        motorcycleCapacity = motorcycleCapacity,
        lightVehicleCapacity = lightVehicleCapacity,
        heavyVehicleCapacity = heavyVehicleCapacity,
        motorcycleRate = motorcycleRate,
        lightVehicleRate = lightVehicleRate,
        heavyVehicleRate = heavyVehicleRate,
        motorcycleOvernightRate = motorcycleOvernightRate,
        lightVehicleOvernightRate = lightVehicleOvernightRate,
        heavyVehicleOvernightRate = heavyVehicleOvernightRate,
        openTimeWeekday = openTimeWeekday,
        closeTimeWeekday = closeTimeWeekday,
        openTimeWeekend = openTimeWeekend,
        closeTimeWeekend = closeTimeWeekend,
        currency = currency,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    suspend fun getBranch(id: String): Result<Branch> {
        return try {
            val response = api.getBranch(id)
            if (response.isSuccessful) {
                val branch = response.body()
                if (branch != null) {
                    database?.branchDao()?.insert(branch.toEntity())
                    Result.Success(branch)
                } else {
                    Result.Error(IOException("Sucursal no encontrada"))
                }
            } else {
                Result.Error(IOException("Error al cargar sucursal: ${response.code()}"))
            }
        } catch (e: Exception) {
            val cached = database?.branchDao()?.getById(id)
            if (cached != null) {
                Result.Success(cached.toModel())
            } else {
                Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
            }
        }
    }

    suspend fun createBranch(input: CreateBranchInput): Result<Branch> {
        return try {
            val response = api.createBranch(input)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(IOException("Error al crear sucursal"))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(IOException("Error al crear sucursal: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

    suspend fun updateBranch(id: String, input: UpdateBranchInput): Result<Branch> {
        return try {
            val map = mutableMapOf<String, Any>()
            input.name?.let { map["name"] = it }
            input.address?.let { map["address"] = it }
            input.motorcycleCapacity?.let { map["motorcycleCapacity"] = it }
            input.lightVehicleCapacity?.let { map["lightVehicleCapacity"] = it }
            input.heavyVehicleCapacity?.let { map["heavyVehicleCapacity"] = it }
            input.motorcycleRate?.let { map["motorcycleRate"] = it }
            input.lightVehicleRate?.let { map["lightVehicleRate"] = it }
            input.heavyVehicleRate?.let { map["heavyVehicleRate"] = it }
            input.currency?.let { map["currency"] = it }
            input.motorcycleOvernightRate?.let { map["motorcycleOvernightRate"] = it }
            input.lightVehicleOvernightRate?.let { map["lightVehicleOvernightRate"] = it }
            input.heavyVehicleOvernightRate?.let { map["heavyVehicleOvernightRate"] = it }
            input.openTimeWeekday?.let { map["openTimeWeekday"] = it }
            input.closeTimeWeekday?.let { map["closeTimeWeekday"] = it }
            input.openTimeWeekend?.let { map["openTimeWeekend"] = it }
            input.closeTimeWeekend?.let { map["closeTimeWeekend"] = it }

            val response = api.updateBranch(id, map)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(IOException("Error al actualizar sucursal"))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(IOException("Error al actualizar sucursal: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

    suspend fun deleteBranch(id: String): Result<Unit> {
        return try {
            val response = api.deleteBranch(id)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                val message = if (response.code() == 409 || errorBody.contains("FK", ignoreCase = true) || errorBody.contains("ingresos", ignoreCase = true)) {
                    "No se puede eliminar esta sucursal porque tiene ingresos registrados."
                } else {
                    "Error al eliminar sucursal: $errorBody"
                }
                Result.Error(IOException(message))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }
}

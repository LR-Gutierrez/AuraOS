package com.beta.auraos.data

import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import com.beta.auraos.data.remote.BranchApi
import java.io.IOException

class BranchRepository(private val api: BranchApi) {

    suspend fun getBranches(): Result<List<Branch>> {
        return try {
            val response = api.getBranches()
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error(IOException("Error al cargar sucursales: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

    suspend fun getBranch(id: String): Result<Branch> {
        return try {
            val response = api.getBranch(id)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(IOException("Sucursal no encontrada"))
            } else {
                Result.Error(IOException("Error al cargar sucursal: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
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

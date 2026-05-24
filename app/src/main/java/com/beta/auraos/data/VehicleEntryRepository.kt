package com.beta.auraos.data

import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi

class VehicleEntryRepository {

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun getActiveEntries(branchId: String): Result<List<VehicleEntry>> {
        return try {
            val response = api.getVehicleEntries(branchId = branchId, activeOnly = true)
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error(Exception("Error al cargar ingresos: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getEntry(id: String): Result<VehicleEntry> {
        return try {
            val response = api.getVehicleEntry(id)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Ingreso no encontrado"))
            } else {
                Result.Error(Exception("Error al cargar ingreso: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun registerExit(id: String): Result<VehicleEntry> {
        return try {
            val response = api.registerExit(id)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Error al registrar salida"))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al registrar salida: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }
}

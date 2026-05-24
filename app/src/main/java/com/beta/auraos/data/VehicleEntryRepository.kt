package com.beta.auraos.data

import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.data.remote.TodaySummary

data class HistoryResult(
    val entries: List<VehicleEntry>,
    val todaySummary: TodaySummary
)

class VehicleEntryRepository {

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun getActiveEntries(branchId: String): Result<List<VehicleEntry>> {
        return try {
            val response = api.getVehicleEntries(branchId = branchId, exited = false)
            if (response.isSuccessful) {
                Result.Success(response.body()?.data ?: emptyList())
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
                response.body()?.data?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Ingreso no encontrado"))
            } else {
                Result.Error(Exception("Error al cargar ingreso: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getHistoryEntries(
        branchId: String,
        from: String? = null,
        to: String? = null
    ): Result<HistoryResult> {
        return try {
            val response = api.getVehicleEntries(
                branchId = branchId,
                exited = true,
                from = from,
                to = to,
            )
            if (response.isSuccessful) {
                val body = response.body()
                Result.Success(
                    HistoryResult(
                        entries = body?.data ?: emptyList(),
                        todaySummary = body?.meta?.todaySummary
                            ?: TodaySummary(0, 0.0)
                    )
                )
            } else {
                Result.Error(Exception("Error al cargar historial: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun registerExit(id: String): Result<VehicleEntry> {
        return try {
            val response = api.registerExit(id)
            if (response.isSuccessful) {
                response.body()?.data?.let { Result.Success(it) }
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

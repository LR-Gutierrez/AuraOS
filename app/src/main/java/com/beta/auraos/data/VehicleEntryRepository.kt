package com.beta.auraos.data

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.VehicleEntryEntity
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.AnalyticsSummaryResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.data.remote.TodaySummary
import java.io.IOException

data class HistoryResult(
    val entries: List<VehicleEntry>,
    val todaySummary: TodaySummary
)

class VehicleEntryRepository(
    private val database: AppDatabase? = null
) {

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun getActiveEntries(branchId: String): Result<List<VehicleEntry>> {
        return try {
            val response = api.getVehicleEntries(branchId = branchId, exited = false)
            if (response.isSuccessful) {
                val list = response.body()?.data ?: emptyList()
                database?.vehicleEntryDao()?.let { dao ->
                    dao.deleteActiveByBranch(branchId)
                    dao.insertAll(list.map { it.toEntity() })
                }
                Result.Success(list)
            } else {
                Result.Error(Exception("Error al cargar ingresos: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.vehicleEntryDao()?.getActiveByBranch(branchId)
                if (cached != null && cached.isNotEmpty()) {
                    return Result.Success(cached.map { it.toModel() })
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getEntry(id: String): Result<VehicleEntry> {
        return try {
            val response = api.getVehicleEntry(id)
            if (response.isSuccessful) {
                val entry = response.body()?.data
                if (entry != null) {
                    database?.vehicleEntryDao()?.insert(entry.toEntity())
                    Result.Success(entry)
                } else {
                    Result.Error(Exception("Ingreso no encontrado"))
                }
            } else {
                Result.Error(Exception("Error al cargar ingreso: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.vehicleEntryDao()?.getById(id)
                if (cached != null) {
                    return Result.Success(cached.toModel())
                }
            }
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
                val list = body?.data ?: emptyList()
                database?.vehicleEntryDao()?.let { dao ->
                    list.forEach { dao.insert(it.toEntity()) }
                }
                Result.Success(
                    HistoryResult(
                        entries = list,
                        todaySummary = body?.meta?.todaySummary
                            ?: TodaySummary(0, 0.0)
                    )
                )
            } else {
                Result.Error(Exception("Error al cargar historial: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.vehicleEntryDao()?.getHistoryByBranch(branchId)
                if (cached != null && cached.isNotEmpty()) {
                    return Result.Success(
                        HistoryResult(
                            entries = cached.map { it.toModel() },
                            todaySummary = TodaySummary(0, 0.0)
                        )
                    )
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun registerExit(id: String): Result<VehicleEntry> {
        return try {
            val response = api.registerExit(id)
            if (response.isSuccessful) {
                val entry = response.body()?.data
                if (entry != null) {
                    database?.vehicleEntryDao()?.let { dao ->
                        dao.insert(entry.toEntity())
                    }
                    Result.Success(entry)
                } else {
                    Result.Error(Exception("Error al registrar salida"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al registrar salida: $errorBody"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.vehicleEntryDao()?.getById(id)
                if (cached != null) {
                    val model = cached.toModel()
                    if (model.exitedAt != null) {
                        return Result.Success(model)
                    }
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun updateCachedEntry(entry: VehicleEntry) {
        database?.vehicleEntryDao()?.insert(entry.toEntity())
    }

    suspend fun deleteCachedEntry(id: String) {
        database?.vehicleEntryDao()?.deleteById(id)
    }

    suspend fun getAnalyticsSummary(
        branchId: String,
        date: String? = null,
        inboundThresholdMinutes: Int? = null
    ): Result<AnalyticsSummaryResponse> {
        return try {
            val response = api.getAnalyticsSummary(
                branchId = branchId,
                date = date,
                inboundThresholdMinutes = inboundThresholdMinutes
            )
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Respuesta vacía del servidor"))
            } else {
                Result.Error(Exception("Error al cargar resumen: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    private fun VehicleEntry.toEntity() = VehicleEntryEntity(
        id = id,
        plate = plate,
        vehicleType = vehicleType,
        isVip = isVip,
        membershipId = membershipId,
        branchId = branchId,
        platePhotoUrl = platePhotoUrl,
        frontPhotoUrl = frontPhotoUrl,
        rearPhotoUrl = rearPhotoUrl,
        leftPhotoUrl = leftPhotoUrl,
        rightPhotoUrl = rightPhotoUrl,
        createdAt = createdAt,
        exitedAt = exitedAt,
        duration = duration,
        fee = fee
    )

    private fun VehicleEntryEntity.toModel() = VehicleEntry(
        id = id,
        plate = plate,
        vehicleType = vehicleType,
        isVip = isVip,
        membershipId = membershipId,
        branchId = branchId,
        platePhotoUrl = platePhotoUrl,
        frontPhotoUrl = frontPhotoUrl,
        rearPhotoUrl = rearPhotoUrl,
        leftPhotoUrl = leftPhotoUrl,
        rightPhotoUrl = rightPhotoUrl,
        createdAt = createdAt,
        exitedAt = exitedAt,
        duration = duration,
        fee = fee,
        branch = null
    )

    private fun isNetworkError(exception: Exception): Boolean {
        return exception is IOException ||
                exception.message?.contains("Unable to resolve host") == true ||
                exception.message?.contains("Failed to connect") == true ||
                exception.message?.contains("timeout") == true
    }
}

package com.beta.auraos.data

import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ChangeJournalProvider
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.VehicleEntryEntity
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.AnalyticsSummaryResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.data.remote.TodaySummary
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HistoryResult(
    val entries: List<VehicleEntry>,
    val todaySummary: TodaySummary
)

class VehicleEntryRepository(
    private val database: AppDatabase? = null,
    private val changeJournal: ChangeJournal? = null
) {
    private val journal: ChangeJournal? get() = changeJournal ?: ChangeJournalProvider.get()

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun getActiveEntries(branchId: String): Result<List<VehicleEntry>> {
        return try {
            val response = api.getVehicleEntries(branchId = branchId, exited = false)
            if (response.isSuccessful) {
                val list = response.body()?.data ?: emptyList()
                database?.vehicleEntryDao()?.insertAll(list.map { it.toEntity() })

                val serverIds = list.map { it.id }.toSet()
                val localPending = getLocalPendingEntries(branchId).filter { it.id !in serverIds }
                val merged = if (localPending.isEmpty()) list else list + localPending

                Result.Success(merged)
            } else {
                Result.Error(Exception("Error al cargar ingresos: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.vehicleEntryDao()?.getActiveByBranch(branchId)
                if (cached != null && cached.isNotEmpty()) {
                    return Result.Success(cached.map { it.toModel() })
                }
                return Result.Success(emptyList())
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    private suspend fun getLocalPendingEntries(branchId: String): List<VehicleEntry> {
        val journal = journal ?: return emptyList()
        val unsynced = journal.getUnsyncedChanges()
        val result = mutableListOf<VehicleEntry>()
        for (change in unsynced) {
            if (change.entityType == "vehicle_entry" && change.operation == ChangeJournal.OP_CREATE) {
                val entry = database?.vehicleEntryDao()?.getById(change.entityId)
                if (entry != null && entry.branchId == branchId && entry.exitedAt == null) {
                    result.add(entry.toModel())
                }
            }
        }
        return result
    }

    suspend fun getActiveEntriesLocal(branchId: String): List<VehicleEntry> {
        return database?.vehicleEntryDao()?.getActiveByBranch(branchId)
            ?.map { it.toModel() } ?: emptyList()
    }

    suspend fun getHistoryEntriesLocal(branchId: String): List<VehicleEntry> {
        return database?.vehicleEntryDao()?.getHistoryByBranch(branchId)
            ?.map { it.toModel() } ?: emptyList()
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

    suspend fun getActiveEntryByMembership(membershipId: String): VehicleEntry? {
        return database?.vehicleEntryDao()?.getActiveByMembershipId(membershipId)?.toModel()
    }

    /** Entries of [membershipId] recorded on this device since [fromDate] (UTC ISO). */
    suspend fun countMembershipEntriesSince(membershipId: String, fromDate: String): Int =
        database?.vehicleEntryDao()?.countByMembershipSince(membershipId, fromDate) ?: 0

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
        val cached = database?.vehicleEntryDao()?.getById(id)?.toModel()

        try {
            val response = api.registerExit(id)
            if (response.isSuccessful) {
                val entry = response.body()?.data
                if (entry != null) {
                    database?.vehicleEntryDao()?.insert(entry.toEntity())
                    journal?.recordUpdate("vehicle_entry", id, entry.toJournalMap(), mapOf(
                        "exitedAt" to entry.exitedAt,
                        "duration" to entry.duration,
                        "fee" to entry.fee
                    ))
                    return Result.Success(entry)
                }
            }
        } catch (_: Exception) {
        }

        val model = cached ?: database?.vehicleEntryDao()?.getById(id)?.toModel()
        return if (model != null && model.exitedAt != null) {
            journal?.recordUpdate("vehicle_entry", id, model.toJournalMap(), mapOf(
                "exitedAt" to model.exitedAt,
                "duration" to model.duration,
                "fee" to model.fee
            ))
            Result.Success(model)
        } else {
            Result.Error(Exception("Error al registrar salida"))
        }
    }

    suspend fun updateCachedEntry(entry: VehicleEntry) {
        val existing = database?.vehicleEntryDao()?.getById(entry.id)
        database?.vehicleEntryDao()?.insert(entry.toEntity())
        if (existing == null) {
            journal?.recordCreate("vehicle_entry", entry.id, entry.toJournalMap())
        }
    }

    suspend fun deleteCachedEntry(id: String) {
        database?.vehicleEntryDao()?.deleteById(id)
    }

    suspend fun replaceLocalWithServerEntry(localId: String, serverEntry: VehicleEntry) {
        database?.vehicleEntryDao()?.deleteById(localId)
        database?.vehicleEntryDao()?.insert(serverEntry.toEntity())
        journal?.markSyncedForEntity("vehicle_entry", localId)
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
                computeAnalyticsLocal(branchId, date, inboundThresholdMinutes)
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                computeAnalyticsLocal(branchId, date, inboundThresholdMinutes)
            } else {
                Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
            }
        }
    }

    private suspend fun computeAnalyticsLocal(
        branchId: String,
        date: String?,
        inboundThresholdMinutes: Int? = null
    ): Result<AnalyticsSummaryResponse> {
        val db = database ?: return Result.Error(Exception("Sin datos locales"))
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val targetDate = date ?: utcFormat.format(Date())
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        if (date != null) {
            cal.time = utcFormat.parse(date) ?: Date()
        }
        val fromDate = "${targetDate}T00:00:00"
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val nextDay = utcFormat.format(cal.time)
        val toDate = "${nextDay}T00:00:00"

        val active = db.vehicleEntryDao().getActiveByBranch(branchId)
        val todayExits = db.vehicleEntryDao().getExitsInRange(branchId, fromDate, toDate)
        val todayEntries = db.vehicleEntryDao().getEntriesInRange(branchId, fromDate, toDate)
        val allHistory = db.vehicleEntryDao().getHistoryByBranch(branchId)
        val branch = db.branchDao().getById(branchId)

        val outboundCount = todayExits.size
        val inboundCount = todayEntries.size
        val activeCount = active.size
        val totalCapacity = branch?.let {
            it.lightVehicleCapacity + it.heavyVehicleCapacity + it.motorcycleCapacity
        } ?: maxOf(activeCount, 1)
        val totalRevenue = todayExits.sumOf { it.fee ?: 0.0 }
        val avgMinutes = allHistory
            .filter { it.duration != null }
            .mapNotNull { parseDurationMinutes(it.duration!!) }
            .let { mins -> if (mins.isEmpty()) 0 else mins.average().toInt() }

        return Result.Success(
            AnalyticsSummaryResponse(
                branchId = branchId,
                date = targetDate,
                totalCapacity = totalCapacity,
                inboundCount = inboundCount,
                activeCount = activeCount,
                outboundCount = outboundCount,
                totalRevenue = totalRevenue,
                avgDurationMinutes = avgMinutes.toDouble(),
                inboundTrendPercent = 0,
                activeTrendPercent = 0,
                outboundTrendPercent = 0,
                revenueTrendPercent = 0,
                peakHourRevenue = 0.0,
                peakHourLabel = "",
                inboundThresholdMinutes = inboundThresholdMinutes ?: 0
            )
        )
    }

    private fun parseDurationMinutes(duration: String): Int? {
        val hRegex = Regex("""(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?""")
        val m = hRegex.matchEntire(duration.trim())?.groupValues
        if (m != null && (m[1].isNotEmpty() || m[2].isNotEmpty())) {
            val hours = m[1].toIntOrNull() ?: 0
            val mins = m[2].toIntOrNull() ?: 0
            return hours * 60 + mins
        }
        val hm = duration.trim().split(":")
        if (hm.size == 2) {
            val h = hm[0].toIntOrNull() ?: return null
            val min = hm[1].toIntOrNull() ?: return null
            return h * 60 + min
        }
        return null
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
        fee = fee,
        lastModifiedAt = System.currentTimeMillis()
    )

    private fun VehicleEntry.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "plate" to plate,
        "vehicleType" to vehicleType,
        "isVip" to isVip,
        "membershipId" to membershipId,
        "branchId" to branchId,
        "platePhotoUrl" to platePhotoUrl,
        "frontPhotoUrl" to frontPhotoUrl,
        "rearPhotoUrl" to rearPhotoUrl,
        "leftPhotoUrl" to leftPhotoUrl,
        "rightPhotoUrl" to rightPhotoUrl,
        "createdAt" to createdAt,
        "exitedAt" to exitedAt,
        "duration" to duration,
        "fee" to fee
    )

    private fun VehicleEntryEntity.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "plate" to plate,
        "vehicleType" to vehicleType,
        "isVip" to isVip,
        "membershipId" to membershipId,
        "branchId" to branchId,
        "platePhotoUrl" to platePhotoUrl,
        "frontPhotoUrl" to frontPhotoUrl,
        "rearPhotoUrl" to rearPhotoUrl,
        "leftPhotoUrl" to leftPhotoUrl,
        "rightPhotoUrl" to rightPhotoUrl,
        "createdAt" to createdAt,
        "exitedAt" to exitedAt,
        "duration" to duration,
        "fee" to fee
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

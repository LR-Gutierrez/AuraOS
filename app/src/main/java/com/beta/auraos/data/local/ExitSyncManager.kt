package com.beta.auraos.data.local

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.PendingExitEntity
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import java.io.IOException

class ExitSyncManager(
    private val database: AppDatabase
) {

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun enqueue(
        entryId: String,
        exitTime: String,
        duration: String?,
        fee: Double,
        branchId: String
    ) {
        database.pendingExitDao().insert(
            PendingExitEntity(
                entryId = entryId,
                exitTime = exitTime,
                duration = duration,
                fee = fee,
                branchId = branchId
            )
        )
    }

    suspend fun syncPending(): SyncResult {
        val pending = database.pendingExitDao().getAll()
        if (pending.isEmpty()) return SyncResult.Noop

        var synced = 0
        var failed = 0

        for (entry in pending) {
            try {
                val response = api.registerExit(entry.entryId)
                if (response.isSuccessful) {
                    database.pendingExitDao().deleteById(entry.id)
                    synced++
                } else {
                    database.pendingExitDao().deleteById(entry.id)
                    failed++
                }
            } catch (e: Exception) {
                val isNetworkError = e is IOException ||
                        e.message?.contains("Unable to resolve host") == true ||
                        e.message?.contains("Failed to connect") == true ||
                        e.message?.contains("timeout") == true
                if (isNetworkError) {
                    return SyncResult.Partial(synced, failed + (pending.size - synced - failed))
                }
                database.pendingExitDao().deleteById(entry.id)
                failed++
            }
        }

        return if (failed > 0) SyncResult.Partial(synced, failed) else SyncResult.Success(synced)
    }

    suspend fun getPendingCount(): Int {
        return database.pendingExitDao().count()
    }

    sealed class SyncResult {
        data object Noop : SyncResult()
        data class Success(val count: Int) : SyncResult()
        data class Partial(val synced: Int, val failed: Int) : SyncResult()
    }
}

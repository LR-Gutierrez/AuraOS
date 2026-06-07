package com.beta.auraos.data.local

import android.util.Log
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
        val maxRetries = 5

        for (entry in pending) {
            if (entry.retryCount >= maxRetries) {
                Log.w(TAG, "Dropping exit for ${entry.entryId}: max retries reached")
                database.pendingExitDao().deleteById(entry.id)
                failed++
                continue
            }

            if (hasPendingCreate(entry.entryId)) {
                Log.d(TAG, "Deferring exit for ${entry.entryId}: entry has pending CREATE journal")
                failed++
                continue
            }

            try {
                val response = api.registerExit(entry.entryId)
                if (response.isSuccessful) {
                    database.pendingExitDao().deleteById(entry.id)
                    synced++
                } else {
                    database.pendingExitDao().incrementRetry(entry.id, response.message() ?: "HTTP ${response.code()}")
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
                database.pendingExitDao().incrementRetry(entry.id, e.message ?: "Unknown error")
                failed++
            }
        }

        return if (failed > 0) SyncResult.Partial(synced, failed) else SyncResult.Success(synced)
    }

    private suspend fun hasPendingCreate(entryId: String): Boolean {
        return database.changeJournalDao().hasUnsyncedCreate("vehicle_entry", entryId) > 0
    }

    suspend fun getPendingCount(): Int {
        return database.pendingExitDao().count()
    }

    sealed class SyncResult {
        data object Noop : SyncResult()
        data class Success(val count: Int) : SyncResult()
        data class Partial(val synced: Int, val failed: Int) : SyncResult()
    }

    companion object {
        private const val TAG = "ExitSyncManager"
    }
}

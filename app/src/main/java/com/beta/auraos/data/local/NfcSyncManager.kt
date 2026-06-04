package com.beta.auraos.data.local

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.PendingNfcEntryEntity
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.NfcEntryRequest
import com.beta.auraos.data.remote.ParkingApi
import java.io.IOException

class NfcSyncManager(
    private val database: AppDatabase
) {

    private val api = ApiClient.createApi<ParkingApi>()

    suspend fun enqueue(
        cardUuid: String,
        branchId: String,
        memberName: String?,
        membershipId: String?,
        localEntryId: String? = null
    ) {
        database.pendingNfcEntryDao().insert(
            PendingNfcEntryEntity(
                cardUuid = cardUuid,
                branchId = branchId,
                memberName = memberName,
                membershipId = membershipId,
                localEntryId = localEntryId
            )
        )
    }

    suspend fun syncPending(): SyncResult {
        val pending = database.pendingNfcEntryDao().getAll()
        if (pending.isEmpty()) return SyncResult.Noop

        var synced = 0
        var failed = 0

        for (entry in pending) {
            try {
                val response = api.registerNfcEntry(NfcEntryRequest(cardUuid = entry.cardUuid))
                if (response.isSuccessful) {
                    database.pendingNfcEntryDao().deleteById(entry.id)
                    synced++
                } else {
                    database.pendingNfcEntryDao().deleteById(entry.id)
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
                database.pendingNfcEntryDao().deleteById(entry.id)
                failed++
            }
        }

        return if (failed > 0) SyncResult.Partial(synced, failed) else SyncResult.Success(synced)
    }

    suspend fun getPendingCount(): Int {
        return database.pendingNfcEntryDao().count()
    }

    sealed class SyncResult {
        data object Noop : SyncResult()
        data class Success(val count: Int) : SyncResult()
        data class Partial(val synced: Int, val failed: Int) : SyncResult()
    }
}

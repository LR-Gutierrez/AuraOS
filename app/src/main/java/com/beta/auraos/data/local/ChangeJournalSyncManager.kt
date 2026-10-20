package com.beta.auraos.data.local

import android.util.Log
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.BranchEntity
import com.beta.auraos.data.local.room.ChangeJournalEntity
import com.beta.auraos.data.local.room.MembershipEntity
import com.beta.auraos.data.local.room.VehicleEntryEntity
import com.beta.auraos.data.model.ChangeDto
import com.beta.auraos.data.model.ChangePayload
import com.beta.auraos.data.model.PushChangesRequest
import com.beta.auraos.data.model.RejectedChangeDto
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ChangeJournalApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class ChangeJournalSyncManager(
    private val database: AppDatabase,
    private val changeJournal: ChangeJournal,
    private val gson: Gson = Gson()
) {

    private val api: ChangeJournalApi = ApiClient.createApi<ChangeJournalApi>()

    suspend fun sync(token: String): SyncResult {
        Log.d(TAG, "--- ChangeJournal sync started ---")

        val pushResult = pushLocalChanges(token)
        if (pushResult is SyncResult.Error) {
            Log.w(TAG, "Push phase failed: ${pushResult.message}")
            return pushResult
        }

        val pullResult = pullRemoteChanges(token)
        if (pullResult is SyncResult.Error) {
            Log.w(TAG, "Pull phase failed: ${pullResult.message}")
            return pullResult
        }

        cleanupOldEntries()
        Log.d(TAG, "--- ChangeJournal sync completed ---")
        return SyncResult.Success()
    }

    private suspend fun pushLocalChanges(token: String): SyncResult {
        val unsynced = changeJournal.getUnsyncedChanges()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No local changes to push")
            return SyncResult.Success()
        }

        Log.d(TAG, "Pushing ${unsynced.size} local changes")

        val payloads = unsynced.map { it.toPayload() }
        val request = PushChangesRequest(changes = payloads)

        return try {
            val response = api.pushChanges(authHeader = "Bearer $token", request)
            if (response.isSuccessful) {
                val ids = unsynced.map { it.id }
                changeJournal.markAsSynced(ids)
                Log.d(TAG, "Successfully pushed ${ids.size} changes")
                response.body()?.rejected.orEmpty().forEach { undoRejected(it) }
                SyncResult.Success()
            } else {
                SyncResult.Error("Push failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            val msg = when (e) {
                is IOException -> "Network error during push: ${e.message}"
                else -> "Push error: ${e.message}"
            }
            SyncResult.Error(msg)
        }
    }

    /**
     * The server refused this change for the user's role, so the device must not keep it:
     * otherwise this phone would show branches or rates nobody else sees. Retrying is pointless,
     * so the journal entry stays marked as synced.
     */
    private suspend fun undoRejected(rejected: RejectedChangeDto) {
        Log.w(TAG, "Server rejected ${rejected.operation} ${rejected.entityType}/${rejected.entityId} (${rejected.reason}), restoring its copy")
        try {
            when (rejected.entityType) {
                "branch" -> BranchRepository(ApiClient.createApi(), database, changeJournal)
                    .restoreFromServer(rejected.entityId)
                "membership" -> MembershipRepository(database, changeJournal)
                    .restoreFromServer(rejected.entityId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore ${rejected.entityType}/${rejected.entityId}: ${e.message}")
        }
    }

    private suspend fun pullRemoteChanges(token: String): SyncResult {
        val since = changeJournal.getLastSyncedTimestamp()
        val deviceId = changeJournal.getDeviceId()

        return try {
            val response = api.pullChanges(
                authHeader = "Bearer $token",
                since = since,
                deviceId = deviceId
            )
            if (!response.isSuccessful) {
                return SyncResult.Error("Pull failed: ${response.code()} ${response.message()}")
            }

            val body = response.body() ?: return SyncResult.Error("Empty pull response")

            val filtered = body.changes.filter { it.deviceId != deviceId }
            if (filtered.isEmpty()) {
                Log.d(TAG, "No remote changes to apply")
                return SyncResult.Success(0)
            }

            Log.d(TAG, "Applying ${filtered.size} remote changes")
            for (change in filtered) {
                applyRemoteChange(change)
            }

            SyncResult.Success(filtered.size)
        } catch (e: Exception) {
            val msg = when (e) {
                is IOException -> "Network error during pull: ${e.message}"
                else -> "Pull error: ${e.message}"
            }
            SyncResult.Error(msg)
        }
    }

    private suspend fun applyRemoteChange(change: ChangeDto) {
        val localChanges = changeJournal.getChangesForEntity(change.entityType, change.entityId)
            .filter {
                !it.synced && it.timestamp > change.timestamp
                        && it.operation != ChangeJournal.OP_DELETE
            }

        when (change.operation) {
            ChangeJournal.OP_CREATE -> {
                val existing = getLocalEntity(change.entityType, change.entityId)
                if (existing == null) {
                    upsertFromMap(change.entityType, change.entityId, change.data, change.timestamp)
                } else if (localChanges.isEmpty() && change.timestamp > (existing.lastModifiedAt ?: 0L)) {
                    upsertFromMap(change.entityType, change.entityId, change.data, change.timestamp)
                }
            }

            ChangeJournal.OP_UPDATE -> {
                val existing = getLocalEntity(change.entityType, change.entityId)
                if (existing == null) {
                    upsertFromMap(change.entityType, change.entityId, change.data, change.timestamp)
                } else if (localChanges.isEmpty()) {
                    upsertFromMap(change.entityType, change.entityId, change.data, change.timestamp)
                } else {
                    mergeAndApply(change, existing, localChanges)
                }
            }

            ChangeJournal.OP_DELETE -> {
                deleteLocalEntity(change.entityType, change.entityId)
            }
        }
    }

    private suspend fun mergeAndApply(
        remote: ChangeDto,
        existing: Any,
        localChanges: List<ChangeJournalEntity>
    ) {
        val remoteChanged = remote.changedFields ?: emptyMap()
        if (remoteChanged.isEmpty()) {
            upsertFromMap(remote.entityType, remote.entityId, remote.data, remote.timestamp)
            return
        }

        val localFieldTimestamps = mutableMapOf<String, Long>()
        val localFieldValues = mutableMapOf<String, Any?>()
        for (localChange in localChanges) {
            val fields = localChange.changedFields?.let { deserializeMap(it) } ?: continue
            for ((field, value) in fields) {
                if (localChange.timestamp > (localFieldTimestamps[field] ?: 0L)) {
                    localFieldTimestamps[field] = localChange.timestamp
                    localFieldValues[field] = value
                }
            }
        }

        val mergedData = remote.data.toMutableMap()
        for ((field, remoteValue) in remoteChanged) {
            val localTs = localFieldTimestamps[field]
            if (localTs != null && localTs > remote.timestamp) {
                mergedData[field] = localFieldValues[field]
                Log.d(TAG, "Merge: keeping local ${remote.entityType}.$field (local ts=$localTs > remote ts=${remote.timestamp})")
            } else {
                mergedData[field] = remoteValue
                Log.d(TAG, "Merge: applying remote ${remote.entityType}.$field")
            }
        }

        upsertFromMap(remote.entityType, remote.entityId, mergedData, remote.timestamp)
    }

    private suspend fun getLocalEntity(entityType: String, entityId: String): EntityWithTimestamp? {
        return when (entityType) {
            "branch" -> {
                val entity = database.branchDao().getById(entityId)
                entity?.let { EntityWithTimestamp(it.lastModifiedAt) }
            }
            "membership" -> {
                val entity = database.membershipDao().getById(entityId)
                entity?.let { EntityWithTimestamp(it.lastModifiedAt) }
            }
            "vehicle_entry" -> {
                val entity = database.vehicleEntryDao().getById(entityId)
                entity?.let { EntityWithTimestamp(it.lastModifiedAt) }
            }
            else -> null
        }
    }

    private suspend fun upsertFromMap(
        entityType: String,
        entityId: String,
        data: Map<String, Any?>,
        remoteTimestamp: Long
    ) {
        when (entityType) {
            "branch" -> {
                val entity = mapToBranchEntity(entityId, data, remoteTimestamp)
                database.branchDao().insert(entity)
            }
            "membership" -> {
                val entity = mapToMembershipEntity(entityId, data, remoteTimestamp)
                database.membershipDao().insert(entity)
            }
            "vehicle_entry" -> {
                val entity = mapToVehicleEntryEntity(entityId, data, remoteTimestamp)
                database.vehicleEntryDao().insert(entity)
            }
        }
    }

    private suspend fun deleteLocalEntity(entityType: String, entityId: String) {
        when (entityType) {
            "branch" -> database.branchDao().deleteById(entityId)
            "membership" -> database.membershipDao().deleteById(entityId)
            "vehicle_entry" -> database.vehicleEntryDao().deleteById(entityId)
        }
    }

    private fun mapToBranchEntity(id: String, data: Map<String, Any?>, lastModifiedAt: Long): BranchEntity {
        return BranchEntity(
            id = id,
            name = data["name"] as? String ?: "",
            address = data["address"] as? String,
            motorcycleCapacity = (data["motorcycleCapacity"] as? Number)?.toInt() ?: 0,
            lightVehicleCapacity = (data["lightVehicleCapacity"] as? Number)?.toInt() ?: 0,
            heavyVehicleCapacity = (data["heavyVehicleCapacity"] as? Number)?.toInt() ?: 0,
            motorcycleRate = (data["motorcycleRate"] as? Number)?.toDouble() ?: 0.0,
            lightVehicleRate = (data["lightVehicleRate"] as? Number)?.toDouble() ?: 0.0,
            heavyVehicleRate = (data["heavyVehicleRate"] as? Number)?.toDouble() ?: 0.0,
            motorcycleOvernightRate = (data["motorcycleOvernightRate"] as? Number)?.toDouble() ?: 0.0,
            lightVehicleOvernightRate = (data["lightVehicleOvernightRate"] as? Number)?.toDouble() ?: 0.0,
            heavyVehicleOvernightRate = (data["heavyVehicleOvernightRate"] as? Number)?.toDouble() ?: 0.0,
            openTimeWeekday = data["openTimeWeekday"] as? String ?: "07:00",
            closeTimeWeekday = data["closeTimeWeekday"] as? String ?: "23:00",
            openTimeWeekend = data["openTimeWeekend"] as? String ?: "09:00",
            closeTimeWeekend = data["closeTimeWeekend"] as? String ?: "22:00",
            currency = data["currency"] as? String ?: "USD",
            createdAt = data["createdAt"] as? String ?: "",
            updatedAt = data["updatedAt"] as? String ?: "",
            favorite = (data["favorite"] as? Boolean) ?: false,
            lastModifiedAt = lastModifiedAt
        )
    }

    private fun mapToMembershipEntity(id: String, data: Map<String, Any?>, lastModifiedAt: Long): MembershipEntity {
        return MembershipEntity(
            id = id,
            memberName = data["memberName"] as? String ?: "",
            tier = data["tier"] as? String ?: "",
            startDate = data["startDate"] as? String ?: "",
            endDate = data["endDate"] as? String ?: "",
            isActive = (data["isActive"] as? Boolean) ?: true,
            branchId = data["branchId"] as? String ?: "",
            cardUuid = data["cardUuid"] as? String,
            createdAt = data["createdAt"] as? String,
            updatedAt = data["updatedAt"] as? String,
            lastModifiedAt = lastModifiedAt
        )
    }

    private fun mapToVehicleEntryEntity(id: String, data: Map<String, Any?>, lastModifiedAt: Long): VehicleEntryEntity {
        return VehicleEntryEntity(
            id = id,
            plate = data["plate"] as? String ?: "",
            vehicleType = data["vehicleType"] as? String ?: "",
            isVip = (data["isVip"] as? Boolean) ?: false,
            membershipId = data["membershipId"] as? String,
            branchId = data["branchId"] as? String ?: "",
            platePhotoUrl = data["platePhotoUrl"] as? String,
            frontPhotoUrl = data["frontPhotoUrl"] as? String,
            rearPhotoUrl = data["rearPhotoUrl"] as? String,
            leftPhotoUrl = data["leftPhotoUrl"] as? String,
            rightPhotoUrl = data["rightPhotoUrl"] as? String,
            createdAt = data["createdAt"] as? String ?: "",
            exitedAt = data["exitedAt"] as? String,
            duration = data["duration"] as? String,
            fee = (data["fee"] as? Number)?.toDouble(),
            lastModifiedAt = lastModifiedAt
        )
    }

    private suspend fun cleanupOldEntries() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        database.changeJournalDao().deleteOldSynced(cutoff)
    }

    private fun deserializeMap(json: String): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    private fun ChangeJournalEntity.toPayload(): ChangePayload {
        val parsedData = deserializeMap(this.data)
        val parsedChanged = this.changedFields?.let { deserializeMap(it) }
        return ChangePayload(
            entityType = this.entityType,
            entityId = this.entityId,
            operation = this.operation,
            data = parsedData,
            changedFields = parsedChanged,
            timestamp = this.timestamp,
            deviceId = this.deviceId
        )
    }

    data class EntityWithTimestamp(val lastModifiedAt: Long?)

    sealed class SyncResult {
        data class Success(val changesApplied: Int = 0) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    suspend fun pullOnly(token: String): SyncResult {
        Log.d(TAG, "--- Pull-only sync (SSE-triggered) ---")
        return pullRemoteChanges(token)
    }

    companion object {
        private const val TAG = "ChangeJournalSync"
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}

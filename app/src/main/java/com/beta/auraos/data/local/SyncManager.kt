package com.beta.auraos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.BranchEntity
import com.beta.auraos.data.local.room.MembershipEntity
import com.beta.auraos.data.local.room.VehicleEntryEntity
import com.beta.auraos.data.model.SyncResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.SyncApi
import java.io.IOException

class SyncManager(
    context: Context,
    private val database: AppDatabase,
    private val token: String
) {

    private val api: SyncApi = ApiClient.createApi()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastSyncTimestamp(): String? {
        return prefs.getString(KEY_LAST_SYNC, null)
    }

    private fun saveLastSyncTimestamp(timestamp: String) {
        prefs.edit().putString(KEY_LAST_SYNC, timestamp).apply()
    }

    suspend fun sync(): SyncResult {
        val lastSync = getLastSyncTimestamp()
        Log.d(TAG, "Starting sync" + if (lastSync != null) " since=$lastSync" else " (full)")
        val response: SyncResponse = try {
            api.sync(authHeader = "Bearer $token", since = lastSync)
        } catch (e: Exception) {
            val result = when (e) {
                is IOException -> SyncResult.NetworkError
                else -> SyncResult.Error(e.message ?: "Unknown error")
            }
            Log.w(TAG, "Sync failed: $result — ${e.message}")
            return result
        }

        if (response.branches.isNotEmpty()) {
            database.branchDao().insertAll(response.branches.map { b ->
                BranchEntity(
                    id = b.id, name = b.name, address = b.address,
                    motorcycleCapacity = b.motorcycleCapacity,
                    lightVehicleCapacity = b.lightVehicleCapacity,
                    heavyVehicleCapacity = b.heavyVehicleCapacity,
                    motorcycleRate = b.motorcycleRate,
                    lightVehicleRate = b.lightVehicleRate,
                    heavyVehicleRate = b.heavyVehicleRate,
                    motorcycleOvernightRate = b.motorcycleOvernightRate,
                    lightVehicleOvernightRate = b.lightVehicleOvernightRate,
                    heavyVehicleOvernightRate = b.heavyVehicleOvernightRate,
                    openTimeWeekday = b.openTimeWeekday,
                    closeTimeWeekday = b.closeTimeWeekday,
                    openTimeWeekend = b.openTimeWeekend,
                    closeTimeWeekend = b.closeTimeWeekend,
                    currency = b.currency,
                    createdAt = b.createdAt,
                    updatedAt = b.updatedAt,
                    favorite = b.favorite ?: false
                )
            })
        }

        if (response.memberships.isNotEmpty()) {
            database.membershipDao().insertAll(response.memberships.map { m ->
                MembershipEntity(
                    id = m.id, memberName = m.memberName, tier = m.tier,
                    startDate = m.startDate, endDate = m.endDate,
                    isActive = m.isActive, branchId = m.branchId,
                    cardUuid = m.cardUuid, createdAt = m.createdAt,
                    updatedAt = m.updatedAt
                )
            })
        }

        if (response.vehicleEntries.isNotEmpty()) {
            database.vehicleEntryDao().insertAll(response.vehicleEntries.map { e ->
                VehicleEntryEntity(
                    id = e.id, plate = e.plate, vehicleType = e.vehicleType,
                    isVip = e.isVip, membershipId = e.membershipId,
                    branchId = e.branchId, platePhotoUrl = e.platePhotoUrl,
                    frontPhotoUrl = e.frontPhotoUrl,
                    rearPhotoUrl = e.rearPhotoUrl,
                    leftPhotoUrl = e.leftPhotoUrl,
                    rightPhotoUrl = e.rightPhotoUrl,
                    createdAt = e.createdAt, exitedAt = e.exitedAt
                )
            })
        }

        saveLastSyncTimestamp(response.syncTimestamp)
        val result = SyncResult.Success(response.syncTimestamp)
        Log.d(TAG, "Sync completed: ${response.branches.size} branches, " +
                "${response.memberships.size} memberships, " +
                "${response.vehicleEntries.size} vehicle entries, " +
                "ts=${response.syncTimestamp}")
        return result
    }

    sealed class SyncResult {
        data class Success(val timestamp: String) : SyncResult()
        data object NetworkError : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val PREFS_NAME = "aura_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }
}

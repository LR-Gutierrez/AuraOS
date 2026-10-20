package com.beta.auraos.data

import android.util.Log
import com.beta.auraos.data.local.NfcSyncManager
import com.beta.auraos.data.local.SyncCoordinator
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.NfcEntryRequest
import com.beta.auraos.data.remote.ParkingApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Registers a member's entry from their NFC card, shared by Registro and the kiosk. The entry
 * is saved on the device first; without a server answer it is queued and sent later.
 */
class MembershipEntryRegistrar(database: AppDatabase) {

    sealed class Outcome {
        data object Registered : Outcome()
        data object Queued : Outcome()
        data object AlreadyInside : Outcome()
    }

    private val vehicleEntryRepository = VehicleEntryRepository(database)
    private val nfcSyncManager = NfcSyncManager(database)
    private val parkingApi: ParkingApi by lazy { ApiClient.createApi<ParkingApi>() }

    /** The caller notifies its sync coordinator afterwards, as with any other mutation. */
    suspend fun register(membership: Membership, cardUuid: String, branchId: String): Outcome {
        if (vehicleEntryRepository.getActiveEntryByMembership(membership.id) != null) {
            return Outcome.AlreadyInside
        }

        val localEntryId = UUID.randomUUID().toString()
        val localEntry = VehicleEntry(
            id = localEntryId,
            plate = "NFC",
            vehicleType = DEFAULT_VEHICLE_TYPE,
            isVip = true,
            membershipId = membership.id,
            branchId = branchId,
            platePhotoUrl = null,
            frontPhotoUrl = null,
            rearPhotoUrl = null,
            leftPhotoUrl = null,
            rightPhotoUrl = null,
            createdAt = nowUtcString(),
            exitedAt = null,
            duration = null,
            fee = null,
            branch = null
        )
        vehicleEntryRepository.updateCachedEntry(localEntry)

        SyncCoordinator.isApiInProgress = true
        return try {
            val response = parkingApi.registerNfcEntry(NfcEntryRequest(cardUuid = cardUuid))
            val serverEntry = response.body()?.data
            if (response.isSuccessful) {
                if (serverEntry != null) {
                    vehicleEntryRepository.replaceLocalWithServerEntry(localEntryId, serverEntry)
                }
                Outcome.Registered
            } else {
                nfcSyncManager.enqueue(cardUuid, branchId, membership.memberName, membership.id, localEntryId)
                Outcome.Queued
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerNfcEntry exception: ${e.message}", e)
            nfcSyncManager.enqueue(cardUuid, branchId, membership.memberName, membership.id, localEntryId)
            Outcome.Queued
        } finally {
            SyncCoordinator.isApiInProgress = false
        }
    }

    private fun nowUtcString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val TAG = "MembershipEntry"
        private const val DEFAULT_VEHICLE_TYPE = "light"
    }
}

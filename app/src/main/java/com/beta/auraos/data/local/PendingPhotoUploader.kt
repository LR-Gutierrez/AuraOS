package com.beta.auraos.data.local

import android.content.Context
import android.util.Log
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException

/**
 * Sends the photos [PendingPhotoStore] kept for entries saved without the server. An entry's
 * record has to reach the server first (its CREATE syncs through the change journal), so
 * entries still waiting on that are skipped until a later pass.
 */
class PendingPhotoUploader(
    private val context: Context,
    private val database: AppDatabase,
    private val api: ParkingApi = ApiClient.createApi(),
) {

    /** Returns how many entries had their photos uploaded. */
    suspend fun uploadPending(): Int {
        var uploaded = 0
        for (entryId in PendingPhotoStore.pendingEntryIds(context)) {
            if (database.changeJournalDao().hasUnsyncedCreate("vehicle_entry", entryId) > 0) continue
            val photos = PendingPhotoStore.photos(context, entryId)
            if (photos.isEmpty()) {
                PendingPhotoStore.clear(context, entryId)
                continue
            }
            val parts = photos.map { (slot, file) ->
                MultipartBody.Part.createFormData(fieldFor(slot), file.name, file.asRequestBody(JPEG))
            }
            val response = try {
                api.attachPhotos(entryId, parts)
            } catch (e: IOException) {
                Log.d(TAG, "Offline, photo upload deferred: ${e.message}")
                return uploaded
            }
            val entry = response.body()?.data
            if (!response.isSuccessful || entry == null) {
                // 404: the server rejected or never created the entry. Keep the evidence.
                Log.w(TAG, "Photos for $entryId not accepted (HTTP ${response.code()})")
                continue
            }
            database.vehicleEntryDao().getById(entryId)?.let { local ->
                database.vehicleEntryDao().insert(
                    local.copy(
                        platePhotoUrl = entry.platePhotoUrl ?: local.platePhotoUrl,
                        frontPhotoUrl = entry.frontPhotoUrl ?: local.frontPhotoUrl,
                        rearPhotoUrl = entry.rearPhotoUrl ?: local.rearPhotoUrl,
                        leftPhotoUrl = entry.leftPhotoUrl ?: local.leftPhotoUrl,
                        rightPhotoUrl = entry.rightPhotoUrl ?: local.rightPhotoUrl,
                    )
                )
            }
            PendingPhotoStore.clear(context, entryId)
            uploaded++
        }
        return uploaded
    }

    /** Capture slots are named after the angle; the backend calls the plate field "platePhoto". */
    private fun fieldFor(slot: String) = if (slot == "plate") "platePhoto" else slot

    companion object {
        private const val TAG = "PendingPhotoUploader"
        private val JPEG = "image/jpeg".toMediaType()
    }
}

package com.beta.auraos.data.local

import android.content.Context
import java.io.File

/**
 * Keeps the photos of an entry the server has not confirmed yet (offline, or a rejected
 * request): the entry itself syncs later as data through the change journal, but that channel
 * carries no images, so the evidence is parked here under the entry id instead of being
 * deleted with the form. Uploading them needs a backend endpoint that attaches photos to an
 * existing entry; until then they stay on the device, one folder per entry.
 */
object PendingPhotoStore {

    private const val ROOT = "pending_photos"

    fun dir(context: Context, entryId: String): File = File(File(context.filesDir, ROOT), entryId)

    /** Moves [photos] (slot name → file) into the entry's folder; missing files are skipped. */
    fun keep(context: Context, entryId: String, photos: Map<String, File>): Int {
        val target = dir(context, entryId).apply { mkdirs() }
        var kept = 0
        photos.forEach { (slot, file) ->
            if (!file.exists() || file.length() == 0L) return@forEach
            val dest = File(target, "$slot.jpg")
            if (file.renameTo(dest) || runCatching { file.copyTo(dest, overwrite = true); file.delete() }.isSuccess) {
                kept++
            }
        }
        if (kept == 0) target.delete()
        return kept
    }

    /** Entry ids that still have photos waiting for upload. */
    fun pendingEntryIds(context: Context): List<String> =
        File(context.filesDir, ROOT).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
}

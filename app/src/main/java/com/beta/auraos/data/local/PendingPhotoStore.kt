package com.beta.auraos.data.local

import android.content.Context
import java.io.File

/**
 * Keeps the photos of an entry the server has not confirmed yet (offline, or a rejected
 * request): the entry itself syncs later as data through the change journal, but that channel
 * carries no images, so the evidence is parked here under the entry id instead of being
 * deleted with the form. [PendingPhotoUploader] sends them once the server knows the entry.
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

    /** Slot name → file for one entry's waiting photos. */
    fun photos(context: Context, entryId: String): Map<String, File> =
        dir(context, entryId).listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.associateBy { it.nameWithoutExtension }
            .orEmpty()

    /** Drops an entry's folder once the server has its photos. */
    fun clear(context: Context, entryId: String) {
        dir(context, entryId).deleteRecursively()
    }

    /** Entry ids that still have photos waiting for upload. */
    fun pendingEntryIds(context: Context): List<String> =
        File(context.filesDir, ROOT).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
}

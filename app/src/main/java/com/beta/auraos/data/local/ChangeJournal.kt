package com.beta.auraos.data.local

import android.content.Context
import android.content.SharedPreferences
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.ChangeJournalDao
import com.beta.auraos.data.local.room.ChangeJournalEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class ChangeJournal(
    database: AppDatabase,
    context: Context
) {
    private val dao: ChangeJournalDao = database.changeJournalDao()

    var onMutation: (() -> Unit)? = null
    private val gson = Gson()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceId: String = getOrCreateDeviceId()

    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceId(): String = deviceId

    suspend fun recordCreate(entityType: String, entityId: String, data: Map<String, Any?>) {
        dao.insert(
            ChangeJournalEntity(
                entityType = entityType,
                entityId = entityId,
                operation = OP_CREATE,
                data = gson.toJson(data),
                changedFields = null,
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId
            )
        )
        onMutation?.invoke()
    }

    suspend fun recordUpdate(
        entityType: String,
        entityId: String,
        data: Map<String, Any?>,
        changedFields: Map<String, Any?>
    ) {
        dao.insert(
            ChangeJournalEntity(
                entityType = entityType,
                entityId = entityId,
                operation = OP_UPDATE,
                data = gson.toJson(data),
                changedFields = gson.toJson(changedFields),
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId
            )
        )
        onMutation?.invoke()
    }

    suspend fun recordDelete(entityType: String, entityId: String, data: Map<String, Any?>) {
        dao.insert(
            ChangeJournalEntity(
                entityType = entityType,
                entityId = entityId,
                operation = OP_DELETE,
                data = gson.toJson(data),
                changedFields = null,
                timestamp = System.currentTimeMillis(),
                deviceId = deviceId
            )
        )
        onMutation?.invoke()
    }

    suspend fun getUnsyncedChanges(): List<ChangeJournalEntity> {
        return dao.getUnsyncedChanges()
    }

    suspend fun markAsSynced(ids: List<Long>) {
        dao.markAsSynced(ids)
    }

    suspend fun getLastSyncedTimestamp(): Long? {
        return dao.getLastSyncedTimestamp()
    }

    suspend fun getChangesForEntity(entityType: String, entityId: String): List<ChangeJournalEntity> {
        return dao.getChangesForEntity(entityType, entityId)
    }

    fun deserializeData(json: String): Map<String, Any?> {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    companion object {
        private const val PREFS_NAME = "aura_change_journal_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}

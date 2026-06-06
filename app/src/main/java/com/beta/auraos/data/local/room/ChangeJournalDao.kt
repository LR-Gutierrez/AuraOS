package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChangeJournalDao {

    @Insert
    suspend fun insert(change: ChangeJournalEntity)

    @Insert
    suspend fun insertAll(changes: List<ChangeJournalEntity>)

    @Query("SELECT * FROM change_journal WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedChanges(): List<ChangeJournalEntity>

    @Query("SELECT * FROM change_journal WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    suspend fun getChangesForEntity(entityType: String, entityId: String): List<ChangeJournalEntity>

    @Query("SELECT * FROM change_journal ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChanges(limit: Int = 100): List<ChangeJournalEntity>

    @Query("SELECT MAX(timestamp) FROM change_journal")
    suspend fun getLastTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM change_journal WHERE synced = 1")
    suspend fun getLastSyncedTimestamp(): Long?

    @Query("UPDATE change_journal SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM change_journal WHERE synced = 1 AND timestamp < :before")
    suspend fun deleteOldSynced(before: Long)
}

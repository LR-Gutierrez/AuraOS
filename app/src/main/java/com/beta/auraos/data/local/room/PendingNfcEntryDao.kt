package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingNfcEntryDao {

    @Query("SELECT * FROM pending_nfc_entries ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingNfcEntryEntity>

    @Query("SELECT COUNT(*) FROM pending_nfc_entries")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: PendingNfcEntryEntity)

    @Query("DELETE FROM pending_nfc_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_nfc_entries")
    suspend fun deleteAll()
}

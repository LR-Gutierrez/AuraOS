package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingExitDao {

    @Query("SELECT * FROM pending_exits ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingExitEntity>

    @Query("SELECT COUNT(*) FROM pending_exits")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entry: PendingExitEntity)

    @Query("DELETE FROM pending_exits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_exits SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String)

    @Query("DELETE FROM pending_exits")
    suspend fun deleteAll()
}

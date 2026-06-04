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

    @Query("DELETE FROM pending_exits")
    suspend fun deleteAll()
}

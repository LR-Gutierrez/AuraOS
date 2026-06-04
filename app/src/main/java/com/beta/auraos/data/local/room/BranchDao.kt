package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BranchDao {

    @Query("SELECT * FROM branches ORDER BY name ASC")
    suspend fun getAll(): List<BranchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<BranchEntity>)

    @Query("DELETE FROM branches")
    suspend fun deleteAll()
}

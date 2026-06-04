package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MembershipDao {

    @Query("SELECT * FROM memberships WHERE branchId = :branchId")
    suspend fun getByBranch(branchId: String): List<MembershipEntity>

    @Query("SELECT * FROM memberships WHERE id = :id")
    suspend fun getById(id: String): MembershipEntity?

    @Query("SELECT * FROM memberships WHERE cardUuid = :cardUuid LIMIT 1")
    suspend fun getByCardUuid(cardUuid: String): MembershipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memberships: List<MembershipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(membership: MembershipEntity)

    @Query("DELETE FROM memberships WHERE branchId = :branchId")
    suspend fun deleteByBranch(branchId: String)

    @Query("DELETE FROM memberships WHERE id = :id")
    suspend fun deleteById(id: String)
}

package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VehicleEntryDao {

    @Query("SELECT * FROM vehicle_entries WHERE branchId = :branchId AND exitedAt IS NULL")
    suspend fun getActiveByBranch(branchId: String): List<VehicleEntryEntity>

    @Query("SELECT * FROM vehicle_entries WHERE branchId = :branchId AND exitedAt IS NOT NULL ORDER BY createdAt DESC")
    suspend fun getHistoryByBranch(branchId: String): List<VehicleEntryEntity>

    @Query("SELECT * FROM vehicle_entries WHERE branchId = :branchId AND exitedAt IS NOT NULL AND exitedAt >= :fromDate AND exitedAt < :toDate ORDER BY createdAt DESC")
    suspend fun getExitsInRange(branchId: String, fromDate: String, toDate: String): List<VehicleEntryEntity>

    @Query("SELECT * FROM vehicle_entries WHERE branchId = :branchId AND createdAt >= :fromDate AND createdAt < :toDate ORDER BY createdAt DESC")
    suspend fun getEntriesInRange(branchId: String, fromDate: String, toDate: String): List<VehicleEntryEntity>

    @Query("SELECT * FROM vehicle_entries WHERE membershipId = :membershipId AND exitedAt IS NULL LIMIT 1")
    suspend fun getActiveByMembershipId(membershipId: String): VehicleEntryEntity?

    @Query("SELECT * FROM vehicle_entries WHERE id = :id")
    suspend fun getById(id: String): VehicleEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VehicleEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VehicleEntryEntity)

    @Query("DELETE FROM vehicle_entries WHERE branchId = :branchId AND exitedAt IS NULL")
    suspend fun deleteActiveByBranch(branchId: String)

    @Query("DELETE FROM vehicle_entries WHERE branchId = :branchId AND exitedAt IS NOT NULL")
    suspend fun deleteHistoryByBranch(branchId: String)

    @Query("DELETE FROM vehicle_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM vehicle_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vehicle_entries SET exitedAt = :exitedAt, duration = :duration, fee = :fee WHERE id = :id")
    suspend fun updateExit(id: String, exitedAt: String, duration: String?, fee: Double?)
}

package com.beta.auraos.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DashboardDao {

    @Transaction
    @Query("SELECT * FROM dashboard_cache WHERE branchId = :branchId LIMIT 1")
    suspend fun getWithDetails(branchId: String): DashboardWithDetails?

    @Insert
    suspend fun insertDashboard(dashboard: DashboardEntity)

    @Insert
    suspend fun insertExpiringMemberships(memberships: List<ExpiringMembershipEntity>)

    @Insert
    suspend fun insertVipArrivals(arrivals: List<VipArrivalEntity>)

    @Query("DELETE FROM dashboard_cache WHERE branchId = :branchId")
    suspend fun deleteByBranch(branchId: String)

    @Transaction
    suspend fun save(branchId: String, dashboard: DashboardEntity,
                     memberships: List<ExpiringMembershipEntity>,
                     arrivals: List<VipArrivalEntity>) {
        deleteByBranch(branchId)
        insertDashboard(dashboard)
        if (memberships.isNotEmpty()) {
            insertExpiringMemberships(memberships)
        }
        if (arrivals.isNotEmpty()) {
            insertVipArrivals(arrivals)
        }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM dashboard_cache WHERE branchId = :branchId)")
    suspend fun hasData(branchId: String): Boolean
}

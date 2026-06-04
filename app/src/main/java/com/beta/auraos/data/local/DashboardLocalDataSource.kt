package com.beta.auraos.data.local

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.DashboardEntity
import com.beta.auraos.data.local.room.ExpiringMembershipEntity
import com.beta.auraos.data.local.room.VipArrivalEntity
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.model.ExpiringMembership
import com.beta.auraos.data.model.VipArrival
import java.io.IOException

class DashboardLocalDataSource(
    private val database: AppDatabase
) {

    suspend fun save(branchId: String, response: DashboardResponse) {
        val entity = DashboardEntity(
            branchId = branchId,
            occupancyPercent = response.occupancyPercent,
            occupiedBays = response.occupiedBays,
            totalBays = response.totalBays,
            trendPercent = response.trendPercent,
            dailyRevenue = response.dailyRevenue,
            activeMemberships = response.activeMemberships,
            totalMemberships = response.totalMemberships,
            criticalAlertsCount = response.criticalAlertsCount
        )
        val memberships = response.expiringMemberships.map { m ->
            ExpiringMembershipEntity(
                branchId = branchId,
                memberName = m.memberName,
                tier = m.tier,
                timeLeft = m.timeLeft
            )
        }
        val arrivals = response.vipArrivals.map { v ->
            VipArrivalEntity(
                branchId = branchId,
                guestName = v.guestName,
                slot = v.slot,
                membershipId = v.membershipId
            )
        }
        database.dashboardDao().save(branchId, entity, memberships, arrivals)
    }

    suspend fun load(branchId: String): DashboardResponse? {
        val result = database.dashboardDao().getWithDetails(branchId) ?: return null
        return DashboardResponse(
            occupancyPercent = result.dashboard.occupancyPercent,
            occupiedBays = result.dashboard.occupiedBays,
            totalBays = result.dashboard.totalBays,
            trendPercent = result.dashboard.trendPercent,
            dailyRevenue = result.dashboard.dailyRevenue,
            activeMemberships = result.dashboard.activeMemberships,
            totalMemberships = result.dashboard.totalMemberships,
            criticalAlertsCount = result.dashboard.criticalAlertsCount,
            expiringMemberships = result.expiringMemberships.map { m ->
                ExpiringMembership(
                    memberName = m.memberName,
                    tier = m.tier,
                    timeLeft = m.timeLeft
                )
            },
            vipArrivals = result.vipArrivals.map { v ->
                VipArrival(
                    guestName = v.guestName,
                    slot = v.slot,
                    membershipId = v.membershipId
                )
            }
        )
    }

    suspend fun hasData(branchId: String): Boolean {
        return database.dashboardDao().hasData(branchId)
    }

    fun isNetworkError(exception: Exception): Boolean {
        return exception is IOException ||
                exception.message?.contains("No se pudo conectar") == true ||
                exception.message?.contains("Unable to resolve host") == true ||
                exception.message?.contains("Failed to connect") == true ||
                exception.message?.contains("timeout") == true
    }
}

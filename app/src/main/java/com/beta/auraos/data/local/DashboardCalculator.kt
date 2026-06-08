package com.beta.auraos.data.local

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.model.ExpiringMembership
import com.beta.auraos.data.model.VipArrival
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DashboardCalculator(
    private val database: AppDatabase
) {

    suspend fun compute(branchId: String): DashboardResponse? {
        val branch = database.branchDao().getById(branchId) ?: return null

        val today = todayDateString()
        val tomorrow = tomorrowDateString()

        val activeEntries = database.vehicleEntryDao().getActiveByBranch(branchId)
        val todayExits = database.vehicleEntryDao().getExitsInRange(branchId, today, tomorrow)
        val todayEntries = database.vehicleEntryDao().getEntriesInRange(branchId, today, tomorrow)
        val memberships = database.membershipDao().getByBranch(branchId)
        val allHistory = database.vehicleEntryDao().getHistoryByBranch(branchId)

        val totalBays = branch.lightVehicleCapacity + branch.heavyVehicleCapacity + branch.motorcycleCapacity
        val occupiedBays = activeEntries.size
        val occupancyPercent = if (totalBays > 0) (occupiedBays.toDouble() / totalBays * 100) else 0.0

        val dailyRevenue = todayExits.sumOf { it.fee ?: 0.0 }
        val activeMemberships = memberships.count { it.isActive }
        val totalMemberships = memberships.size

        val yesterday = yesterdayDateString()
        val yesterdayExits = database.vehicleEntryDao().getExitsInRange(branchId, yesterday, today)
        val yesterdayEntries = database.vehicleEntryDao().getEntriesInRange(branchId, yesterday, today)

        val previousActive = database.vehicleEntryDao().getActiveByBranch(branchId).size
        val currentActive = activeEntries.size
        val trendPercent = if (previousActive > 0) {
            ((currentActive - previousActive).toDouble() / previousActive * 100)
        } else 0.0

        val expiringMemberships = memberships
            .filter { it.isActive && it.endDate != null }
            .filter { isExpiringSoon(it.endDate!!) }
            .sortedBy { it.endDate }
            .take(5)
            .map { m ->
                ExpiringMembership(
                    memberName = m.memberName,
                    tier = m.tier,
                    timeLeft = formatTimeLeft(m.endDate!!)
                )
            }

        val vipArrivals = activeEntries
            .filter { it.isVip || it.membershipId != null }
            .sortedBy { it.createdAt }
            .take(5)
            .map { entry ->
                val memberName = if (entry.membershipId != null) {
                    memberships.find { it.id == entry.membershipId }?.memberName
                        ?: entry.membershipId
                } else {
                    "VIP"
                }
                VipArrival(
                    guestName = memberName,
                    slot = entry.plate,
                    membershipId = entry.membershipId
                )
            }

        return DashboardResponse(
            occupancyPercent = occupancyPercent,
            occupiedBays = occupiedBays,
            totalBays = totalBays,
            trendPercent = trendPercent,
            dailyRevenue = dailyRevenue,
            activeMemberships = activeMemberships,
            totalMemberships = totalMemberships,
            criticalAlertsCount = 0,
            expiringMemberships = expiringMemberships,
            vipArrivals = vipArrivals
        )
    }

    private fun isExpiringSoon(endDate: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            val end = dateFormat.parse(endDate) ?: return false
            val diff = end.time - System.currentTimeMillis()
            diff in 0..7L * 24 * 60 * 60 * 1000
        } catch (_: Exception) {
            false
        }
    }

    private fun formatTimeLeft(endDate: String): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            val end = dateFormat.parse(endDate) ?: return ""
            val diffMs = end.time - System.currentTimeMillis()
            if (diffMs <= 0) return "Expired"
            val days = diffMs / (24 * 60 * 60 * 1000)
            val hours = (diffMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
            if (days > 0) "${days}d ${hours}h" else "${hours}h"
        } catch (_: Exception) {
            ""
        }
    }

    private fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date()) + "T00:00:00"
    }

    private fun tomorrowDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = Date()
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        return sdf.format(cal.time) + "T00:00:00"
    }

    private fun yesterdayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = Date()
        cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
        return sdf.format(cal.time) + "T00:00:00"
    }
}

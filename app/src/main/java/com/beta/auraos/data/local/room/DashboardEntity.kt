package com.beta.auraos.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dashboard_cache")
data class DashboardEntity(
    @PrimaryKey
    val branchId: String,
    val occupancyPercent: Double,
    val occupiedBays: Int,
    val totalBays: Int,
    val trendPercent: Double,
    val dailyRevenue: Double,
    val activeMemberships: Int,
    val totalMemberships: Int,
    val criticalAlertsCount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

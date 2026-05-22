package com.beta.auraos.data.model

data class DashboardResponse(
    val occupancyPercent: Double,
    val occupiedBays: Int,
    val totalBays: Int,
    val trendPercent: Double,
    val dailyRevenue: Double,
    val activeMemberships: Int,
    val totalMemberships: Int,
    val criticalAlertsCount: Int,
    val expiringMemberships: List<ExpiringMembership>,
    val vipArrivals: List<VipArrival>
)

data class ExpiringMembership(
    val memberName: String,
    val tier: String,
    val timeLeft: String
)

data class VipArrival(
    val guestName: String,
    val slot: String
)

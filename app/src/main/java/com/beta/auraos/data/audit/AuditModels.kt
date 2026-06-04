package com.beta.auraos.data.audit

data class AuditRecord(
    val id: String,
    val branchId: String,
    val vehicleEntryId: String,
    val plate: String,
    val vehicleType: String,
    val entryTime: String,
    val exitTime: String,
    val durationMinutes: Long?,
    val rateApplied: Double,
    val overnightRateApplied: Double,
    val baseFee: Double,
    val overnightFee: Double,
    val totalFee: Double,
    val isVip: Boolean,
    val membershipId: String? = null,
    val membershipTier: String? = null,
    val closedInBatch: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ClosureBatch(
    val id: String,
    val branchId: String,
    val closedAt: Long,
    val closedByUserId: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalVehicleRevenue: Double,
    val totalMembershipRevenue: Double,
    val totalRevenue: Double,
    val entryCount: Int,
    val exitCount: Int,
    val vipEntryCount: Int,
    val regularEntryCount: Int,
    val revenueByType: Map<String, Double>,
    val isVerified: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class MembershipPayment(
    val id: String,
    val membershipId: String,
    val branchId: String,
    val memberName: String,
    val tier: String,
    val amount: Double,
    val paymentDate: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val isRenewal: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

data class ClosureReport(
    val closure: ClosureBatch,
    val totalEntries: Int,
    val totalExits: Int,
    val revenueByType: Map<String, Double>,
    val membershipPaymentsReceived: Int,
    val membershipRevenueTotal: Double,
    val activeMembershipsCount: Int,
    val expiringMembershipsCount: Int,
    val discrepancies: List<DiscrepancyItem>
)

data class DiscrepancyItem(
    val entryId: String,
    val plate: String,
    val expectedFee: Double,
    val actualFee: Double?,
    val difference: Double
)

data class VerificationResult(
    val isMatch: Boolean,
    val expectedRevenue: Double,
    val actualRevenue: Double,
    val difference: Double,
    val discrepancies: List<DiscrepancyItem>
)

data class AuditPeriodSummary(
    val branchId: String,
    val periodStart: Long,
    val periodEnd: Long,
    val totalRevenue: Double,
    val vehicleRevenue: Double,
    val membershipRevenue: Double,
    val entryCount: Int,
    val exitCount: Int,
    val lastClosureDate: Long?,
    val pendingClosureRecords: Int
)

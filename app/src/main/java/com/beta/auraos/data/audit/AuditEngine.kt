package com.beta.auraos.data.audit

import android.content.Context
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.VehicleEntry
import kotlin.math.max

class AuditEngine(private val context: Context) {

    private val repository = AuditRepository(context)

    fun recordVehicleExit(
        entry: VehicleEntry,
        branch: Branch?,
        feePaid: Double?,
        exitTime: String
    ) {
        // Members pay monthly: their exit carries no daily or overnight charge.
        val isMember = entry.membershipId != null
        val calculatedFee = FeeCalculator.exitFee(entry, branch)
        val baseFee = calculatedFee ?: (feePaid ?: 0.0)
        val days = if (isMember) 0 else if (branch != null) FeeCalculator.elapsedDays(entry.createdAt) else 1
        val nights = max(0, days - 1)

        val rateApplied = when (entry.vehicleType) {
            "motorcycle" -> branch?.motorcycleRate ?: 0.0
            "light" -> branch?.lightVehicleRate ?: 0.0
            "heavy" -> branch?.heavyVehicleRate ?: 0.0
            else -> 0.0
        }
        val overnightRateApplied = when (entry.vehicleType) {
            "motorcycle" -> branch?.motorcycleOvernightRate ?: 0.0
            "light" -> branch?.lightVehicleOvernightRate ?: 0.0
            "heavy" -> branch?.heavyVehicleOvernightRate ?: 0.0
            else -> 0.0
        }

        val record = AuditRecord(
            id = AuditRepository.generateId(),
            branchId = entry.branchId,
            vehicleEntryId = entry.id,
            plate = entry.plate,
            vehicleType = entry.vehicleType,
            entryTime = entry.createdAt,
            exitTime = exitTime,
            durationMinutes = null,
            rateApplied = rateApplied,
            overnightRateApplied = overnightRateApplied,
            baseFee = (days * rateApplied),
            overnightFee = (nights * overnightRateApplied),
            totalFee = feePaid ?: baseFee,
            isVip = entry.isVip,
            membershipId = entry.membershipId,
            membershipTier = null
        )

        repository.recordVehicleExit(record)
    }

    suspend fun executeClosure(
        branchId: String,
        userId: String,
        memberships: List<com.beta.auraos.data.model.Membership>
    ): ClosureReport {
        val records = repository.getUnclosedRecords(branchId)
        val latestClosure = repository.getLatestClosure(branchId)
        val periodStart = latestClosure?.closedAt ?: 0L
        val periodEnd = System.currentTimeMillis()

        val totalVehicleRevenue = records.sumOf { it.totalFee }
        val revenueByType = records
            .groupBy { it.vehicleType }
            .mapValues { it.value.sumOf { r -> r.totalFee } }

        val entryCount = records.size
        val exitCount = records.count { it.exitTime.isNotEmpty() }
        val vipCount = records.count { it.isVip }
        val regularCount = entryCount - vipCount

        val membershipPayments = repository.getMembershipPayments(branchId)
            .filter { it.paymentDate >= periodStart && it.paymentDate <= periodEnd }
        val membershipRevenueTotal = membershipPayments.sumOf { it.amount }
        val totalRevenue = totalVehicleRevenue + membershipRevenueTotal

        val activeMembers = memberships.count { it.isActive }
        val expiringMembers = memberships.count {
            it.isActive && isNearExpiry(it.endDate)
        }

        val batch = ClosureBatch(
            id = AuditRepository.generateId(),
            branchId = branchId,
            closedAt = periodEnd,
            closedByUserId = userId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            totalVehicleRevenue = totalVehicleRevenue,
            totalMembershipRevenue = membershipRevenueTotal,
            totalRevenue = totalRevenue,
            entryCount = entryCount,
            exitCount = exitCount,
            vipEntryCount = vipCount,
            regularEntryCount = regularCount,
            revenueByType = revenueByType,
            isVerified = false
        )

        repository.saveClosureBatch(batch)
        repository.markRecordsAsClosed(branchId, batch.id)

        val discrepancies = records.mapNotNull { record ->
            if (record.membershipId != null) return@mapNotNull null
            val expected = record.baseFee + record.overnightFee
            if (record.totalFee != expected) {
                DiscrepancyItem(
                    entryId = record.vehicleEntryId,
                    plate = record.plate,
                    expectedFee = expected,
                    actualFee = record.totalFee,
                    difference = record.totalFee - expected
                )
            } else null
        }

        return ClosureReport(
            closure = batch,
            totalEntries = entryCount,
            totalExits = exitCount,
            revenueByType = revenueByType,
            membershipPaymentsReceived = membershipPayments.size,
            membershipRevenueTotal = membershipRevenueTotal,
            activeMembershipsCount = activeMembers,
            expiringMembershipsCount = expiringMembers,
            discrepancies = discrepancies
        )
    }

    fun getLatestClosure(branchId: String): ClosureBatch? {
        return repository.getLatestClosure(branchId)
    }

    fun getClosureHistory(branchId: String): List<ClosureBatch> {
        return repository.getClosureBatches(branchId)
    }

    /** Exits recorded since the last closure, newest first. */
    fun getPendingRecords(branchId: String): List<AuditRecord> {
        return repository.getUnclosedRecords(branchId).sortedByDescending { it.exitTime }
    }

    fun getPeriodSummary(branchId: String): AuditPeriodSummary {
        val latestClosure = repository.getLatestClosure(branchId)
        val unclosed = repository.getUnclosedRecords(branchId)
        val now = System.currentTimeMillis()
        val periodStart = latestClosure?.closedAt ?: 0L

        return AuditPeriodSummary(
            branchId = branchId,
            periodStart = periodStart,
            periodEnd = now,
            totalRevenue = unclosed.sumOf { it.totalFee },
            vehicleRevenue = unclosed.sumOf { it.totalFee },
            membershipRevenue = 0.0,
            entryCount = unclosed.size,
            exitCount = unclosed.count { it.exitTime.isNotEmpty() },
            lastClosureDate = latestClosure?.closedAt,
            pendingClosureRecords = unclosed.size
        )
    }

    private fun isNearExpiry(endDate: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val end = sdf.parse(endDate) ?: return false
            val sevenDaysFromNow = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
            end.time in System.currentTimeMillis()..sevenDaysFromNow
        } catch (e: Exception) {
            false
        }
    }
}

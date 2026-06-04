package com.beta.auraos.data.local.room

import androidx.room.Embedded
import androidx.room.Relation

data class DashboardWithDetails(
    @Embedded
    val dashboard: DashboardEntity,
    @Relation(
        parentColumn = "branchId",
        entityColumn = "branchId"
    )
    val expiringMemberships: List<ExpiringMembershipEntity>,
    @Relation(
        parentColumn = "branchId",
        entityColumn = "branchId"
    )
    val vipArrivals: List<VipArrivalEntity>
)

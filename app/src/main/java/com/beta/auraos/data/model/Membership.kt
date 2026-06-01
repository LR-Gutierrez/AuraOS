package com.beta.auraos.data.model

data class Membership(
    val id: String,
    val memberName: String,
    val tier: String,
    val startDate: String,
    val endDate: String,
    val isActive: Boolean,
    val branchId: String,
    val branch: Branch?,
    val cardUuid: String? = null
)

data class CreateMembershipInput(
    val memberName: String,
    val tier: String,
    val startDate: String,
    val endDate: String,
    val branchId: String,
    val cardUuid: String? = null
)

data class UpdateMembershipInput(
    val memberName: String? = null,
    val tier: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val cardUuid: String? = null
)

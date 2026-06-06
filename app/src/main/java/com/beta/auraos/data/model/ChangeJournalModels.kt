package com.beta.auraos.data.model

data class ChangePayload(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val data: Map<String, Any?>,
    val changedFields: Map<String, Any?>?,
    val timestamp: Long,
    val deviceId: String
)

data class PushChangesRequest(
    val changes: List<ChangePayload>
)

data class PushChangesResponse(
    val success: Boolean,
    val conflicts: List<ConflictDto> = emptyList()
)

data class ConflictDto(
    val entityType: String,
    val entityId: String,
    val localTimestamp: Long,
    val remoteTimestamp: Long,
    val resolvedFields: Map<String, Any?>?
)

data class ChangesResponse(
    val changes: List<ChangeDto>,
    val syncTimestamp: Long
)

data class ChangeDto(
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val data: Map<String, Any?>,
    val changedFields: Map<String, Any?>?,
    val timestamp: Long,
    val deviceId: String
)

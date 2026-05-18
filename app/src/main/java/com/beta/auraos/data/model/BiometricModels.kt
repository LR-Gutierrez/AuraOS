package com.beta.auraos.data.model

data class BiometricEnrollRequest(
    val userId: String,
    val publicKey: String
)

data class BiometricEnrollResponse(
    val success: Boolean,
    val message: String?,
    val errorCode: String?
)

data class BiometricLoginRequest(
    val userId: String,
    val challenge: String,
    val signature: String
)

package com.beta.auraos.data.model

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val data: LoginData?,
    val errorCode: String?,
    val errors: Map<String, String>?
)

data class LoginData(
    val token: String,
    val tokenType: String,
    val expiresIn: Long,
    val user: UserData
)

data class UserData(
    val id: String,
    val name: String,
    val email: String,
    val role: String
)

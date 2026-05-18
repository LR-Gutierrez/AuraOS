package com.beta.auraos.data.remote

import com.beta.auraos.data.model.BiometricEnrollRequest
import com.beta.auraos.data.model.BiometricEnrollResponse
import com.beta.auraos.data.model.BiometricLoginRequest
import com.beta.auraos.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class LoginRequest(
    val email: String,
    val password: String
)

interface AuthApi {

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/biometric-enroll")
    suspend fun biometricEnroll(
        @Header("Authorization") authHeader: String,
        @Body request: BiometricEnrollRequest
    ): BiometricEnrollResponse

    @POST("api/v1/biometric-login")
    suspend fun biometricLogin(@Body request: BiometricLoginRequest): LoginResponse
}

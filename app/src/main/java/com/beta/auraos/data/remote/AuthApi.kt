package com.beta.auraos.data.remote

import com.beta.auraos.data.model.BiometricEnrollRequest
import com.beta.auraos.data.model.BiometricEnrollResponse
import com.beta.auraos.data.model.BiometricLoginRequest
import com.beta.auraos.data.model.LoginResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class LoginRequest(
    val email: String,
    val password: String
)

data class AvatarUploadResponse(
    val success: Boolean,
    val data: AvatarUploadData?,
    val message: String?
)

data class AvatarUploadData(
    val avatarUrl: String
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

    @Multipart
    @POST("api/v1/users/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") authHeader: String,
        @Part avatar: MultipartBody.Part
    ): AvatarUploadResponse
}

package com.beta.auraos.data.remote

import retrofit2.http.GET

data class HealthResponse(
    val status: String,
    val timestamp: String,
    val uptime: Double,
    val database: String
)

interface HealthApi {

    @GET("api/v1/health")
    suspend fun health(): HealthResponse
}

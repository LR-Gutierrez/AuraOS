package com.beta.auraos.data.remote

import com.beta.auraos.data.model.DashboardResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface DashboardApi {

    @GET("api/v1/branches/{branchId}/dashboard")
    suspend fun getDashboard(
        @Path("branchId") branchId: String
    ): Response<DashboardResponse>
}

package com.beta.auraos.data.remote

import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface BranchApi {

    @GET("api/v1/branches")
    suspend fun getBranches(): Response<List<Branch>>

    @GET("api/v1/branches/{id}")
    suspend fun getBranch(@Path("id") id: String): Response<Branch>

    @POST("api/v1/branches")
    suspend fun createBranch(@Body body: CreateBranchInput): Response<Branch>

    @PATCH("api/v1/branches/{id}")
    suspend fun updateBranch(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Branch>

    @DELETE("api/v1/branches/{id}")
    suspend fun deleteBranch(@Path("id") id: String): Response<Unit>
}

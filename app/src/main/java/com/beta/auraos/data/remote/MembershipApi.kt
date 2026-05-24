package com.beta.auraos.data.remote

import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.UpdateMembershipInput
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MembershipApi {

    @GET("api/v1/memberships")
    suspend fun getMemberships(
        @Query("branchId") branchId: String? = null
    ): Response<List<Membership>>

    @GET("api/v1/memberships/{id}")
    suspend fun getMembership(
        @Path("id") id: String
    ): Response<Membership>

    @POST("api/v1/memberships")
    suspend fun createMembership(
        @Body body: CreateMembershipInput
    ): Response<Membership>

    @PATCH("api/v1/memberships/{id}")
    suspend fun updateMembership(
        @Path("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Membership>

    @DELETE("api/v1/memberships/{id}")
    suspend fun deleteMembership(@Path("id") id: String): Response<Unit>
}

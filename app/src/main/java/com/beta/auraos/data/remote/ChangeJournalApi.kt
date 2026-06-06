package com.beta.auraos.data.remote

import com.beta.auraos.data.model.ChangesResponse
import com.beta.auraos.data.model.PushChangesRequest
import com.beta.auraos.data.model.PushChangesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface ChangeJournalApi {

    @POST("api/v1/changes/push")
    suspend fun pushChanges(
        @Header("Authorization") authHeader: String,
        @Body request: PushChangesRequest
    ): Response<PushChangesResponse>

    @GET("api/v1/changes/pull")
    suspend fun pullChanges(
        @Header("Authorization") authHeader: String,
        @Query("since") since: Long? = null,
        @Query("deviceId") deviceId: String
    ): Response<ChangesResponse>
}

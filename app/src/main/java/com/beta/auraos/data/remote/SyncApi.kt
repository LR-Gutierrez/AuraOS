package com.beta.auraos.data.remote

import com.beta.auraos.data.model.SyncResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SyncApi {

    @GET("api/v1/sync")
    suspend fun sync(
        @Header("Authorization") authHeader: String,
        @Query("since") since: String? = null
    ): SyncResponse
}

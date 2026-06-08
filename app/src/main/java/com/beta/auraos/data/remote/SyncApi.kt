package com.beta.auraos.data.remote

import com.beta.auraos.data.model.SyncResponse
import com.beta.auraos.data.remote.fcm.FcmTokenRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {

    @GET("api/v1/sync")
    suspend fun sync(
        @Header("Authorization") authHeader: String,
        @Query("since") since: String? = null
    ): SyncResponse

    @POST("api/v1/fcm/token")
    suspend fun registerFcmToken(
        @Header("Authorization") authHeader: String,
        @Body request: FcmTokenRequest
    ): retrofit2.Response<Unit>

    @DELETE("api/v1/fcm/token")
    suspend fun unregisterFcmToken(
        @Header("Authorization") authHeader: String,
        @Query("token") token: String
    ): retrofit2.Response<Unit>
}

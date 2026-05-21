package com.beta.auraos.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ParkingApi {

    @Multipart
    @POST("test")
    suspend fun registerEntry(
        @Part("plate") plate: RequestBody,
        @Part("vehicleType") vehicleType: RequestBody,
        @Part front: MultipartBody.Part?,
        @Part rear: MultipartBody.Part?,
        @Part left: MultipartBody.Part?,
        @Part right: MultipartBody.Part?,
    ): Response<ResponseBody>
}

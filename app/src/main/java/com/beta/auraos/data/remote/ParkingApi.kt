package com.beta.auraos.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class PlateOcrResponse(
    val plate: String,
    val success: Boolean,
    val message: String? = null
)

interface ParkingApi {

    @Multipart
    @POST("api/v1/vehicle-entries")
    suspend fun registerEntry(
        @Part("plate") plate: RequestBody,
        @Part("vehicleType") vehicleType: RequestBody,
        @Part("branchId") branchId: RequestBody,
        @Part platePhoto: MultipartBody.Part?,
        @Part front: MultipartBody.Part?,
        @Part rear: MultipartBody.Part?,
        @Part left: MultipartBody.Part?,
        @Part right: MultipartBody.Part?,
    ): Response<ResponseBody>

    @Multipart
    @POST("ocr/plate")
    suspend fun recognizePlate(
        @Part image: MultipartBody.Part
    ): Response<PlateOcrResponse>
}

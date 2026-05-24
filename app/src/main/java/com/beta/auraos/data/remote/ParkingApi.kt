package com.beta.auraos.data.remote

import com.beta.auraos.data.model.VehicleEntry
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/v1/vehicle-entries")
    suspend fun getVehicleEntries(
        @Query("branchId") branchId: String? = null,
        @Query("activeOnly") activeOnly: Boolean? = null
    ): Response<List<VehicleEntry>>

    @GET("api/v1/vehicle-entries/{id}")
    suspend fun getVehicleEntry(
        @Path("id") id: String
    ): Response<VehicleEntry>

    @PATCH("api/v1/vehicle-entries/{id}/exit")
    suspend fun registerExit(
        @Path("id") id: String
    ): Response<VehicleEntry>
}

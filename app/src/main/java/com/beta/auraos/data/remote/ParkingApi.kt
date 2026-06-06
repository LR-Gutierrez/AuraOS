package com.beta.auraos.data.remote

import com.beta.auraos.data.model.VehicleEntry
import okhttp3.MultipartBody
import okhttp3.RequestBody

import retrofit2.Response
import retrofit2.http.Body
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

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

data class PaginatedData<T>(
    val data: List<T>,
    val meta: PaginationMeta
)

data class TodaySummary(
    val exitedCount: Int,
    val totalRevenue: Double
)

data class PaginationMeta(
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
    val todaySummary: TodaySummary? = null
)

data class AnalyticsSummaryResponse(
    val branchId: String,
    val date: String,
    val totalCapacity: Int,
    val inboundCount: Int,
    val activeCount: Int,
    val outboundCount: Int,
    val totalRevenue: Double,
    val avgDurationMinutes: Double,
    val inboundTrendPercent: Int,
    val activeTrendPercent: Int,
    val outboundTrendPercent: Int,
    val revenueTrendPercent: Int,
    val peakHourRevenue: Double,
    val peakHourLabel: String,
    val inboundThresholdMinutes: Int
)

data class NfcEntryRequest(val cardUuid: String)

interface ParkingApi {

    @POST("api/v1/vehicle-entries/nfc-entry")
    suspend fun registerNfcEntry(
        @Body body: NfcEntryRequest
    ): Response<ApiResponse<VehicleEntry>>

    @Multipart
    @POST("api/v1/vehicle-entries")
    suspend fun registerEntry(
        @Part("plate") plate: RequestBody,
        @Part("vehicleType") vehicleType: RequestBody,
        @Part("branchId") branchId: RequestBody,
        @Part("membershipId") membershipId: RequestBody?,
        @Part platePhoto: MultipartBody.Part?,
        @Part front: MultipartBody.Part?,
        @Part rear: MultipartBody.Part?,
        @Part left: MultipartBody.Part?,
        @Part right: MultipartBody.Part?,
    ): Response<ApiResponse<VehicleEntry>>

    @Multipart
    @POST("ocr/plate")
    suspend fun recognizePlate(
        @Part image: MultipartBody.Part
    ): Response<PlateOcrResponse>

    @GET("api/v1/vehicle-entries")
    suspend fun getVehicleEntries(
        @Query("branchId") branchId: String? = null,
        @Query("exited") exited: Boolean? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<PaginatedData<VehicleEntry>>

    @GET("api/v1/vehicle-entries/{id}")
    suspend fun getVehicleEntry(
        @Path("id") id: String
    ): Response<ApiResponse<VehicleEntry>>

    @PATCH("api/v1/vehicle-entries/{id}/exit")
    suspend fun registerExit(
        @Path("id") id: String
    ): Response<ApiResponse<VehicleEntry>>

    @GET("api/v1/analytics/summary")
    suspend fun getAnalyticsSummary(
        @Query("branchId") branchId: String,
        @Query("date") date: String? = null,
        @Query("inboundThresholdMinutes") inboundThresholdMinutes: Int? = null
    ): Response<AnalyticsSummaryResponse>
}

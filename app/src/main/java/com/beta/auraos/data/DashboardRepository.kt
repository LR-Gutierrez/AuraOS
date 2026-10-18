package com.beta.auraos.data

import com.beta.auraos.data.local.DashboardCalculator
import com.beta.auraos.data.local.DashboardLocalDataSource
import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.DashboardApi
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardRepository(
    private val token: String?,
    private val localDataSource: DashboardLocalDataSource,
    private val calculator: DashboardCalculator
) {

    var lastResultFromCache: Boolean = false
        private set

    private val api: DashboardApi? by lazy {
        if (token == null) null
        else {
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }

            val client = ApiClient.okHttpClient.newBuilder()
                .addInterceptor(authInterceptor)
                .build()

            Retrofit.Builder()
                .baseUrl(ApiClient.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DashboardApi::class.java)
        }
    }

    suspend fun getDashboard(branchId: String): Result<DashboardResponse> {
        val local = calculator.compute(branchId)
        if (local != null) {
            lastResultFromCache = true
            return Result.Success(local)
        }

        if (api == null) {
            val cached = localDataSource.load(branchId)
            return if (cached != null) {
                lastResultFromCache = true
                Result.Success(cached)
            } else {
                Result.Error(Exception("Sin datos locales"))
            }
        }

        return try {
            val response = api!!.getDashboard(branchId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    localDataSource.save(branchId, body)
                    lastResultFromCache = false
                    Result.Success(body)
                } else {
                    Result.Error(Exception("Sin datos del servidor"))
                }
            } else {
                Result.Error(Exception("Error del servidor: ${response.code()}"))
            }
        } catch (e: Exception) {
            val cached = localDataSource.load(branchId)
            if (cached != null) {
                lastResultFromCache = true
                Result.Success(cached)
            } else {
                Result.Error(Exception("Sin conexión: ${e.localizedMessage ?: "Error desconocido"}"))
            }
        }
    }

    private suspend fun computeLocal(branchId: String): Result<DashboardResponse> {
        val computed = calculator.compute(branchId)
        return if (computed != null) {
            lastResultFromCache = true
            Result.Success(computed)
        } else {
            Result.Error(Exception("Sin conexión y sin datos locales"))
        }
    }
}

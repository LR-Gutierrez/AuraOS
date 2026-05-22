package com.beta.auraos.data

import com.beta.auraos.data.model.DashboardResponse
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.DashboardApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DashboardRepository(private val token: String) {

    private val api: DashboardApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DashboardApi::class.java)
    }

    suspend fun getDashboard(branchId: String): Result<DashboardResponse> {
        return try {
            val response = api.getDashboard(branchId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body)
                } else {
                    Result.Error(Exception("Respuesta vacía del servidor"))
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Sesión expirada. Inicia sesión nuevamente."
                    404 -> "Sucursal no encontrada"
                    else -> "Error del servidor: ${response.code()}"
                }
                Result.Error(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }
}

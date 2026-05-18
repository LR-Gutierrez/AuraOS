package com.beta.auraos.data

import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.BiometricEnrollRequest
import com.beta.auraos.data.model.BiometricLoginRequest
import com.beta.auraos.data.model.LoggedInUser
import com.beta.auraos.data.model.LoginResponse
import com.beta.auraos.data.remote.AuthApi
import com.beta.auraos.data.remote.LoginRequest
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Remote data source that communicates with the Aura OS backend via Retrofit.
 *
 * All public methods are `suspend` functions returning [Result] so the caller
 * (typically a ViewModel) can safely dispatch them on `Dispatchers.IO` without
 * worrying about thread-safety.
 *
 * ## Error handling pattern
 * HTTP errors (4xx/5xx) are caught via `retrofit2.HttpException` and their
 * JSON body is parsed to extract the `message` field. Network errors fall
 * through to `IOException`. Both are wrapped in [Result.Error] so the UI
 * layer renders them uniformly as Toast messages.
 *
 * ## Base URL
 * Change [BASE_URL] to point to your environment (emulator → host uses
 * `10.0.2.2`; a real device on the same network uses the machine's local IP).
 */
class LoginDataSource(
    private val sessionManager: SessionManager
) {

    private val api: AuthApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }

    suspend fun login(email: String, password: String): Result<LoggedInUser> {
        return try {
            val response = api.login(LoginRequest(email, password))

            if (!response.success || response.data == null) {
                val errorMsg = response.message ?: "Error de autenticación"
                return Result.Error(IOException(errorMsg))
            }

            val loginData = response.data
            val user = LoggedInUser(
                userId = loginData.user.id,
                displayName = loginData.user.name,
                email = loginData.user.email
            )
            sessionManager.saveToken(loginData.token)
            Result.Success(user)
        } catch (e: retrofit2.HttpException) {
            val msg = parseApiError(e)
            Result.Error(IOException(msg))
        } catch (e: IOException) {
            Result.Error(IOException("No se pudo conectar con el servidor"))
        } catch (e: Exception) {
            Result.Error(IOException("Error inesperado: ${e.localizedMessage}"))
        }
    }

    suspend fun biometricEnroll(token: String, userId: String, publicKey: String): Result<Unit> {
        return try {
            val response = api.biometricEnroll(
                authHeader = "Bearer $token",
                request = BiometricEnrollRequest(userId, publicKey)
            )
            if (!response.success) {
                Result.Error(IOException(response.message ?: "Error al registrar biometría"))
            } else {
                Result.Success(Unit)
            }
        } catch (e: retrofit2.HttpException) {
            val msg = parseApiError(e)
            Result.Error(IOException(msg))
        } catch (e: IOException) {
            Result.Error(IOException("No se pudo conectar con el servidor"))
        } catch (e: Exception) {
            Result.Error(IOException("Error inesperado: ${e.localizedMessage}"))
        }
    }

    suspend fun biometricLogin(userId: String, challenge: String, signature: String): Result<LoggedInUser> {
        return try {
            val response = api.biometricLogin(
                BiometricLoginRequest(userId, challenge, signature)
            )
            if (!response.success || response.data == null) {
                val errorMsg = response.message ?: "Error de autenticación biométrica"
                return Result.Error(IOException(errorMsg))
            }
            val loginData = response.data
            val user = LoggedInUser(
                userId = loginData.user.id,
                displayName = loginData.user.name,
                email = loginData.user.email
            )
            sessionManager.saveToken(loginData.token)
            Result.Success(user)
        } catch (e: retrofit2.HttpException) {
            val msg = parseApiError(e)
            Result.Error(IOException(msg))
        } catch (e: IOException) {
            Result.Error(IOException("No se pudo conectar con el servidor"))
        } catch (e: Exception) {
            Result.Error(IOException("Error inesperado: ${e.localizedMessage}"))
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    private fun parseApiError(e: retrofit2.HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            val errorResponse = Gson().fromJson(errorBody, LoginResponse::class.java)
            errorResponse.message ?: "Error del servidor (${e.code()})"
        } catch (_: Exception) {
            when (e.code()) {
                401 -> "Credenciales inválidas"
                400 -> "Datos inválidos"
                else -> "Error del servidor (${e.code()})"
            }
        }
    }

    companion object {
        private const val BASE_URL = "http://192.168.0.143:3000/"
    }
}
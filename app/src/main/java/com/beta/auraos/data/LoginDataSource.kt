package com.beta.auraos.data

import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import android.content.ContentResolver
import android.net.Uri
import com.beta.auraos.data.model.BiometricEnrollRequest
import com.beta.auraos.data.model.BiometricLoginRequest
import com.beta.auraos.data.model.LoggedInUser
import com.beta.auraos.data.model.LoginResponse
import com.beta.auraos.data.remote.AuthApi
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.AvatarUploadResponse
import com.beta.auraos.data.remote.LoginRequest
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

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
 * The server comes from [com.beta.auraos.data.remote.ServerConfig]: the address typed in
 * the app, else `api.baseUrl.*` from local.properties (emulator → host is `10.0.2.2`).
 */
class LoginDataSource(
    private val sessionManager: SessionManager
) {

    private val api: AuthApi by lazy {
        ApiClient.createApi<AuthApi>()
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
                email = loginData.user.email,
                avatarUrl = loginData.user.avatarUrl
            )
            sessionManager.saveToken(loginData.token)
            sessionManager.saveRole(loginData.user.role)

            loginData.user.avatarUrl?.let { sessionManager.saveAvatarUrl(it) }

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
                email = loginData.user.email,
                avatarUrl = loginData.user.avatarUrl
            )
            sessionManager.saveToken(loginData.token)
            sessionManager.saveRole(loginData.user.role)

            loginData.user.avatarUrl?.let { sessionManager.saveAvatarUrl(it) }

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

    suspend fun uploadAvatar(token: String, uri: Uri, contentResolver: ContentResolver): Result<String> {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.Error(IOException("No se pudo leer la imagen"))

            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestBody)

            val response = api.uploadAvatar(
                authHeader = "Bearer $token",
                avatar = part
            )

            if (!response.success || response.data == null) {
                Result.Error(IOException(response.message ?: "Error al subir avatar"))
            } else {
                Result.Success(response.data.avatarUrl)
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

}
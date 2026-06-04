package com.beta.auraos.data.local

import com.beta.auraos.data.Result
import com.beta.auraos.data.model.LoggedInUser
import com.beta.auraos.util.PasswordHasher
import com.google.gson.Gson

class LocalAuthDataSource(
    private val sessionManager: SessionManager
) {

    private val gson = Gson()

    suspend fun login(email: String, password: String): Result<LoggedInUser> {
        val stored = sessionManager.getLocalUserProfile()
            ?: return Result.Error(IllegalStateException("No hay datos locales. Conéctate a internet para el primer inicio de sesión."))

        if (stored.email != email) {
            return Result.Error(IllegalStateException("No hay datos locales para este usuario."))
        }

        val passwordHash = sessionManager.getLocalPasswordHash()
        if (passwordHash == null || !PasswordHasher.verify(password, passwordHash)) {
            return Result.Error(IllegalStateException("Credenciales incorrectas"))
        }

        return Result.Success(
            LoggedInUser(
                userId = stored.userId,
                displayName = stored.displayName,
                email = stored.email,
                avatarUrl = stored.avatarUrl
            )
        )
    }

    suspend fun biometricLogin(userId: String): Result<LoggedInUser> {
        val stored = sessionManager.getLocalUserProfile()
            ?: return Result.Error(IllegalStateException("No hay datos locales para usuario biométrico."))

        if (stored.userId != userId) {
            return Result.Error(IllegalStateException("No hay datos locales para este usuario."))
        }

        return Result.Success(
            LoggedInUser(
                userId = stored.userId,
                displayName = stored.displayName,
                email = stored.email,
                avatarUrl = stored.avatarUrl
            )
        )
    }

    suspend fun cacheUser(user: LoggedInUser, password: String, token: String) {
        sessionManager.saveLocalUserProfile(user)
        sessionManager.saveLocalPasswordHash(PasswordHasher.hash(password))
    }

    suspend fun hasLocalUser(): Boolean {
        return sessionManager.getLocalUserProfile() != null
    }

    suspend fun isNetworkError(exception: Exception): Boolean {
        return exception is java.io.IOException ||
                exception.message?.contains("No se pudo conectar") == true ||
                exception.message?.contains("Unable to resolve host") == true ||
                exception.message?.contains("Failed to connect") == true ||
                exception.message?.contains("timeout") == true
    }
}

package com.beta.auraos.data

import android.content.ContentResolver
import android.net.Uri
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.LoggedInUser

/**
 * Single source of truth for authentication state in the app.
 *
 * Mediates between the remote [LoginDataSource] and the local
 * [SessionManager]. After a successful login the result is cached
 * in-memory via [user] so other layers can check `isLoggedIn`
 * without a disk read.
 *
 ## Why a Repository?
 * - Keeps the ViewModel unaware of where data comes from (API vs. local).
 * - Centralises side-effects such as saving the JWT token and clearing
 *   session state on logout.
 */
class LoginRepository(
    val dataSource: LoginDataSource,
    private val sessionManager: SessionManager
) {

    var user: LoggedInUser? = null
        private set

    val isLoggedIn: Boolean
        get() = user != null

    init {
        user = null
    }

    suspend fun login(email: String, password: String): Result<LoggedInUser> {
        val result = dataSource.login(email, password)

        if (result is Result.Success) {
            setLoggedInUser(result.data)
        }

        return result
    }

    fun hasBiometricCredentials(): Boolean {
        return sessionManager.hasSavedCredentials()
    }

    fun enableBiometric(email: String, password: String) {
        sessionManager.saveCredentials(email, password)
    }

    fun disableBiometric() {
        sessionManager.clearCredentials()
    }

    fun getStoredEmail(): String? = sessionManager.getEmail()

    fun getStoredPassword(): String? = sessionManager.getPassword()

    suspend fun biometricEnroll(token: String, userId: String, publicKey: String): Result<Unit> {
        return dataSource.biometricEnroll(token, userId, publicKey)
    }

    suspend fun biometricLogin(userId: String, challenge: String, signature: String): Result<LoggedInUser> {
        val result = dataSource.biometricLogin(userId, challenge, signature)
        if (result is Result.Success) {
            setLoggedInUser(result.data)
        }
        return result
    }

    fun isBiometricEnrolled(): Boolean = sessionManager.isBiometricEnrolled()

    fun setBiometricEnrolled(enrolled: Boolean) {
        sessionManager.setBiometricEnrolled(enrolled)
    }

    fun saveUserId(userId: String) {
        sessionManager.saveUserId(userId)
    }

    fun getUserId(): String? = sessionManager.getUserId()

    fun saveLastEmail(email: String) {
        sessionManager.saveLastEmail(email)
    }

    fun getLastEmail(): String? = sessionManager.getLastEmail()

    fun saveLastDisplayName(name: String) {
        sessionManager.saveLastDisplayName(name)
    }

    fun getLastDisplayName(): String? = sessionManager.getLastDisplayName()

    fun hasPreviousLogin(): Boolean {
        return sessionManager.hasPreviousLogin()
    }

    suspend fun uploadAvatar(token: String, uri: Uri, contentResolver: ContentResolver): Result<String> {
        return dataSource.uploadAvatar(token, uri, contentResolver)
    }

    fun saveAvatarUrl(url: String) {
        sessionManager.saveAvatarUrl(url)
    }

    fun getAvatarUrl(): String? = sessionManager.getAvatarUrl()

    fun logout() {
        user = null
        dataSource.logout()
    }

    private fun setLoggedInUser(loggedInUser: LoggedInUser) {
        this.user = loggedInUser
    }
}
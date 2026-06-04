package com.beta.auraos.data

import android.content.ContentResolver
import android.net.Uri
import com.beta.auraos.data.local.LocalAuthDataSource
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.LoggedInUser

class LoginRepository(
    val dataSource: LoginDataSource,
    private val sessionManager: SessionManager,
    private val localAuthDataSource: LocalAuthDataSource
) {

    var user: LoggedInUser? = null
        private set

    val isLoggedIn: Boolean
        get() = user != null

    init {
        user = null
    }

    suspend fun login(email: String, password: String): Result<LoggedInUser> {
        val remoteResult = dataSource.login(email, password)

        return when (remoteResult) {
            is Result.Success -> {
                setLoggedInUser(remoteResult.data)
                val token = sessionManager.getToken() ?: ""
                localAuthDataSource.cacheUser(remoteResult.data, password, token)
                remoteResult
            }
            is Result.Error -> {
                if (localAuthDataSource.isNetworkError(remoteResult.exception)) {
                    val localResult = localAuthDataSource.login(email, password)
                    if (localResult is Result.Success) {
                        setLoggedInUser(localResult.data)
                    }
                    localResult
                } else {
                    remoteResult
                }
            }
        }
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
        val remoteResult = dataSource.biometricLogin(userId, challenge, signature)

        return when (remoteResult) {
            is Result.Success -> {
                setLoggedInUser(remoteResult.data)
                remoteResult
            }
            is Result.Error -> {
                if (localAuthDataSource.isNetworkError(remoteResult.exception)) {
                    val localResult = localAuthDataSource.biometricLogin(userId)
                    if (localResult is Result.Success) {
                        setLoggedInUser(localResult.data)
                    }
                    localResult
                } else {
                    remoteResult
                }
            }
        }
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
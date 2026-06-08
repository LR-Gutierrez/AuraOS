package com.beta.auraos.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.beta.auraos.data.model.LoggedInUser
import com.google.gson.Gson

/**
 * Persists session data and biometric credentials with different security levels.
 *
 * ## Why two separate preference stores?
 * - **Regular `SharedPreferences`** — stores the JWT token, user ID, and
 *   the `isBiometricEnrolled` flag. These are not sensitive enough to
 *   warrant encryption (the token expires server-side).
 * - **`EncryptedSharedPreferences`** — stores the email + password pair needed
 *   for biometric auto-login. These are credentials and **must** be encrypted
 *   at rest via AES-256 GCM backed by the Android Keystore.
 *
 * ## Security note
 * Credentials are only saved here **after** a successful biometric enrollment.
 * The biometric crypto key (`BiometricKeyManager`) is what authorises their use;
 * this class just provides the encrypted container.
 */
class SessionManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
    private val encryptedPrefs: SharedPreferences

    init {
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        encryptedPrefs = createEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            appContext.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun saveCredentials(email: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getEmail(): String? = encryptedPrefs.getString(KEY_EMAIL, null)

    fun getPassword(): String? = encryptedPrefs.getString(KEY_PASSWORD, null)

    fun hasSavedCredentials(): Boolean {
        return getEmail() != null && getPassword() != null
    }

    fun clearCredentials() {
        encryptedPrefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .apply()
    }

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun isBiometricEnrolled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENROLLED, false)
    }

    fun setBiometricEnrolled(enrolled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENROLLED, enrolled).apply()
    }

    fun saveSelectedBranchId(branchId: String) {
        prefs.edit().putString(KEY_SELECTED_BRANCH_ID, branchId).apply()
    }

    fun getSelectedBranchId(): String? = prefs.getString(KEY_SELECTED_BRANCH_ID, null)

    fun clearSelectedBranchId() {
        prefs.edit().remove(KEY_SELECTED_BRANCH_ID).apply()
    }

    fun saveLastEmail(email: String) {
        prefs.edit().putString(KEY_LAST_EMAIL, email).apply()
    }

    fun getLastEmail(): String? = prefs.getString(KEY_LAST_EMAIL, null)

    fun saveLastDisplayName(name: String) {
        prefs.edit().putString(KEY_LAST_DISPLAY_NAME, name).apply()
    }

    fun getLastDisplayName(): String? = prefs.getString(KEY_LAST_DISPLAY_NAME, null)

    fun hasPreviousLogin(): Boolean {
        return getUserId() != null
    }

    fun saveAvatarUrl(url: String) {
        prefs.edit().putString(KEY_AVATAR_URL, url).apply()
    }

    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun clearSession() {
        clearToken()
        clearCredentials()
        clearLocalCache()
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_BIOMETRIC_ENROLLED)
            .remove(KEY_SELECTED_BRANCH_ID)
            .remove(KEY_LAST_EMAIL)
            .remove(KEY_LAST_DISPLAY_NAME)
            .remove(KEY_AVATAR_URL)
            .apply()
    }

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    // ── Local offline cache ────────────────────────────────────────────

    fun saveLocalUserProfile(user: LoggedInUser) {
        val json = Gson().toJson(user)
        encryptedPrefs.edit().putString(KEY_LOCAL_USER_PROFILE, json).apply()
    }

    fun getLocalUserProfile(): LoggedInUser? {
        val json = encryptedPrefs.getString(KEY_LOCAL_USER_PROFILE, null) ?: return null
        return try {
            Gson().fromJson(json, LoggedInUser::class.java)
        } catch (_: Exception) { null }
    }

    fun saveLocalPasswordHash(hash: String) {
        encryptedPrefs.edit().putString(KEY_LOCAL_PASSWORD_HASH, hash).apply()
    }

    fun getLocalPasswordHash(): String? {
        return encryptedPrefs.getString(KEY_LOCAL_PASSWORD_HASH, null)
    }

    fun clearLocalCache() {
        encryptedPrefs.edit()
            .remove(KEY_LOCAL_USER_PROFILE)
            .remove(KEY_LOCAL_PASSWORD_HASH)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "aura_prefs"
        private const val ENCRYPTED_PREFS_NAME = "aura_encrypted_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "biometric_email"
        private const val KEY_PASSWORD = "biometric_password"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_BIOMETRIC_ENROLLED = "biometric_enrolled"
        private const val KEY_SELECTED_BRANCH_ID = "selected_branch_id"
        private const val KEY_LAST_EMAIL = "last_email"
        private const val KEY_LAST_DISPLAY_NAME = "last_display_name"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_LOCAL_USER_PROFILE = "local_user_profile"
        private const val KEY_LOCAL_PASSWORD_HASH = "local_password_hash"
    }
}

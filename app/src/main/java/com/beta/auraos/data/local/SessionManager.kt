package com.beta.auraos.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    fun clearSession() {
        clearToken()
        clearCredentials()
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_BIOMETRIC_ENROLLED)
            .remove(KEY_SELECTED_BRANCH_ID)
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
    }
}

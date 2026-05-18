package com.beta.auraos.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

/**
 * Manages an RSA key pair stored in the hardware-backed AndroidKeyStore.
 *
 * ## Security contract
 * - The **private key never leaves the device** — it is stored inside the
 *   TEE/StrongBox and only unlocked via a successful biometric authentication.
 * - The **public key** (X.509, Base64) is sent to the backend during enrollment
 *   so the server can verify signatures produced by this device.
 * - If the user adds or removes a fingerprint, `KeyPermanentlyInvalidatedException`
 *   is thrown on the next signature attempt. This class handles that by deleting
 *   the stale key and returning `null`, forcing re-enrollment.
 *
 * ## Flow
 * 1. `generateKeyPair()` — called once during biometric enrollment.
 * 2. `getSignatureForAuth()` — returns a [Signature] pre-initialised with the
 *    private key, intended for `BiometricPrompt.CryptoObject`.
 * 3. After fingerprint auth succeeds, `signChallenge()` finalises the signature
 *    with the challenge string.
 *
 * @see BiometricPrompt.CryptoObject
 */
class BiometricKeyManager(context: Context) {

    private val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
    private val appContext = context.applicationContext

    fun generateKeyPair() {
        if (keyStore.containsAlias(KEY_NAME)) {
            keyStore.deleteEntry(KEY_NAME)
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_TYPE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_SIGN
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()

        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /**
     * Exports the public key in X.509 subject-public-key-info format,
     * encoded as a Base64 string.
     *
     * This is the value sent to `POST /api/v1/biometric-enroll` so the
     * server can associate this device's public key with the user account.
     */
    fun getPublicKey(): String? {
        return try {
            val cert = keyStore.getCertificate(KEY_NAME) ?: return null
            Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns a [Signature] pre-initialised for signing, or `null` if the
     * key is unavailable or has been invalidated (e.g. after a fingerprint
     * change).
     *
     * Pass the returned instance to [BiometricPrompt.CryptoObject] so that
     * Android's biometric gate protects the private key. After the user
     * authenticates successfully, call [signChallenge] on the **same**
     * signature object obtained from `authenticationResult.cryptoObject`.
     *
     * ## Edge case
     * When `KeyPermanentlyInvalidatedException` is caught the stale key is
     * deleted automatically and `null` is returned, which will trigger a
     * re-enrollment flow in the UI layer.
     */
    fun getSignatureForAuth(): Signature? {
        return try {
            val privateKey = keyStore.getKey(KEY_NAME, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Feeds [challenge] into the [Signature] (unlocked by biometric auth)
     * and returns the Base64-encoded result.
     *
     * The server will verify this signature against the stored public key
     * to prove the user is in possession of the trusted device.
     */
    fun signChallenge(signature: Signature, challenge: String): String {
        signature.update(challenge.toByteArray(Charsets.UTF_8))
        val signedBytes = signature.sign()
        return Base64.encodeToString(signedBytes, Base64.NO_WRAP)
    }

    fun isKeyGenerated(): Boolean {
        return keyStore.containsAlias(KEY_NAME)
    }

    fun deleteKey() {
        try {
            keyStore.deleteEntry(KEY_NAME)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val KEY_NAME = "auraos_biometric_key_v1"
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }
}

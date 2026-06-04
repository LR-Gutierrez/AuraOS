package com.beta.auraos.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    fun hash(password: String): String {
        val passwordChars = password.toCharArray()
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val hash = pbkdf2(passwordChars, salt)
        passwordChars.fill('0')
        return "${salt.toHex()}:${hash.toHex()}"
    }

    fun verify(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytes()
        val expectedHash = parts[1].hexToBytes()
        val passwordChars = password.toCharArray()
        val actualHash = pbkdf2(passwordChars, salt)
        passwordChars.fill('0')
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return hash
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val bytes = ByteArray(length / 2)
        for (i in bytes.indices) {
            bytes[i] = Integer.parseInt(substring(i * 2, i * 2 + 2), 16).toByte()
        }
        return bytes
    }
}

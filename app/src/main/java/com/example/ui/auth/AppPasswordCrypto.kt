package com.example.ui.auth

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing and constant-time verification for workspace App Passwords.
 * Never stores or logs plaintext App Passwords.
 */
object AppPasswordCrypto {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val PREFIX_SHA256 = "pbkdf2_sha256$"
    private const val PREFIX_SHA1 = "pbkdf2_sha1$"

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hashAppPassword(password: String, saltHex: String): String {
        val salt = hexToBytes(saltHex)
        return try {
            val bytes = pbkdf2("PBKDF2WithHmacSHA256", password, salt)
            PREFIX_SHA256 + bytes.toHex()
        } catch (_: NoSuchAlgorithmException) {
            val bytes = pbkdf2("PBKDF2WithHmacSHA1", password, salt)
            PREFIX_SHA1 + bytes.toHex()
        }
    }

    fun verifyAppPassword(password: String, saltHex: String, storedHash: String): Boolean {
        if (password.isEmpty() || saltHex.isBlank() || storedHash.isBlank()) return false
        val salt = hexToBytes(saltHex)
        val (algo, expectedHex) = when {
            storedHash.startsWith(PREFIX_SHA256) -> "PBKDF2WithHmacSHA256" to storedHash.removePrefix(PREFIX_SHA256)
            storedHash.startsWith(PREFIX_SHA1) -> "PBKDF2WithHmacSHA1" to storedHash.removePrefix(PREFIX_SHA1)
            else -> return false
        }
        return try {
            val computed = pbkdf2(algo, password, salt)
            val expected = hexToBytes(expectedHex)
            MessageDigest.isEqual(computed, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(algorithm: String, password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(algorithm)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return ByteArray(0)
        return ByteArray(clean.length / 2) { idx ->
            clean.substring(idx * 2, idx * 2 + 2).toIntOrNull(16)?.toByte() ?: 0
        }
    }
}

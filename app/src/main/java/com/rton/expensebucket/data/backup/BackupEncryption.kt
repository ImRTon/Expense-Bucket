package com.rton.expensebucket.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object BackupEncryption {
    const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 600_000
    private val signature = byteArrayOf(69, 66, 75, 1)
    private val headerSize = signature.size + SALT_BYTES + NONCE_BYTES
    val maxFileBytes = MAX_PAYLOAD_BYTES + headerSize + TAG_BITS / 8
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        require(salt.size == SALT_BYTES)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    fun salt(file: ByteArray): ByteArray {
        require(file.size in (headerSize + TAG_BITS / 8)..maxFileBytes)
        require(file.copyOfRange(0, signature.size).contentEquals(signature))
        return file.copyOfRange(signature.size, signature.size + SALT_BYTES)
    }

    fun encrypt(payload: ByteArray, salt: ByteArray, key: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_BYTES && salt.size == SALT_BYTES)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = signature + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(payload)
    }

    fun decrypt(file: ByteArray, key: ByteArray): ByteArray {
        salt(file)
        val nonce = file.copyOfRange(headerSize - NONCE_BYTES, headerSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(file, 0, headerSize)
        return cipher.doFinal(file, headerSize, file.size - headerSize)
    }
}

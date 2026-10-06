package com.mahava.app.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Field/file encryption via Android Keystore AES-GCM for on-device secrets,
 * and PBKDF2WithHmacSHA256 + AES-GCM for portable password-based backups.
 * SQLCipher was not available in the local Gradle cache; this is the chosen alternative.
 */
class CryptoManager(
    private val keyAlias: String = "mahava_aes_gcm_v1"
) {
    companion object {
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
        const val SALT_BYTES = 16
        const val PBKDF2_ITERATIONS = 120_000
        const val KEY_BYTES = 32
        const val BACKUP_MAGIC = "MAHAVABK1"
    }

    fun encryptWithKeystore(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        return iv to ciphertext
    }

    fun decryptWithKeystore(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptBackup(password: CharArray, plain: ByteArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        // format: magic|iter int|salt|iv|ciphertext
        val magic = BACKUP_MAGIC.toByteArray(Charsets.US_ASCII)
        val iter = intToBytes(PBKDF2_ITERATIONS)
        return magic + iter + salt + iv + ct
    }

    fun decryptBackup(password: CharArray, blob: ByteArray): ByteArray {
        val magic = BACKUP_MAGIC.toByteArray(Charsets.US_ASCII)
        require(blob.size > magic.size + 4 + SALT_BYTES + IV_BYTES) { "corrupt" }
        require(blob.copyOfRange(0, magic.size).contentEquals(magic)) { "bad_magic" }
        var o = magic.size
        val iter = bytesToInt(blob, o); o += 4
        val salt = blob.copyOfRange(o, o + SALT_BYTES); o += SALT_BYTES
        val iv = blob.copyOfRange(o, o + IV_BYTES); o += IV_BYTES
        val ct = blob.copyOfRange(o, blob.size)
        val key = deriveKey(password, salt, iter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= 28) {
            builder.setUnlockedDeviceRequired(true)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun intToBytes(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun bytesToInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xff) shl 24) or
            ((b[offset + 1].toInt() and 0xff) shl 16) or
            ((b[offset + 2].toInt() and 0xff) shl 8) or
            (b[offset + 3].toInt() and 0xff)
}

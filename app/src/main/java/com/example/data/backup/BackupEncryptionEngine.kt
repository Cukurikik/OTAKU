package com.example.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryptionEngine {

    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_SIZE_BYTES = 12
    const val TAG_SIZE_BITS = 128
    const val SALT_SIZE_BYTES = 16
    const val PBKDF2_ITERATIONS = 65536
    const val KEY_SIZE_BITS = 256

    val MAGIC_BYTES = byteArrayOf('O'.code.toByte(), 'T'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte())
    const val CURRENT_VERSION: Byte = 0x01

    const val MODE_PASSPHRASE: Byte = 0x01
    const val MODE_KEYSTORE: Byte = 0x02
    const val MODE_SYSTEM_DEFAULT: Byte = 0x03

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "OtakuHub_Vault_Master_Key"

    fun encryptBackup(dataJson: String, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val encryptedData = cipher.doFinal(dataJson.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(iv.size + encryptedData.size)
        buffer.put(iv)
        buffer.put(encryptedData)
        return buffer.array()
    }

    fun decryptBackup(encryptedBytes: ByteArray, secretKey: SecretKey): String {
        if (encryptedBytes.size < IV_SIZE_BYTES + (TAG_SIZE_BITS / 8)) {
            throw IllegalArgumentException("Data cadangan terenkripsi tidak valid atau terpotong.")
        }
        val buffer = ByteBuffer.wrap(encryptedBytes)
        val iv = ByteArray(IV_SIZE_BYTES)
        buffer.get(iv)

        val encryptedData = ByteArray(buffer.remaining())
        buffer.get(encryptedData)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = try {
            cipher.doFinal(encryptedData)
        } catch (e: AEADBadTagException) {
            throw SecurityException("Kata sandi salah atau berkas cadangan rusak (Integritas GCM gagal).", e)
        }
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun deriveKeyFromPassphrase(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): SecretKey {
        val keySpec = PBEKeySpec(passphrase, salt, iterations, KEY_SIZE_BITS)
        val keyFactory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: Exception) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val secretBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(secretBytes, "AES")
    }

    fun getOrCreateKeyStoreKey(context: Context): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
            val builder = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (e: Throwable) {
            getFallbackSystemKey(context)
        }
    }

    fun getFallbackSystemKey(context: Context, salt: ByteArray? = null): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(context.packageName.toByteArray(Charsets.UTF_8))
        digest.update("OtakuHub.HardwareVaultSecret.AES256GCM".toByteArray(Charsets.UTF_8))
        if (salt != null) {
            digest.update(salt)
        }
        val keyBytes = digest.digest()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun writeContainer(
        outputStream: OutputStream,
        dataJson: String,
        mode: Byte,
        passphrase: String? = null,
        context: Context
    ) {
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)

        val secretKey: SecretKey = when (mode) {
            MODE_PASSPHRASE -> {
                require(!passphrase.isNullOrEmpty()) { "Passphrase wajib diisi untuk mode enkripsi kata sandi." }
                deriveKeyFromPassphrase(passphrase.toCharArray(), salt)
            }
            MODE_KEYSTORE -> {
                getOrCreateKeyStoreKey(context)
            }
            else -> {
                getFallbackSystemKey(context, salt)
            }
        }

        val encryptedPayload = encryptBackup(dataJson, secretKey)

        outputStream.write(MAGIC_BYTES)
        outputStream.write(byteArrayOf(CURRENT_VERSION, mode))
        outputStream.write(salt)
        outputStream.write(encryptedPayload)
        outputStream.flush()
    }

    data class ContainerHeader(
        val version: Byte,
        val mode: Byte,
        val salt: ByteArray
    )

    fun readContainerHeader(inputStream: InputStream): ContainerHeader? {
        val magic = ByteArray(4)
        val read = inputStream.read(magic)
        if (read != 4 || !magic.contentEquals(MAGIC_BYTES)) {
            return null
        }
        val version = inputStream.read()
        val mode = inputStream.read()
        if (version < 0 || mode < 0) return null

        val salt = ByteArray(SALT_SIZE_BYTES)
        val saltRead = inputStream.read(salt)
        if (saltRead != SALT_SIZE_BYTES) return null

        return ContainerHeader(version.toByte(), mode.toByte(), salt)
    }

    fun readAndDecryptContainer(
        inputStream: InputStream,
        passphrase: String? = null,
        context: Context
    ): String {
        val header = readContainerHeader(inputStream)
            ?: throw IllegalArgumentException("Format berkas bukan kontainer .otakubackup yang sah.")

        val secretKey: SecretKey = when (header.mode) {
            MODE_PASSPHRASE -> {
                if (passphrase.isNullOrEmpty()) {
                    throw PassphraseRequiredException("Berkas ini dilindungi kata sandi. Silakan masukkan kata sandi.")
                }
                deriveKeyFromPassphrase(passphrase.toCharArray(), header.salt)
            }
            MODE_KEYSTORE -> {
                getOrCreateKeyStoreKey(context)
            }
            MODE_SYSTEM_DEFAULT -> {
                getFallbackSystemKey(context, header.salt)
            }
            else -> {
                throw SecurityException("Mode enkripsi berkas tidak dikenal: ${header.mode}")
            }
        }

        val payload = inputStream.readBytes()
        return decryptBackup(payload, secretKey)
    }
}

class PassphraseRequiredException(message: String) : SecurityException(message)

package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Robust, standalone AES-256 encrypted SharedPreferences fallback for environments
 * where AndroidKeyStore is unavailable or broken (Non-GMS devices, custom ROMs,
 * emulator environments, Robolectric JVM unit tests).
 */
class AesEncryptedPreferences private constructor(
    private val context: Context,
    private val prefName: String
) : SharedPreferences {

    private val delegate: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val secretKey: SecretKeySpec = getOrCreateSecretKey()

    companion object {
        private const val PREFIX = "ENC_AES:"
        private const val CIPHER_ALGO = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGO = "AES"
        private const val KEY_FILE_NAME = ".sec_prefs_key"

        fun create(context: Context, prefName: String = "otakuhub_secure_fallback"): AesEncryptedPreferences {
            return AesEncryptedPreferences(context.applicationContext ?: context, prefName)
        }
    }

    private fun getOrCreateSecretKey(): SecretKeySpec {
        return try {
            val keyFile = File(context.filesDir, KEY_FILE_NAME)
            val keyBytes = if (keyFile.exists() && keyFile.length() == 32L) {
                keyFile.readBytes()
            } else {
                val newBytes = ByteArray(32)
                SecureRandom().nextBytes(newBytes)
                try {
                    FileOutputStream(keyFile).use { it.write(newBytes) }
                } catch (e: Exception) {
                    // Fallback to deterministic SHA-256 derivation if disk writing fails
                    val digest = MessageDigest.getInstance("SHA-256")
                    return SecretKeySpec(
                        digest.digest("${context.packageName}.secure.salt.fallback".toByteArray(StandardCharsets.UTF_8)),
                        KEY_ALGO
                    )
                }
                newBytes
            }
            SecretKeySpec(keyBytes, KEY_ALGO)
        } catch (e: Exception) {
            val digest = MessageDigest.getInstance("SHA-256")
            val fallbackBytes = digest.digest("${context.packageName}.secure.key.fallback".toByteArray(StandardCharsets.UTF_8))
            SecretKeySpec(fallbackBytes, KEY_ALGO)
        }
    }

    private fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun decrypt(cipherTextWithPrefix: String): String {
        if (!cipherTextWithPrefix.startsWith(PREFIX)) return cipherTextWithPrefix
        return try {
            val rawBase64 = cipherTextWithPrefix.removePrefix(PREFIX)
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            if (combined.size <= 16) return cipherTextWithPrefix
            val iv = ByteArray(16)
            val cipherBytes = ByteArray(combined.size - 16)
            System.arraycopy(combined, 0, iv, 0, 16)
            System.arraycopy(combined, 16, cipherBytes, 0, cipherBytes.size)

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            cipherTextWithPrefix
        }
    }

    override fun getAll(): MutableMap<String, *> {
        val rawMap = delegate.all
        val resultMap = mutableMapOf<String, Any?>()
        for ((key, value) in rawMap) {
            if (value is String) {
                resultMap[key] = decrypt(value)
            } else {
                resultMap[key] = value
            }
        }
        return resultMap
    }

    override fun getString(key: String, defValue: String?): String? {
        val raw = delegate.getString(key, null) ?: return defValue
        return decrypt(raw)
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val rawSet = delegate.getStringSet(key, null) ?: return defValues
        return rawSet.map { decrypt(it) }.toMutableSet()
    }

    override fun getInt(key: String, defValue: Int): Int {
        val raw = delegate.getString(key, null) ?: return defValue
        return decrypt(raw).toIntOrNull() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val raw = delegate.getString(key, null) ?: return defValue
        return decrypt(raw).toLongOrNull() ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val raw = delegate.getString(key, null) ?: return defValue
        return decrypt(raw).toFloatOrNull() ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val raw = delegate.getString(key, null) ?: return defValue
        return decrypt(raw).toBooleanStrictOrNull() ?: defValue
    }

    override fun contains(key: String): Boolean {
        return delegate.contains(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return EditorImpl(delegate.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    inner class EditorImpl(private val editorDelegate: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                editorDelegate.remove(key)
            } else {
                editorDelegate.putString(key, encrypt(value))
            }
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            if (values == null) {
                editorDelegate.remove(key)
            } else {
                val encryptedSet = values.map { encrypt(it) }.toSet()
                editorDelegate.putStringSet(key, encryptedSet)
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            editorDelegate.putString(key, encrypt(value.toString()))
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            editorDelegate.putString(key, encrypt(value.toString()))
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            editorDelegate.putString(key, encrypt(value.toString()))
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            editorDelegate.putString(key, encrypt(value.toString()))
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            editorDelegate.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            editorDelegate.clear()
            return this
        }

        override fun commit(): Boolean {
            return editorDelegate.commit()
        }

        override fun apply() {
            editorDelegate.apply()
        }
    }
}

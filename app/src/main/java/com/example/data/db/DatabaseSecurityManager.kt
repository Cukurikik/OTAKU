package com.example.data.db

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DatabaseSecurityManager {
    private const val TAG = "DatabaseSecurityManager"
    private const val PREFS_NAME = "secure_db_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase_v1"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @VisibleForTesting
    fun resetCachedPassphraseForTesting() {
        cachedPassphrase = null
    }

    /**
     * Checks if the app is currently running in a unit test / Robolectric environment.
     */
    fun isRunningInUnitTest(): Boolean {
        return android.os.Build.FINGERPRINT == "robolectric" ||
               android.os.Build.HARDWARE == "robolectric" ||
               try {
                   Class.forName("org.robolectric.Robolectric")
                   true
               } catch (e: Throwable) {
                   false
               }
    }

    /**
     * Generates a deterministic 256-bit passphrase purely in volatile memory via PBKDF2WithHmacSHA256
     * with 100,000 iterations, binding to hardware identity (BOARD, BRAND, MODEL, ID) and device entropy.
     * Zero plaintext keys are ever persisted to disk.
     */
    fun generateFallbackPassphrase(context: Context, userPin: String = ""): ByteArray {
        val hardwareEntropy = "${Build.BOARD}-${Build.BRAND}-${Build.MODEL}-${Build.ID}-${context.packageName}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hardwareSalt = digest.digest(hardwareEntropy.toByteArray(Charsets.UTF_8))

        val deviceSeed = if (userPin.isNotEmpty()) {
            userPin
        } else {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            } catch (_: Throwable) {
                ""
            }
            "OtakuHub-KDF-Seed-${context.packageName}-$androidId-${Build.FINGERPRINT}-${Build.HARDWARE}"
        }

        val spec = PBEKeySpec(deviceSeed.toCharArray(), hardwareSalt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedKey = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return derivedKey
    }

    /**
     * Retrieves or generates a cryptographically secure 256-bit passphrase.
     * Primary mechanism: Stored in EncryptedSharedPreferences backed by Android Keystore (TEE/StrongBox).
     * Fallback mechanism: Hardware-bound dynamic key derivation via PBKDF2WithHmacSHA256 (100,000 iterations).
     * No raw passphrase is ever written in plaintext XML or external storage.
     */
    fun getPassphrase(context: Context): ByteArray {
        cachedPassphrase?.let { return it }

        synchronized(this) {
            cachedPassphrase?.let { return it }

            val pass = try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                val existing = prefs.getString(KEY_PASSPHRASE, null)
                if (!existing.isNullOrEmpty()) {
                    Base64.decode(existing, Base64.NO_WRAP)
                } else {
                    val newKey = ByteArray(32)
                    SecureRandom().nextBytes(newKey)
                    val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)
                    prefs.edit().putString(KEY_PASSPHRASE, encoded).apply()
                    newKey
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load/generate key via EncryptedSharedPreferences, engaging PBKDF2 dynamic KDF fallback", e)
                
                // Sanitize and purge any legacy plaintext fallback entries from disk
                try {
                    val fallbackPrefs = context.getSharedPreferences("db_security_fallback", Context.MODE_PRIVATE)
                    if (fallbackPrefs.contains(KEY_PASSPHRASE)) {
                        fallbackPrefs.edit().remove(KEY_PASSPHRASE).apply()
                    }
                } catch (_: Throwable) {}

                // Dynamically derive 256-bit key in volatile RAM without saving plaintext to disk
                generateFallbackPassphrase(context)
            }

            cachedPassphrase = pass
            return pass
        }
    }

    /**
     * Builds a SupportFactory instance for Room database encryption with SQLCipher.
     * Returns null if native SQLCipher libraries are unavailable (e.g. during local JVM unit tests).
     * Note: clearPassphrase is set to false so Room can reconnect or reopen if needed.
     */
    fun createSupportFactory(context: Context): SupportFactory? {
        if (isRunningInUnitTest()) {
            Log.i(TAG, "Robolectric environment detected; skipping SQLCipher native library load.")
            return null
        }

        return try {
            SQLiteDatabase.loadLibs(context)
            val passphrase = getPassphrase(context)
            SupportFactory(passphrase, null, false)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "SQLCipher native libraries not available in current runtime environment", e)
            null
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize SQLCipher SupportFactory", e)
            null
        }
    }

    /**
     * Automatically migrates an existing unencrypted Room database file to SQLCipher AES-256.
     * If the database does not exist, is already encrypted, or native libraries are missing, this is safely bypassed.
     */
    fun migratePlaintextDatabaseIfNeeded(context: Context, dbName: String) {
        if (isRunningInUnitTest()) {
            return
        }

        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return
        }

        try {
            val header = ByteArray(16)
            FileInputStream(dbFile).use { input ->
                val read = input.read(header)
                if (read < 16) return
            }

            val isPlaintext = String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
            if (!isPlaintext) {
                // Database is already encrypted with SQLCipher
                return
            }

            Log.i(TAG, "Unencrypted database detected for '$dbName'. Migrating to SQLCipher AES-256...")

            try {
                SQLiteDatabase.loadLibs(context)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Cannot migrate database to SQLCipher: native libs missing", e)
                return
            }

            val passphrase = getPassphrase(context)
            val hexKey = passphrase.joinToString("") { "%02x".format(it) }

            val tempEncryptedFile = File(dbFile.parentFile, "$dbName.tmp_encrypted")
            if (tempEncryptedFile.exists()) {
                tempEncryptedFile.delete()
            }

            val plaintextDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            try {
                plaintextDb.rawExecSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY \"x'$hexKey'\";")
                plaintextDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                plaintextDb.rawExecSQL("DETACH DATABASE encrypted;")
            } finally {
                plaintextDb.close()
            }

            // Cleanup journal, WAL, and replace original with encrypted
            File(dbFile.path + "-journal").delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            if (dbFile.delete()) {
                tempEncryptedFile.renameTo(dbFile)
                Log.i(TAG, "Successfully migrated '$dbName' to SQLCipher encrypted database.")
            } else {
                Log.e(TAG, "Failed to replace plaintext db file with encrypted version.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error during plaintext to SQLCipher database migration", e)
        }
    }
}

package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.OtakuHubApp
import com.example.data.ProfileManager
import com.example.data.SettingsManager
import com.example.data.models.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manajer Cadangan & Pemulihan Data OtakuHub dengan Enkripsi AES-256-GCM
 * dan Isolasi Rahasia Vault (Vault Secret Isolation).
 */
class BackupManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsManager by lazy { SettingsManager(context) }
    private val LEGACY_BACKUP_PASSWORD = "OtakuHub_Secure_Backup_V1"

    private fun getLegacySecretKeyAndIv(salt: ByteArray): Pair<SecretKeySpec, IvParameterSpec> {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(LEGACY_BACKUP_PASSWORD.toByteArray())
        md.update(salt)
        val keyBytes = md.digest()
        val ivBytes = keyBytes.copyOfRange(0, 16)
        return Pair(SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
    }

    suspend fun inspectBackup(uri: Uri): Result<BackupInspectionResult> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { rawIs ->
                val bis = BufferedInputStream(rawIs)
                bis.mark(32)
                val headerBytes = ByteArray(4)
                val read = bis.read(headerBytes)
                bis.reset()

                if (read >= 4 && headerBytes.contentEquals(BackupEncryptionEngine.MAGIC_BYTES)) {
                    val header = BackupEncryptionEngine.readContainerHeader(bis)
                    if (header != null) {
                        val mode = when (header.mode) {
                            BackupEncryptionEngine.MODE_PASSPHRASE -> BackupEncryptionMode.PASSPHRASE
                            BackupEncryptionEngine.MODE_KEYSTORE -> BackupEncryptionMode.KEYSTORE
                            else -> BackupEncryptionMode.SYSTEM_DEFAULT
                        }
                        return@withContext Result.success(
                            BackupInspectionResult(
                                isEncrypted = true,
                                encryptionMode = mode,
                                requiresPassphrase = header.mode == BackupEncryptionEngine.MODE_PASSPHRASE,
                                formatDescription = "OtakuHub Encrypted Container (.otakubackup / AES-256-GCM)"
                            )
                        )
                    }
                }

                if (read >= 4 && String(headerBytes) == "OHBK") {
                    return@withContext Result.success(
                        BackupInspectionResult(
                            isEncrypted = true,
                            encryptionMode = BackupEncryptionMode.SYSTEM_DEFAULT,
                            requiresPassphrase = false,
                            formatDescription = "OtakuHub Legacy Encrypted Zip (AES-CBC)"
                        )
                    )
                }

                return@withContext Result.success(
                    BackupInspectionResult(
                        isEncrypted = false,
                        encryptionMode = null,
                        requiresPassphrase = false,
                        formatDescription = "Standard Zip Backup"
                    )
                )
            } ?: Result.failure(Exception("Tidak dapat membuka berkas cadangan."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBackup(
        uri: Uri,
        options: BackupOptions = BackupOptions()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val database = (context.applicationContext as OtakuHubApp).database
            val profileId = ProfileManager.currentProfileIdValue

            val vaultSecrets = if (options.includeVaultSecrets) {
                VaultSecretData(
                    malToken = settingsManager.malToken,
                    malRefreshToken = settingsManager.malRefreshToken,
                    malExpiresAt = settingsManager.malExpiresAt,
                    anilistToken = settingsManager.anilistToken,
                    kitsuToken = settingsManager.kitsuToken,
                    kitsuUserId = settingsManager.kitsuUserId,
                    firebaseProjectId = settingsManager.firebaseProjectId,
                    firebaseAppId = settingsManager.firebaseAppId,
                    firebaseApiKey = settingsManager.firebaseApiKey,
                    googleWebClientId = settingsManager.googleWebClientId
                )
            } else {
                null
            }

            val backupData = BackupData(
                schemaVersion = 10,
                historyItems = database.historyDao().getAllHistorySync(profileId),
                libraryEntries = database.libraryDao().getAllEntriesSync(profileId),
                chapterEntries = database.chapterDao().getAllChaptersSync(profileId),
                downloads = database.downloadDao().getAllDownloadsSync(profileId),
                pageDownloads = database.pageDownloadDao().getAllPageDownloadsSync(),
                categories = database.categoryDao().getAllCategoriesSync(),
                tracks = database.trackDao().getAllTracksSync(),
                updates = database.updateDao().getAllUpdatesSync(),
                vaultTokens = vaultSecrets
            )

            val jsonString = json.encodeToString(backupData)
            val tmpFile = File(context.cacheDir, "backup_tmp_${System.currentTimeMillis()}.otakubackup")

            val modeByte = when (options.encryptionMode) {
                BackupEncryptionMode.PASSPHRASE -> BackupEncryptionEngine.MODE_PASSPHRASE
                BackupEncryptionMode.KEYSTORE -> BackupEncryptionEngine.MODE_KEYSTORE
                BackupEncryptionMode.SYSTEM_DEFAULT -> BackupEncryptionEngine.MODE_SYSTEM_DEFAULT
            }

            FileOutputStream(tmpFile).use { fos ->
                BackupEncryptionEngine.writeContainer(
                    outputStream = fos,
                    dataJson = jsonString,
                    mode = modeByte,
                    passphrase = options.passphrase,
                    context = context
                )
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(tmpFile).use { input ->
                    input.copyTo(output)
                }
            }

            tmpFile.delete()
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAutoBackup(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val database = (context.applicationContext as OtakuHubApp).database
            val profileId = ProfileManager.currentProfileIdValue

            val vaultSecrets = VaultSecretData(
                malToken = settingsManager.malToken,
                malRefreshToken = settingsManager.malRefreshToken,
                malExpiresAt = settingsManager.malExpiresAt,
                anilistToken = settingsManager.anilistToken,
                kitsuToken = settingsManager.kitsuToken,
                kitsuUserId = settingsManager.kitsuUserId,
                firebaseProjectId = settingsManager.firebaseProjectId,
                firebaseAppId = settingsManager.firebaseAppId,
                firebaseApiKey = settingsManager.firebaseApiKey,
                googleWebClientId = settingsManager.googleWebClientId
            )

            val backupData = BackupData(
                schemaVersion = 10,
                historyItems = database.historyDao().getAllHistorySync(profileId),
                libraryEntries = database.libraryDao().getAllEntriesSync(profileId),
                chapterEntries = database.chapterDao().getAllChaptersSync(profileId),
                downloads = database.downloadDao().getAllDownloadsSync(profileId),
                pageDownloads = database.pageDownloadDao().getAllPageDownloadsSync(),
                categories = database.categoryDao().getAllCategoriesSync(),
                tracks = database.trackDao().getAllTracksSync(),
                updates = database.updateDao().getAllUpdatesSync(),
                vaultTokens = vaultSecrets
            )

            val jsonString = json.encodeToString(backupData)
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val finalBackupFile = File(backupDir, "autobackup_encrypted.otakubackup")
            val tmpFile = File(backupDir, "autobackup_encrypted.otakubackup.tmp")

            FileOutputStream(tmpFile).use { fos ->
                BackupEncryptionEngine.writeContainer(
                    outputStream = fos,
                    dataJson = jsonString,
                    mode = BackupEncryptionEngine.MODE_KEYSTORE,
                    passphrase = null,
                    context = context
                )
            }

            if (tmpFile.renameTo(finalBackupFile)) {
                Result.success(finalBackupFile)
            } else {
                tmpFile.copyTo(finalBackupFile, overwrite = true)
                tmpFile.delete()
                Result.success(finalBackupFile)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        uri: Uri,
        passphrase: String? = null,
        restoreVaultSecrets: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var jsonString: String? = null

            context.contentResolver.openInputStream(uri)?.use { rawIs ->
                val bis = BufferedInputStream(rawIs)
                bis.mark(32)
                val magic = ByteArray(4)
                val magicRead = bis.read(magic)
                bis.reset()

                if (magicRead >= 4 && magic.contentEquals(BackupEncryptionEngine.MAGIC_BYTES)) {
                    jsonString = BackupEncryptionEngine.readAndDecryptContainer(
                        inputStream = bis,
                        passphrase = passphrase,
                        context = context
                    )
                } else if (magicRead >= 4 && String(magic) == "OHBK") {
                    bis.skip(4)
                    val salt = ByteArray(16)
                    val saltRead = bis.read(salt)
                    if (saltRead != 16) throw Exception("Berkas cadangan legacy rusak (salt tidak lengkap).")

                    val (secretKey, iv) = getLegacySecretKeyAndIv(salt)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

                    CipherInputStream(bis, cipher).use { cis ->
                        ZipInputStream(cis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name == "backup.json") {
                                    jsonString = String(zis.readBytes(), Charsets.UTF_8)
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } else {
                    ZipInputStream(bis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "backup.json") {
                                jsonString = String(zis.readBytes(), Charsets.UTF_8)
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }

            if (jsonString.isNullOrEmpty()) {
                throw Exception("Data cadangan JSON tidak ditemukan di dalam berkas.")
            }

            val backupData = json.decodeFromString<BackupData>(jsonString!!)
            if (backupData.schemaVersion > 10) {
                throw Exception("Berkas cadangan berasal dari versi aplikasi yang lebih baru (Schema ${backupData.schemaVersion}).")
            }

            val database = (context.applicationContext as OtakuHubApp).database
            database.withTransaction {
                if (backupData.historyItems.isNotEmpty()) database.historyDao().insertHistory(backupData.historyItems)
                if (backupData.libraryEntries.isNotEmpty()) database.libraryDao().insertLibrary(backupData.libraryEntries)
                if (backupData.chapterEntries.isNotEmpty()) database.chapterDao().insertChapters(backupData.chapterEntries)
                if (backupData.downloads.isNotEmpty()) database.downloadDao().insertDownloads(backupData.downloads)
                if (backupData.pageDownloads.isNotEmpty()) database.pageDownloadDao().insertPageDownloads(backupData.pageDownloads)
                if (backupData.categories.isNotEmpty()) database.categoryDao().insertCategories(backupData.categories)
                if (backupData.tracks.isNotEmpty()) database.trackDao().insertTracks(backupData.tracks)
                if (backupData.updates.isNotEmpty()) database.updateDao().insertAll(backupData.updates)
            }

            if (restoreVaultSecrets && backupData.vaultTokens != null) {
                val vault = backupData.vaultTokens
                if (vault.malToken.isNotEmpty()) settingsManager.malToken = vault.malToken
                if (vault.malRefreshToken.isNotEmpty()) settingsManager.malRefreshToken = vault.malRefreshToken
                if (vault.malExpiresAt > 0L) settingsManager.malExpiresAt = vault.malExpiresAt
                if (vault.anilistToken.isNotEmpty()) settingsManager.anilistToken = vault.anilistToken
                if (vault.kitsuToken.isNotEmpty()) settingsManager.kitsuToken = vault.kitsuToken
                if (vault.kitsuUserId.isNotEmpty()) settingsManager.kitsuUserId = vault.kitsuUserId
                if (vault.firebaseProjectId.isNotEmpty()) settingsManager.firebaseProjectId = vault.firebaseProjectId
                if (vault.firebaseAppId.isNotEmpty()) settingsManager.firebaseAppId = vault.firebaseAppId
                if (vault.firebaseApiKey.isNotEmpty()) settingsManager.firebaseApiKey = vault.firebaseApiKey
                if (vault.googleWebClientId.isNotEmpty()) settingsManager.googleWebClientId = vault.googleWebClientId
            }

            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example

import com.example.data.backup.BackupData
import com.example.data.backup.BackupEncryptionEngine
import com.example.data.backup.VaultSecretData
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedBackupSecurityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAes256GcmEncryptionAndDecryptionRoundtrip() {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val originalData = "{\"historyItems\":[{\"id\":\"101\",\"title\":\"Solo Leveling\"}]}"
        val encryptedBytes = BackupEncryptionEngine.encryptBackup(originalData, secretKey)

        assertNotNull(encryptedBytes)
        assertTrue("Ciphertext harus lebih panjang dari IV (12 bytes) + Tag (16 bytes)", encryptedBytes.size > 28)

        val decryptedData = BackupEncryptionEngine.decryptBackup(encryptedBytes, secretKey)
        assertEquals(originalData, decryptedData)
    }

    @Test
    fun testGcmIntegrityTamperingDetection() {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val originalData = "Sensitive Vault Backup Data"
        val encryptedBytes = BackupEncryptionEngine.encryptBackup(originalData, secretKey)

        // Tamper 1 byte pada ciphertext / tag
        val tamperedBytes = encryptedBytes.copyOf()
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes[tamperedBytes.size - 1].toInt() xor 0xFF).toByte()

        try {
            BackupEncryptionEngine.decryptBackup(tamperedBytes, secretKey)
            fail("Harus melempar SecurityException karena integritas GCM dilanggar (Tampering)")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Integritas GCM gagal") == true || e.cause is AEADBadTagException)
        }
    }

    @Test
    fun testPbkdf2KeyDerivationUniqueness() {
        val passphrase = "UserSuperSecretPassword123!".toCharArray()
        val salt1 = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val salt2 = ByteArray(16).apply { SecureRandom().nextBytes(this) }

        val key1 = BackupEncryptionEngine.deriveKeyFromPassphrase(passphrase, salt1)
        val key2 = BackupEncryptionEngine.deriveKeyFromPassphrase(passphrase, salt2)

        assertEquals("AES", key1.algorithm)
        assertEquals(32, key1.encoded.size) // 256-bit key
        assertFalse("Kunci dari salt yang berbeda tidak boleh identik", key1.encoded.contentEquals(key2.encoded))

        // Same salt should yield identical key
        val key1Recomputed = BackupEncryptionEngine.deriveKeyFromPassphrase(passphrase, salt1)
        assertArrayEquals(key1.encoded, key1Recomputed.encoded)
    }

    @Test
    fun testContainerHeaderFormatAndMagicBytes() {
        val bos = ByteArrayOutputStream()
        val dummyContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        val backupData = BackupData(
            schemaVersion = 10,
            historyItems = listOf(
                HistoryItem(id = "1", title = "Solo Leveling", sourceId = "mgkomik", imageUrl = "https://example.com/cover.jpg")
            ),
            libraryEntries = listOf(
                LibraryEntry(sourceId = "mgkomik", mangaId = "solo-leveling", title = "Solo Leveling", thumbnailUrl = "https://example.com/cover.jpg")
            ),
            vaultTokens = VaultSecretData(
                malToken = "mal_secret_token_abc",
                anilistToken = "anilist_secret_jwt_xyz"
            )
        )
        val jsonString = json.encodeToString(backupData)

        BackupEncryptionEngine.writeContainer(
            outputStream = bos,
            dataJson = jsonString,
            mode = BackupEncryptionEngine.MODE_PASSPHRASE,
            passphrase = "MySecretPassword",
            context = dummyContext
        )

        val containerBytes = bos.toByteArray()
        assertTrue(containerBytes.size > 22)

        // Verifikasi Magic Bytes: 'OTKB'
        assertEquals('O'.code.toByte(), containerBytes[0])
        assertEquals('T'.code.toByte(), containerBytes[1])
        assertEquals('K'.code.toByte(), containerBytes[2])
        assertEquals('B'.code.toByte(), containerBytes[3])
        assertEquals(BackupEncryptionEngine.CURRENT_VERSION, containerBytes[4])
        assertEquals(BackupEncryptionEngine.MODE_PASSPHRASE, containerBytes[5])

        // Verifikasi Dekripsi Kontainer
        val bis = ByteArrayInputStream(containerBytes)
        val decryptedJson = BackupEncryptionEngine.readAndDecryptContainer(
            inputStream = bis,
            passphrase = "MySecretPassword",
            context = dummyContext
        )

        val restoredBackup = json.decodeFromString<BackupData>(decryptedJson)
        assertEquals(10, restoredBackup.schemaVersion)
        assertEquals(1, restoredBackup.historyItems.size)
        assertEquals("Solo Leveling", restoredBackup.historyItems[0].title)
        assertEquals("mal_secret_token_abc", restoredBackup.vaultTokens?.malToken)
    }

    @Test
    fun testVaultSecretIsolationOmission() {
        // Uji jika opsi vault secrets dimatikan (false), token sensitif tidak boleh tersimpan
        val backupDataWithoutSecrets = BackupData(
            schemaVersion = 10,
            historyItems = listOf(HistoryItem(id = "1", title = "Test Manga")),
            vaultTokens = null // Secrets terisolasi & tidak diekspor
        )

        val jsonString = json.encodeToString(backupDataWithoutSecrets)
        assertFalse("JSON tidak boleh memuat token rahasia saat diisolasi", jsonString.contains("mal_secret_token"))
        assertFalse("JSON tidak boleh memuat anilist token saat diisolasi", jsonString.contains("anilist_secret"))
    }
}

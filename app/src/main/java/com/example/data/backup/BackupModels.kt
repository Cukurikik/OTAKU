package com.example.data.backup

import com.example.data.models.*
import kotlinx.serialization.Serializable

@Serializable
data class VaultSecretData(
    val malToken: String = "",
    val malRefreshToken: String = "",
    val malExpiresAt: Long = 0L,
    val anilistToken: String = "",
    val kitsuToken: String = "",
    val kitsuUserId: String = "",
    val firebaseProjectId: String = "",
    val firebaseAppId: String = "",
    val firebaseApiKey: String = "",
    val googleWebClientId: String = ""
)

@Serializable
data class BackupData(
    val schemaVersion: Int = 10,
    val historyItems: List<HistoryItem> = emptyList(),
    val libraryEntries: List<LibraryEntry> = emptyList(),
    val chapterEntries: List<ChapterEntry> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val pageDownloads: List<PageDownloadEntity> = emptyList(),
    val categories: List<CategoryEntry> = emptyList(),
    val tracks: List<TrackEntity> = emptyList(),
    val updates: List<UpdateEntity> = emptyList(),
    val vaultTokens: VaultSecretData? = null
)

enum class BackupEncryptionMode {
    PASSPHRASE,
    KEYSTORE,
    SYSTEM_DEFAULT
}

data class BackupOptions(
    val encryptionMode: BackupEncryptionMode = BackupEncryptionMode.KEYSTORE,
    val passphrase: String? = null,
    val includeVaultSecrets: Boolean = false
)

data class BackupInspectionResult(
    val isEncrypted: Boolean,
    val encryptionMode: BackupEncryptionMode?,
    val requiresPassphrase: Boolean,
    val formatDescription: String
)

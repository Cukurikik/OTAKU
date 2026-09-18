package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.backup.BackupOptions
import com.example.data.backup.BackupInspectionResult
import java.io.File

// Backward-compatible typealias and delegation
typealias BackupData = com.example.data.backup.BackupData
typealias VaultSecretData = com.example.data.backup.VaultSecretData
typealias BackupEncryptionMode = com.example.data.backup.BackupEncryptionMode

class BackupManager(private val context: Context) {
    private val delegate = com.example.data.backup.BackupManager(context)

    suspend fun inspectBackup(uri: Uri): Result<BackupInspectionResult> {
        return delegate.inspectBackup(uri)
    }

    suspend fun createBackup(
        uri: Uri,
        options: BackupOptions = BackupOptions()
    ): Result<Unit> {
        return delegate.createBackup(uri, options)
    }

    suspend fun createAutoBackup(): Result<File> {
        return delegate.createAutoBackup()
    }

    suspend fun restoreBackup(
        uri: Uri,
        passphrase: String? = null,
        restoreVaultSecrets: Boolean = true
    ): Result<Unit> {
        return delegate.restoreBackup(uri, passphrase, restoreVaultSecrets)
    }
}

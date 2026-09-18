package com.example.ui.viewmodels

import android.app.Application
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OtakuHubApp
import com.example.data.SettingsManager
import com.example.data.sources.tachiyomi.AvailableExtension
import com.example.data.sources.tachiyomi.ExtensionManager
import com.example.data.sources.tachiyomi.ExtensionRepo
import com.example.data.sources.tachiyomi.InstalledExtension
import com.example.data.sources.tachiyomi.RepoCategory
import com.example.workers.ExtensionUpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface ExtensionsUiState {
    object Loading : ExtensionsUiState
    data class Success(
        val available: List<AvailableExtension>,
        val installed: List<InstalledExtension>,
        val updates: List<Pair<InstalledExtension, AvailableExtension>> = emptyList()
    ) : ExtensionsUiState
    data class Error(val message: String) : ExtensionsUiState
}

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OtakuHubApp
    private val settings = SettingsManager(application)

    private val _uiState = MutableStateFlow<ExtensionsUiState>(ExtensionsUiState.Loading)
    val uiState: StateFlow<ExtensionsUiState> = _uiState
    val searchQuery = MutableStateFlow("")
    val selectedLang = MutableStateFlow("id") // Default filter to ID (Indonesian)
    
    val selectedCategory = MutableStateFlow("Semua") // "Semua", "Manga", "Anime", "Custom"
    val selectedRepoId = MutableStateFlow<String?>(null) // null means all repositories
    
    private val repoManager = com.example.data.sources.tachiyomi.ExtensionRepositoryManager(application)
    
    val reposList = repoManager.repositories
    // Maps APK name -> progress (0.0 to 1.0)
    val downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val showPermissionWarning = MutableStateFlow(false)

    // Auto-Updater State & Settings
    val autoUpdateEnabled = MutableStateFlow(settings.autoUpdateExtensionsEnabled)
    val autoUpdateIntervalHours = MutableStateFlow(settings.autoUpdateExtensionsIntervalHours)
    val autoDownloadUpdates = MutableStateFlow(settings.autoDownloadExtensionUpdates)
    val lastCheckTime = MutableStateFlow(settings.lastExtensionUpdateCheckTime)
    val lastCheckResult = MutableStateFlow(settings.lastExtensionUpdateCheckResult)

    val isCheckingBackgroundUpdates: StateFlow<Boolean> = ExtensionUpdateWorker.isCheckingFlow(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isUpdatingAll = MutableStateFlow(false)

    init {
        loadExtensions()
        viewModelScope.launch {
            ExtensionManager.packageChangesFlow.collect {
                refreshInstalledStatus()
            }
        }
    }

    fun toggleAutoUpdate(enabled: Boolean) {
        autoUpdateEnabled.value = enabled
        settings.autoUpdateExtensionsEnabled = enabled
        ExtensionUpdateWorker.schedule(app)
    }

    fun setAutoUpdateInterval(hours: Int) {
        autoUpdateIntervalHours.value = hours
        settings.autoUpdateExtensionsIntervalHours = hours
        ExtensionUpdateWorker.schedule(app)
    }

    fun toggleAutoDownload(enabled: Boolean) {
        autoDownloadUpdates.value = enabled
        settings.autoDownloadExtensionUpdates = enabled
    }

    fun triggerBackgroundCheckNow() {
        ExtensionUpdateWorker.runOnce(app)
        viewModelScope.launch {
            // Give worker time to start, then refresh UI
            kotlinx.coroutines.delay(1000)
            loadExtensions()
            lastCheckTime.value = settings.lastExtensionUpdateCheckTime
            lastCheckResult.value = settings.lastExtensionUpdateCheckResult
        }
    }

    fun updateAllExtensions() {
        val currentState = _uiState.value
        if (currentState !is ExtensionsUiState.Success || currentState.updates.isEmpty()) return

        if (!canRequestPackageInstalls()) {
            showPermissionWarning.value = true
        }

        viewModelScope.launch {
            isUpdatingAll.value = true
            val updates = currentState.updates
            for ((_, remote) in updates) {
                try {
                    val apkName = remote.apk
                    val pkgName = remote.pkg
                    downloadProgress.value = downloadProgress.value + (apkName to 0f)

                    val result = ExtensionManager.downloadExtensionApk(
                        app,
                        apkName,
                        remote.repoUrl,
                        pkgName,
                        remote.code
                    ) { progress ->
                        downloadProgress.value = downloadProgress.value + (apkName to progress)
                    }

                    downloadProgress.value = downloadProgress.value - apkName

                    result.onSuccess { file ->
                        ExtensionManager.installApk(app, file)
                    }
                } catch (e: Exception) {
                    // Continue with remaining updates
                }
            }
            isUpdatingAll.value = false
            refreshInstalledStatus()
        }
    }

    fun addRepo(name: String, url: String, category: RepoCategory) {
        repoManager.addRepository(name, url, category, "Custom repo oleh user")
        loadExtensions()
    }

    fun removeRepo(repoId: String) {
        repoManager.removeRepository(repoId)
        loadExtensions()
    }



    fun canRequestPackageInstalls(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${app.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(intent)
            }
        }
    }

    
    fun loadExtensions() {
        viewModelScope.launch {
            _uiState.value = ExtensionsUiState.Loading
            
            val repos = repoManager.repositories.value
            val result = ExtensionManager.getAllAvailableExtensions(repos)
            val installed = ExtensionManager.getInstalledExtensions(app)
            val updates = ExtensionManager.checkForUpdates(app, repos)
            
            result.fold(
                onSuccess = { available ->
                    _uiState.value = ExtensionsUiState.Success(available, installed, updates)
                },
                onFailure = { e ->
                    _uiState.value = ExtensionsUiState.Error(e.localizedMessage ?: "Gagal memuat ekstensi")
                }
            )
        }
    }


    
    fun refreshInstalledStatus() {
        val currentState = _uiState.value
        if (currentState is ExtensionsUiState.Success) {
            viewModelScope.launch {
                val repos = repoManager.repositories.value
                val installed = ExtensionManager.getInstalledExtensions(app)
                val updates = ExtensionManager.checkForUpdates(app, repos)
                _uiState.value = currentState.copy(installed = installed, updates = updates)
                // Also refresh loaded dynamic sources in content repository!
                // app.reloadDynamicExtensions()
            }
        }
    }


    fun downloadAndInstall(extension: AvailableExtension) {
        if (!canRequestPackageInstalls()) {
            showPermissionWarning.value = true
        }
        viewModelScope.launch {
            val apkName = extension.apk
            val pkgName = extension.pkg
            _uiState.value.let { state ->
                if (state !is ExtensionsUiState.Success) return@launch
            }
            
            // Set initial progress
            downloadProgress.value = downloadProgress.value + (apkName to 0f)
            
            val result = ExtensionManager.downloadExtensionApk(app, apkName, extension.repoUrl, pkgName, extension.code) { progress ->
                downloadProgress.value = downloadProgress.value + (apkName to progress)
            }
            
            // Remove progress after complete/error
            downloadProgress.value = downloadProgress.value - apkName
            
            result.onSuccess { file ->
                ExtensionManager.installApk(app, file)
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        ExtensionManager.uninstallExtension(app, pkgName)
    }

    fun installLocalApk(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                if (!canRequestPackageInstalls()) {
                    showPermissionWarning.value = true
                    return@launch
                }
                val contentResolver = app.contentResolver
                val tempFile = File(app.cacheDir, "local_extension_install.apk")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                if (tempFile.exists()) {
                    ExtensionManager.installApk(app, tempFile)
                }
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }
}

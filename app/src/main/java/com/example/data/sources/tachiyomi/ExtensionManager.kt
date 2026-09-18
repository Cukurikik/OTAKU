package com.example.data.sources.tachiyomi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.sources.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

object ExtensionManager {
    private val client = com.example.data.NetworkModule.okHttpClient

    val BUILT_IN_REPOS = listOf(
        ExtensionRepo(
            id = "keiyoushi",
            name = "Keiyoushi Extensions",
            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/",
            category = RepoCategory.MANGA,
            isDefault = true,
            description = "Repo resmi fork Tachiyomi"
        ),
        ExtensionRepo(
            id = "keiyoushi-anime",
            name = "Keiyoushi Anime",
            url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/apk/",
            category = RepoCategory.ANIME,
            isDefault = true,
            description = "Anime Extensions"
        ),
        ExtensionRepo(
            id = "secozzi",
            name = "Secozzi Extensions",
            url = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/apk/",
            category = RepoCategory.ANIME,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "claudemirovsky",
            name = "Claudemirovsky Extensions",
            url = "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/apk/",
            category = RepoCategory.ANIME,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "hollow",
            name = "Hollow Aniyomi Extensions",
            url = "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/index.min.json",
            apkBaseUrl = "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/apk/",
            category = RepoCategory.ANIME,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "yuzono-manga",
            name = "Yuzono Manga",
            url = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/apk/",
            category = RepoCategory.MANGA,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "mojuru",
            name = "Mojuru Cursed Manga",
            url = "https://raw.githubusercontent.com/mojuru/cursed-manga-repo/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/mojuru/cursed-manga-repo/repo/apk/",
            category = RepoCategory.MANGA,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "kareadita",
            name = "Kareadita Tach Extension",
            url = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/apk/",
            category = RepoCategory.MANGA,
            isDefault = true,
            description = ""
        ),
        ExtensionRepo(
            id = "littlesurvival",
            name = "LittleSurvival CopyManga",
            url = "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/index.min.json",
            apkBaseUrl = "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/apk/",
            category = RepoCategory.MANGA,
            isDefault = true,
            description = ""
        )

    )
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private const val PREFS_NAME = "extension_manager_prefs"
    private const val KEY_CUSTOM_REPOS = "custom_repos_json"

    var customRepos: MutableList<ExtensionRepo> = mutableListOf()

    /** Extension repositories are intentionally restricted to Comic/MANGA. */
    val allRepos: List<ExtensionRepo>
        get() = (BUILT_IN_REPOS + customRepos)

    fun loadCustomRepos(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CUSTOM_REPOS, null)
            if (!jsonStr.isNullOrEmpty()) {
                val list = json.decodeFromString<List<ExtensionRepo>>(jsonStr)
                customRepos.clear()
                customRepos.addAll(list)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun saveCustomRepos(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = json.encodeToString(customRepos.toList())
            prefs.edit().putString(KEY_CUSTOM_REPOS, jsonStr).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun addCustomRepo(context: Context, repo: ExtensionRepo) {
        // Anime/Drama are direct-scraper-only; only MANGA repos may be installed here.
        if (repo.category != RepoCategory.MANGA) return
        if (customRepos.none { it.url == repo.url }) {
            customRepos.add(repo)
            saveCustomRepos(context)
        }
    }

    fun removeCustomRepo(context: Context, repoId: String) {
        customRepos.removeAll { it.id == repoId }
        saveCustomRepos(context)
    }

    private val TRUSTED_SIGNATURES = setOf(
        "557058c06cef19c010708551b3d683758b7c6798e2ac0fa908e2f6fa72e51964", // Tachiyomi Key
        "7ca5f5d8869bf8ba73ec992523f6e9196b24cb34a4bc31284a1e948fc22378f4"  // Keiyoushi Key
    )
    
    var userTrustedSignatures = mutableSetOf<String>()
    
    private val allTrustedSignatures: Set<String> get() = TRUSTED_SIGNATURES + userTrustedSignatures

    private fun getSignatureSha256(signature: android.content.pm.Signature): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    private fun getAppSignatures(context: Context): Set<String> {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            info.signatures?.map { getSignatureSha256(it) }?.toSet().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    private suspend fun fetchRepoExtensions(repo: ExtensionRepo): List<AvailableExtension> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(repo.url)
                    .header("User-Agent", "CineManga/1.0 (Android)")
                    .build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    response.body?.string() ?: return@withContext emptyList()
                }
                json.decodeFromString<List<AvailableExtension>>(body)
                    .map { it.copy(repoUrl = repo.apkBaseUrl) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // ---- Fetch semua repo ----
    suspend fun getAvailableExtensions(
        repos: List<ExtensionRepo> = allRepos
    ): Result<List<AvailableExtension>> {
        return try {
            val allExtensions = mutableListOf<AvailableExtension>()
            val errors = mutableListOf<String>()
            
            for (repo in repos) {
                val ext = fetchRepoExtensions(repo)
                if (ext.isNotEmpty()) {
                    allExtensions.addAll(ext)
                } else {
                    errors.add(repo.name)
                }
            }
            
            // Dedup: jika pkg sama di beberapa repo, prefer yang pertama (prioritas lebih tinggi)
            val deduped = allExtensions.distinctBy { it.pkg }
            Result.success(deduped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Filter by category ----
    suspend fun getMangaExtensions(): Result<List<AvailableExtension>> {
        val mangaRepos = allRepos.filter { it.category == RepoCategory.MANGA || it.category == RepoCategory.MIXED }
        return getAvailableExtensions(mangaRepos)
    }

    suspend fun getAnimeExtensions(): Result<List<AvailableExtension>> {
        // Architectural rule: Anime is direct-scraper-only in CinemaNaga.
        return Result.success(emptyList())
    }

    fun getInstalledExtensions(context: Context): List<InstalledExtension> {
        val list = mutableListOf<InstalledExtension>()
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val packages = pm.getInstalledPackages(flags)
        
        for (pkgInfo in packages) {
            val pkgName = pkgInfo.packageName
            val isTachiyomiExt = pkgName.startsWith("eu.kanade.tachiyomi.extension.") || pkgName.startsWith("com.example.extension.")
            val isAniyomiExt = pkgName.startsWith("eu.kanade.tachiyomi.aniyomi.") || pkgName.startsWith("eu.kanade.tachiyomi.animeextension.") || pkgName.startsWith("xyz.nulldev.tachiyomi.extension.")
            
            if (isTachiyomiExt || isAniyomiExt) {
                try {
                    // Verify Signature Before Trusting
                    val sigPkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
                    }
                    
                    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        sigPkgInfo.signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        sigPkgInfo.signatures
                    }
                    
                    var isTrusted = false
                    if (signatures != null && signatures.isNotEmpty()) {
                        isTrusted = signatures.any { sig ->
                            val hash = getSignatureSha256(sig)
                            allTrustedSignatures.contains(hash)
                        }
                    }
                    
                    if (!isTrusted) {
                        // Skip loading untrusted extensions to prevent Malicious Code Injection
                        continue
                    }

                    val appInfo = pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
                    val mainClass = appInfo.metaData?.getString("tachiyomi.extension.class")
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                    list.add(
                        InstalledExtension(
                            name = name,
                            pkgName = pkgName,
                            versionCode = versionCode,
                            versionName = pkgInfo.versionName ?: "",
                            pkgFactory = mainClass,
                            lang = "all",
                            isNsfw = false
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Ignore package info errors
                }
            }
        }
        return list
    }

    

    suspend fun getAllAvailableExtensions(repos: List<ExtensionRepo>): Result<List<AvailableExtension>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = mutableListOf<AvailableExtension>()
        for (repo in repos) {
            try {
                val request = okhttp3.Request.Builder().url(repo.url).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseJson = response.body?.string()
                        if (responseJson != null) {
                            val list = json.decodeFromString<List<AvailableExtension>>(responseJson)
                            list.forEach { it.repoUrl = repo.url }
                            all.addAll(list)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        Result.success(all)
    }

    suspend fun checkForUpdates(context: Context, repos: List<ExtensionRepo>): List<Pair<InstalledExtension, AvailableExtension>> {
        val installed = getInstalledExtensions(context)
        val available = getAllAvailableExtensions(repos).getOrDefault(emptyList())
        val availableByPkg = available.associateBy { it.pkg }
        return installed.mapNotNull { inst ->
            val avail = availableByPkg[inst.pkgName] ?: return@mapNotNull null
            if (avail.code > inst.versionCode) Pair(inst, avail) else null
        }
    }

    fun loadExtensionSources(context: Context, extension: InstalledExtension): List<ContentSource> {
        val sources = mutableListOf<ContentSource>()
        val mainClass = extension.mainClass ?: return emptyList()
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(extension.pkg, PackageManager.GET_META_DATA)
            val apkPath = appInfo.sourceDir
            
            val classLoader = dalvik.system.PathClassLoader(apkPath, null, context.classLoader)
            val clazz = classLoader.loadClass(mainClass)
            
            val instance = try {
                clazz.getDeclaredConstructor(Context::class.java).newInstance(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clazz.getDeclaredConstructor().newInstance()
            }

            if (instance is SourceFactory) {
                instance.createSources().forEach { source ->
                    sources.add(TachiyomiExtensionAdapter(source, extension.pkg))
                }
            } else if (instance is Source) {
                sources.add(TachiyomiExtensionAdapter(instance, extension.pkg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignore load errors
        }
        return sources
    }

    suspend fun downloadExtensionApk(
        context: Context,
        apkName: String,
        repoUrl: String,
        expectedPkg: String,
        expectedVersionCode: Long,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseUrl = when {
            repoUrl.endsWith("index.min.json") -> repoUrl.removeSuffix("index.min.json").plus("apk/")
            repoUrl.endsWith("index.json") -> repoUrl.removeSuffix("index.json").plus("apk/")
            repoUrl.endsWith("/") -> repoUrl
            else -> repoUrl.substringBeforeLast('/') + "/apk/"
        }
        val url = "$baseUrl$apkName"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val totalBytes = body.contentLength()
                
                val cacheDir = File(context.cacheDir, "extensions")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                
                val tempFile = File(cacheDir, "$apkName.tmp")
                val finalFile = File(cacheDir, apkName)
                
                body.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int = 0
                        var bytesDownloaded: Long = 0
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            if (totalBytes > 0) {
                                onProgress(bytesDownloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                    }
                }
                
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val pkgInfo = try {
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_SIGNATURES)
                } catch (e: Exception) {
                    null
                }
                
                if (pkgInfo != null) {
                    if (pkgInfo.packageName != expectedPkg) {
                        tempFile.delete()
                        return@withContext Result.failure(Exception("Package name mismatch: ${pkgInfo.packageName} != $expectedPkg"))
                    }
                    
                    val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong()
                    if (apkVersionCode != expectedVersionCode) {
                        tempFile.delete()
                        return@withContext Result.failure(Exception("Version code mismatch: $apkVersionCode != $expectedVersionCode"))
                    }
                    
                    // Verify Signature
                    val signatures = pkgInfo.signatures
                    if (signatures != null && signatures.isNotEmpty()) {
                        val validSignature = signatures.any { sig ->
                            val hash = getSignatureSha256(sig)
                            allTrustedSignatures.contains(hash)
                        }
                        if (!validSignature) {
                            tempFile.delete()
                            return@withContext Result.failure(Exception("Untrusted APK signature"))
                        }
                    } else {
                        tempFile.delete()
                        return@withContext Result.failure(Exception("APK has no signature"))
                    }
                } else {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("Failed to parse APK"))
                }
                
                if (tempFile.length() > 50L * 1024 * 1024) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("APK size exceeds 50MB limit"))
                }
                
                if (tempFile.renameTo(finalFile)) {
                    Result.success(finalFile)
                } else {
                    tempFile.delete()
                    Result.failure(Exception("Failed to rename file"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, file: File): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val packageInstaller = context.packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)
                val out = session.openWrite(file.name, 0, file.length())
                java.io.FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
                out.close()
                
                val intent = Intent("com.example.EXTENSION_INSTALL_RESULT")
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags
                )
                session.commit(pendingIntent.intentSender)
                session.close()
                true
            } else {
                fallbackInstall(context, file)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallbackInstall(context, file)
        }
    }

    private fun fallbackInstall(context: Context, file: File): Boolean {
        return try {
            val authority = "${context.packageName}.provider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun uninstallExtension(context: Context, pkgName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$pkgName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ignore
        }
    }

    private val _packageChangesFlow = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val packageChangesFlow = _packageChangesFlow.asSharedFlow()

    fun notifyPackageChanged() {
        _packageChangesFlow.tryEmit(Unit)
    }
}

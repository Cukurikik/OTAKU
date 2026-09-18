package com.example.data.sources.tachiyomi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.util.UUID

class ExtensionRepositoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("extension_repos", Context.MODE_PRIVATE)
    
    private val _repositories = MutableStateFlow<List<ExtensionRepo>>(emptyList())
    val repositories: StateFlow<List<ExtensionRepo>> = _repositories.asStateFlow()
    
    init {
        loadRepositories()
    }
    
    private fun loadRepositories() {
        val reposJson = prefs.getString("repos_list", "[]") ?: "[]"
        try {
            val list = Json.decodeFromString<List<ExtensionRepo>>(reposJson)
            if (list.isEmpty()) {
                // Add Keiyoushi as default
                
                
                val defaultRepos = listOf(
                    ExtensionRepo(
                        id = "keiyoushi_default",
                        name = "Keiyoushi",
                        url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/",
                        category = RepoCategory.MIXED,
                        isDefault = true,
                        description = "Tachiyomi extensions repository maintained by Keiyoushi"
                    ),
                    ExtensionRepo(
                        id = "yuzono_manga",
                        name = "Yuzono Manga",
                        url = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/apk/",
                        category = RepoCategory.MANGA,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "yuzono_anime",
                        name = "Yuzono Anime",
                        url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/apk/",
                        category = RepoCategory.ANIME,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "mojuru_cursed",
                        name = "Mojuru Cursed",
                        url = "https://raw.githubusercontent.com/mojuru/cursed-manga-repo/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/mojuru/cursed-manga-repo/repo/apk/",
                        category = RepoCategory.MANGA,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "secozzi_aniyomi",
                        name = "Secozzi Aniyomi",
                        url = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/apk/",
                        category = RepoCategory.ANIME,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "claude_cursedyomi",
                        name = "Claudemirovsky Cursedyomi",
                        url = "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/apk/",
                        category = RepoCategory.MIXED,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "littlesurvival_copymanga",
                        name = "LittleSurvival Copymanga",
                        url = "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/apk/",
                        category = RepoCategory.MANGA,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "kareadita_tach",
                        name = "Kareadita Tach",
                        url = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
                        apkBaseUrl = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/apk/",
                        category = RepoCategory.MANGA,
                        isDefault = true
                    ),
                    ExtensionRepo(
                        id = "hollow_aniyomi_fr",
                        name = "Hollow Aniyomi FR",
                        url = "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/index.min.json",
                        apkBaseUrl = "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/apk/",
                        category = RepoCategory.ANIME,
                        isDefault = true
                    )
                )
                _repositories.value = defaultRepos
                saveRepositories(defaultRepos)
            } else {
                _repositories.value = list
            }
        } catch (e: Exception) {
            _repositories.value = emptyList()
        }
    }
    
    private fun saveRepositories(repos: List<ExtensionRepo>) {
        prefs.edit().putString("repos_list", Json.encodeToString(repos)).apply()
    }
    
    fun addRepository(name: String, url: String, category: RepoCategory, description: String = ""): Result<ExtensionRepo> {
        val current = _repositories.value.toMutableList()
        if (current.any { it.url == url }) {
            return Result.failure(Exception("Repository dengan URL ini sudah ada"))
        }
        
        // Basic URL validation
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(Exception("URL tidak valid"))
        }
        
        val apkBase = if (url.endsWith("index.min.json")) {
            url.replace("index.min.json", "apk/")
        } else if (url.endsWith("index.json")) {
            url.replace("index.json", "apk/")
        } else {
            url.substringBeforeLast("/") + "/apk/"
        }
        
        val newRepo = ExtensionRepo(
            id = UUID.randomUUID().toString(),
            name = name,
            url = url,
            apkBaseUrl = apkBase,
            category = category,
            isDefault = false,
            description = description
        )
        
        current.add(newRepo)
        _repositories.value = current
        saveRepositories(current)
        
        return Result.success(newRepo)
    }
    
    fun removeRepository(id: String): Boolean {
        val current = _repositories.value.toMutableList()
        val repo = current.find { it.id == id } ?: return false
        
        if (repo.isDefault) return false // Cannot remove default
        
        current.remove(repo)
        _repositories.value = current
        saveRepositories(current)
        return true
    }
}

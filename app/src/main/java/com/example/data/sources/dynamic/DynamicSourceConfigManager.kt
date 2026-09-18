package com.example.data.sources.dynamic

import android.content.Context
import android.util.Log
import com.example.data.NetworkModule
import com.example.data.sources.direct.Selectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for Dynamic Source OTA Configuration.
 * Separates scraper parsing logic and DOM selectors from core APK binary,
 * allowing live updates to Jsoup selectors, regexes, and endpoints without APK redeployment.
 */
object DynamicSourceConfigManager {
    private const val TAG = "DynamicSourceConfig"
    private const val CONFIG_FILE_NAME = "dynamic_source_configs.json"
    private const val PREFS_NAME = "dynamic_source_config_prefs"
    private const val KEY_LAST_SYNC = "last_ota_sync_time"
    private const val KEY_LOCAL_VERSION = "local_manifest_version"

    // Remote OTA Endpoints (Cloudflare Worker & Remote Repository Fallbacks)
    private val REMOTE_OTA_ENDPOINTS = listOf(
        "https://anime-aggregator.njirlah.workers.dev/api/sources/config",
        "https://raw.githubusercontent.com/yuzono/manga-repo/repo/dynamic_sources.json"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val sourceConfigs = ConcurrentHashMap<String, DynamicSourceConfig>()
    private val _configsFlow = MutableStateFlow<Map<String, DynamicSourceConfig>>(emptyMap())
    val configsFlow: StateFlow<Map<String, DynamicSourceConfig>> = _configsFlow.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes the manager by loading local cache, falling back to built-in seed defaults.
     */
    fun init(context: Context) {
        if (isInitialized) return
        loadLocalCache(context)
        isInitialized = true
    }

    private fun loadLocalCache(context: Context) {
        // 1. Seed baseline defaults
        val defaultMap = createDefaultConfigs()
        sourceConfigs.putAll(defaultMap)

        // 2. Load cached overrides from disk if present
        try {
            val file = File(context.filesDir, CONFIG_FILE_NAME)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val manifest = json.decodeFromString<DynamicSourceManifest>(content)
                    manifest.sources.forEach { cfg ->
                        sourceConfigs[cfg.sourceId] = cfg
                    }
                    Log.i(TAG, "Loaded ${manifest.sources.size} dynamic configs from local disk cache (version: ${manifest.version})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read local dynamic source cache: ${e.message}")
        }

        _configsFlow.value = sourceConfigs.toMap()
    }

    private fun saveLocalCache(context: Context, manifest: DynamicSourceManifest) {
        try {
            val file = File(context.filesDir, CONFIG_FILE_NAME)
            val content = json.encodeToString(manifest)
            file.writeText(content)
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .putInt(KEY_LOCAL_VERSION, manifest.version)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save dynamic source cache to disk: ${e.message}")
        }
    }

    /**
     * Syncs dynamic source configurations from remote OTA server.
     * Compares version and updates local cache if remote version is higher or on force.
     */
    suspend fun syncRemoteConfigs(context: Context, force: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        init(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt(KEY_LOCAL_VERSION, 1)
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()

        // Minimum 15-minute interval between automatic syncs unless forced
        if (!force && (now - lastSync) < 15 * 60 * 1000L) {
            return@withContext Result.success(sourceConfigs.size)
        }

        for (endpoint in REMOTE_OTA_ENDPOINTS) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", NetworkModule.userAgent)
                    .header("Accept", "application/json")
                    .build()

                val response = NetworkModule.okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val manifest = json.decodeFromString<DynamicSourceManifest>(body)
                        if (force || manifest.version >= currentVersion) {
                            manifest.sources.forEach { cfg ->
                                sourceConfigs[cfg.sourceId] = cfg
                            }
                            saveLocalCache(context, manifest)
                            _configsFlow.value = sourceConfigs.toMap()
                            Log.i(TAG, "Successfully updated ${manifest.sources.size} dynamic configs from $endpoint (version: ${manifest.version})")
                            return@withContext Result.success(manifest.sources.size)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "OTA fetch failed from $endpoint: ${e.message}")
            }
        }

        Result.success(sourceConfigs.size)
    }

    /**
     * Retrieves dynamic configuration for a specific source ID.
     */
    fun getConfig(sourceId: String): DynamicSourceConfig? {
        return sourceConfigs[sourceId]
    }

    /**
     * Injects dynamic selectors into standard [Selectors], overriding hardcoded values
     * if remote/cached dynamic selectors are configured.
     */
    fun getEffectiveSelectors(sourceId: String, default: Selectors): Selectors {
        val cfg = sourceConfigs[sourceId] ?: return default
        val s = cfg.selectors
        return Selectors(
            card = s.card?.takeIf { it.isNotBlank() } ?: s.animeList?.takeIf { it.isNotBlank() } ?: default.card,
            cardLink = s.cardLink?.takeIf { it.isNotBlank() } ?: default.cardLink,
            cardTitle = s.cardTitle?.takeIf { it.isNotBlank() } ?: s.title?.takeIf { it.isNotBlank() } ?: default.cardTitle,
            cardImage = s.cardImage?.takeIf { it.isNotBlank() } ?: default.cardImage,
            description = s.description?.takeIf { it.isNotBlank() } ?: default.description,
            genres = s.genres?.takeIf { it.isNotBlank() } ?: default.genres,
            episode = s.episode?.takeIf { it.isNotBlank() } ?: default.episode,
            episodeLink = s.episodeLink?.takeIf { it.isNotBlank() } ?: default.episodeLink,
            episodeTitle = s.episodeTitle?.takeIf { it.isNotBlank() } ?: default.episodeTitle,
            player = s.player?.takeIf { it.isNotBlank() } ?: s.episodeStream?.takeIf { it.isNotBlank() } ?: default.player,
            videoAttribute = s.videoAttribute?.takeIf { it.isNotBlank() } ?: default.videoAttribute,
            page = s.page?.takeIf { it.isNotBlank() } ?: default.page,
            pageAttribute = s.pageAttribute?.takeIf { it.isNotBlank() } ?: default.pageAttribute
        )
    }

    /**
     * Retrieves a single dynamic selector with fallback.
     */
    fun getSelector(sourceId: String, key: String, defaultSelector: String): String {
        val cfg = sourceConfigs[sourceId] ?: return defaultSelector
        return when (key) {
            "card" -> cfg.selectors.card ?: cfg.selectors.animeList ?: defaultSelector
            "card_link" -> cfg.selectors.cardLink ?: defaultSelector
            "card_title" -> cfg.selectors.cardTitle ?: cfg.selectors.title ?: defaultSelector
            "card_image" -> cfg.selectors.cardImage ?: defaultSelector
            "description" -> cfg.selectors.description ?: defaultSelector
            "genres" -> cfg.selectors.genres ?: defaultSelector
            "episode" -> cfg.selectors.episode ?: defaultSelector
            "episode_link" -> cfg.selectors.episodeLink ?: defaultSelector
            "episode_title" -> cfg.selectors.episodeTitle ?: defaultSelector
            "player" -> cfg.selectors.player ?: cfg.selectors.episodeStream ?: defaultSelector
            "page" -> cfg.selectors.page ?: defaultSelector
            "mirror_select" -> cfg.selectors.mirrorSelect ?: defaultSelector
            "mirror_item" -> cfg.selectors.mirrorItem ?: defaultSelector
            else -> cfg.selectors.extras[key] ?: defaultSelector
        }
    }

    /**
     * Retrieves dynamic regex pattern with fallback.
     */
    fun getRegex(sourceId: String, key: String, defaultRegex: String): Regex {
        val cfg = sourceConfigs[sourceId]
        val pattern = when (key) {
            "regex_m3u8" -> cfg?.regexes?.regexM3u8
            "regex_mp4" -> cfg?.regexes?.regexMp4
            "regex_decryption" -> cfg?.regexes?.regexDecryption
            "regex_ajax_nonce" -> cfg?.regexes?.regexAjaxNonce
            "regex_unpacker" -> cfg?.regexes?.regexUnpacker
            else -> cfg?.regexes?.regexExtras?.get(key)
        } ?: defaultRegex
        return pattern.toRegex(RegexOption.IGNORE_CASE)
    }

    /**
     * Manually updates a source configuration (e.g. from developer debug UI or custom OTA update).
     */
    fun updateConfig(context: Context, config: DynamicSourceConfig) {
        sourceConfigs[config.sourceId] = config
        _configsFlow.value = sourceConfigs.toMap()
        
        val manifest = DynamicSourceManifest(
            version = (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LOCAL_VERSION, 1)) + 1,
            lastUpdated = System.currentTimeMillis(),
            sources = sourceConfigs.values.toList()
        )
        saveLocalCache(context, manifest)
    }

    /**
     * Resets a specific source or all sources back to default seed mappings.
     */
    fun resetToDefaults(context: Context, sourceId: String? = null) {
        val defaults = createDefaultConfigs()
        if (sourceId != null) {
            defaults[sourceId]?.let { sourceConfigs[sourceId] = it }
        } else {
            sourceConfigs.clear()
            sourceConfigs.putAll(defaults)
        }
        _configsFlow.value = sourceConfigs.toMap()
        val manifest = DynamicSourceManifest(
            version = 1,
            lastUpdated = System.currentTimeMillis(),
            sources = sourceConfigs.values.toList()
        )
        saveLocalCache(context, manifest)
    }

    /**
     * Built-in seed defaults for all major scrapers.
     */
    private fun createDefaultConfigs(): Map<String, DynamicSourceConfig> {
        val map = mutableMapOf<String, DynamicSourceConfig>()

        // 1. Kuramanime
        map["kuramanime"] = DynamicSourceConfig(
            sourceId = "kuramanime",
            version = 100,
            baseUrl = "https://v19.kuramanime.ing",
            selectors = DynamicSelectors(
                card = "#animeList a, .filter__gallery a, .product__item a, .product__sidebar__view__item a",
                cardTitle = "h5, .sidebar-title-h5, .title, .entry-title",
                cardImage = ".product__sidebar__view__item, .set-bg, img",
                description = ".anime__details__text p, .entry-content p, .synopsis",
                genres = ".anime__details__widget ul li:contains(Genre) a, .genre-info a, .genres a",
                episode = "#episodeLists a, .episode-list a, .ep-item a, a[data-content]",
                player = "iframe#video-player, iframe[src*='kuramanime'], iframe[src*='stream'], #player iframe",
                animeList = "div.animepost, div.product__item",
                title = "h2.entry-title, .anime__details__title h3",
                episodeStream = "iframe#video-player"
            ),
            regexes = DynamicRegexes(
                regexM3u8 = """(?:source|file|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
                regexMp4 = """(?:source|file|src)\s*:\s*["']([^"']+\.mp4[^"']*)["']"""
            )
        )

        // 2. AsuraScans
        map["asurascans"] = DynamicSourceConfig(
            sourceId = "asurascans",
            version = 100,
            baseUrl = "https://www.asurascans.com",
            selectors = DynamicSelectors(
                card = ".listupd .bs, .bsx, .utao .uta, div.grid-cols-2 > div",
                cardLink = "a",
                cardTitle = ".tt, .title, h2, h3",
                cardImage = "img",
                description = ".entry-content, .synopsis, .desc",
                genres = ".genres a, .gnr a",
                episode = ".eplister li, .episodelist li, .clstyle li",
                episodeLink = "a",
                episodeTitle = ".epl-title, .chapternum",
                page = "#readerarea img, .rdminimal img, img.wp-manga-chapter-img"
            )
        )

        // 3. OtakuDesu
        map["otakudesu"] = DynamicSourceConfig(
            sourceId = "otakudesu",
            version = 100,
            baseUrl = "https://otakudesu.blog",
            selectors = DynamicSelectors(
                card = ".venz ul li, .rseries .rapost, .venz .detpost",
                cardLink = "a",
                cardTitle = ".jdlflm, h2.title",
                cardImage = ".thumb img, img",
                description = ".sinopc, .entry-content",
                genres = ".infozingle p:contains(Genre) a",
                episode = ".episodelist ul li",
                episodeLink = "a",
                episodeTitle = "a",
                player = "iframe.player-iframe, #embed_holder iframe, iframe"
            ),
            regexes = DynamicRegexes(
                regexM3u8 = """(?:source|file|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
                regexAjaxNonce = """nonce:\s*['"]([^'"]+)['"]"""
            )
        )

        // 4. Animasu
        map["animasu"] = DynamicSourceConfig(
            sourceId = "animasu",
            version = 100,
            baseUrl = "https://v2.animasu.work",
            selectors = DynamicSelectors(
                card = ".listupd .bs, .bsx, .animepost",
                cardLink = "a",
                cardTitle = ".tt, .title",
                cardImage = "img",
                description = ".entry-content, .desc",
                genres = ".genxed a",
                episode = ".episodelist li, .eplister li",
                episodeLink = "a",
                player = "select.mirror option, iframe",
                mirrorSelect = "select.mirror"
            )
        )

        // 5. WordPress AnimeStream Standard baseline
        map["wp_anime_stream"] = DynamicSourceConfig(
            sourceId = "wp_anime_stream",
            version = 100,
            selectors = DynamicSelectors(
                card = "a.group.block, .manga-card, .series-card, .film-list .item, .listupd .bs, .bsx, article, .post-item, .item, .gmr-box-content, .movie-item, .animepost, .venz, .series, .bixbox, .post-show, .product__item",
                cardLink = "a",
                cardTitle = ".tt, .title, .entry-title, h2, h3, h4, h5, .album-title, .video-title, .titlelist, .product__item__text h5",
                cardImage = "img, .thumb img, .kover img, .video-thumb img, .anime__details__pic, .set-bg, .product__item__pic, .post-thumbnail img",
                episode = "a.group.flex, .episodelist li, .eplister li, .episode-list li, .epslist li, .episodes a, .video-links a, .server-list li, ul.servers li, .lsteps li, .ep-button, #episodes a, .ep-list a, .list-eps a, .box-list-episode a, .episode-list-2 li, a.ep-item",
                episodeLink = "a",
                episodeTitle = ".epl-title, .ep-title, .episodi, .num, .server-name, .eps",
                player = "iframe[src], iframe[data-src], video source[src]"
            ),
            regexes = DynamicRegexes(
                regexM3u8 = """(?:source|file|src)\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
                regexMp4 = """(?:source|file|src)\s*:\s*["']([^"']+\.mp4[^"']*)["']""",
                regexAjaxNonce = """nonce:\s*['"]([^'"]+)['"]"""
            )
        )

        // 6. MangaStream Theme baseline
        map["wp_manga_stream"] = DynamicSourceConfig(
            sourceId = "wp_manga_stream",
            version = 100,
            selectors = DynamicSelectors(
                card = ".listupd .bs, .bsx, article.bs, .manga-item",
                cardLink = "a",
                cardTitle = ".tt, .title",
                cardImage = "img",
                description = ".entry-content, .desc",
                genres = ".genres a, .gnr a",
                episode = ".eplister li, .clstyle li, #chapterlist li",
                episodeLink = "a",
                episodeTitle = ".epl-title, .chapternum",
                page = "#readerarea img, .rdminimal img"
            )
        )

        // 7. Madara / WP Manga Theme baseline
        map["madara"] = DynamicSourceConfig(
            sourceId = "madara",
            version = 100,
            selectors = DynamicSelectors(
                card = ".page-item-detail, .c-tabs-item__content, .manga-card",
                cardLink = ".post-title a, a",
                cardTitle = ".post-title h3, .post-title h4, h3, h4",
                cardImage = "img",
                description = ".summary__content, .description-summary",
                genres = ".genres-content a",
                episode = "li.wp-manga-chapter, .chapter-list li",
                episodeLink = "a",
                episodeTitle = "a",
                page = ".page-break img, .reading-content img"
            )
        )

        return map
    }
}

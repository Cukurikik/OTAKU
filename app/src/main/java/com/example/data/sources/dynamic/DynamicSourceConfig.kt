package com.example.data.sources.dynamic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Manifest format for Remote JSON Mapping of Dynamic Source DOM Selectors & Regexes.
 * Enables OTA (Over-The-Air) updates to scraper selectors without requiring APK updates.
 */
@Serializable
data class DynamicSourceManifest(
    @SerialName("version") val version: Int = 1,
    @SerialName("last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @SerialName("sources") val sources: List<DynamicSourceConfig> = emptyList()
)

@Serializable
data class DynamicSourceConfig(
    @SerialName("source_id") val sourceId: String,
    @SerialName("version") val version: Int = 1,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("search_pattern") val searchPattern: String? = null,
    @SerialName("popular_pattern") val popularPattern: String? = null,
    @SerialName("selectors") val selectors: DynamicSelectors = DynamicSelectors(),
    @SerialName("regexes") val regexes: DynamicRegexes = DynamicRegexes(),
    @SerialName("extra_headers") val extraHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class DynamicSelectors(
    @SerialName("card") val card: String? = null,
    @SerialName("card_link") val cardLink: String? = null,
    @SerialName("card_title") val cardTitle: String? = null,
    @SerialName("card_image") val cardImage: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("genres") val genres: String? = null,
    @SerialName("episode") val episode: String? = null,
    @SerialName("episode_link") val episodeLink: String? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("player") val player: String? = null,
    @SerialName("video_attribute") val videoAttribute: String? = null,
    @SerialName("page") val page: String? = null,
    @SerialName("page_attribute") val pageAttribute: String? = null,
    // Specialized selectors
    @SerialName("anime_list") val animeList: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("episode_stream") val episodeStream: String? = null,
    @SerialName("mirror_select") val mirrorSelect: String? = null,
    @SerialName("mirror_item") val mirrorItem: String? = null,
    @SerialName("extras") val extras: Map<String, String> = emptyMap()
)

@Serializable
data class DynamicRegexes(
    @SerialName("regex_m3u8") val regexM3u8: String? = null,
    @SerialName("regex_mp4") val regexMp4: String? = null,
    @SerialName("regex_decryption") val regexDecryption: String? = null,
    @SerialName("regex_ajax_nonce") val regexAjaxNonce: String? = null,
    @SerialName("regex_unpacker") val regexUnpacker: String? = null,
    @SerialName("regex_extras") val regexExtras: Map<String, String> = emptyMap()
)

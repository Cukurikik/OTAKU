package com.example.data.sources

import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class BrowseFilter(
    val genres: List<String> = emptyList(),
    val status: String? = null, // "ongoing", "completed", "hiatus"
    val type: String? = null,   // "manga", "manhwa", "manhua"
    val orderBy: String = "update" // "update", "popular", "new"
)

enum class FilterType { GENRE, STATUS, TYPE, ORDER }

interface ContentSource {
    val sourceId: String
    val sourceName: String
    val type: com.example.data.models.ContentType get() = com.example.data.models.ContentType.COMIC
    
    val supportsVideo: Boolean get() = false
    val supportsSearch: Boolean get() = true
    val supportsFilter: Boolean get() = false
    val language: String get() = "id"
    val supportedFilters: List<FilterType> get() = emptyList()

    // Per-Source Custom Configuration & Header Overrides (Cloudflare/Turnstile/L7 Bypass)
    val customUserAgent: String?
        get() {
            val byId = SourceHeaderStore.getUserAgent(sourceId)
            if (!byId.isNullOrBlank()) return byId
            if (this is ConfigurableSource) {
                val host = runCatching { java.net.URI(defaultBaseUrl).host }.getOrNull()
                if (!host.isNullOrBlank()) return SourceHeaderStore.getUserAgent(host)
            }
            return null
        }
    val customCookie: String?
        get() {
            val byId = SourceHeaderStore.getCookie(sourceId)
            if (!byId.isNullOrBlank()) return byId
            if (this is ConfigurableSource) {
                val host = runCatching { java.net.URI(defaultBaseUrl).host }.getOrNull()
                if (!host.isNullOrBlank()) return SourceHeaderStore.getCookie(host)
            }
            return null
        }
    val customReferer: String?
        get() {
            val byId = SourceHeaderStore.getReferer(sourceId)
            if (!byId.isNullOrBlank()) return byId
            if (this is ConfigurableSource) {
                val host = runCatching { java.net.URI(defaultBaseUrl).host }.getOrNull()
                if (!host.isNullOrBlank()) return SourceHeaderStore.getReferer(host)
            }
            return null
        }

    /**
     * Dynamic HTTP headers configured for this source.
     * Falls back to global headers when individual values are not overridden.
     */
    val requestHeaders: Map<String, String>
        get() {
            val headers = mutableMapOf<String, String>()
            val ua = customUserAgent ?: com.example.data.NetworkModule.userAgent
            if (ua.isNotBlank()) headers["User-Agent"] = ua
            
            val cookie = customCookie
            if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie
            
            val ref = customReferer
            if (!ref.isNullOrBlank()) headers["Referer"] = ref
            
            return headers
        }

    fun getRecommendations(page: Int = 1): Flow<List<ContentItem>>
    fun getChapters(itemId: String): Flow<List<Chapter>>
    fun getPages(chapterId: String): Flow<List<Page>>
    
    fun getVideoStreamUrl(episodeId: String): Flow<String> = kotlinx.coroutines.flow.flow {
        throw UnsupportedOperationException("$sourceName tidak mendukung video stream")
    }
    
    suspend fun getDetail(itemId: String): ContentItem?

    fun search(query: String, page: Int = 1): Flow<List<ContentItem>> = kotlinx.coroutines.flow.flowOf(emptyList())

    fun browse(filter: BrowseFilter, page: Int = 1): Flow<List<ContentItem>> = getRecommendations(page)
}

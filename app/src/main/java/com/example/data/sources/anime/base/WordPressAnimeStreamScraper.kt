package com.example.data.sources.anime.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup

/**
 * Kelompok 1: WordPress AnimeStream Standard
 * Karakteristik Arsitektur:
 * - Struktur tema WordPress AnimeStream standar (bixbox, listupd, bs/bsx).
 * - Pagination standar: /page/X/ dan /?s=query
 * - Detail informasi: .entry-title, .thumb img/.kover img, .entry-content/.desc, .genxed a
 * - Episode list: .eplister li, .episodelist li, .epslist li, a.ep-item
 * - Resolusi player via AJAX player_ajax atau direct/nested iframe selector
 */
open class WordPressAnimeStreamScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : AnimeStreamThemeScraper(context, sourceId, sourceName, baseUrl)

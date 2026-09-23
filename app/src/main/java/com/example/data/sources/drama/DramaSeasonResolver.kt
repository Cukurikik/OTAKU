package com.example.data.sources.drama

import androidx.compose.runtime.Immutable
import com.example.data.models.Chapter
import com.example.data.models.ChapterEntry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Data structures for multi-season Drama series normalization.
 */
@Immutable
data class DramaSeries(
    val title: String,
    val seasons: List<DramaSeason> = emptyList()
) {
    val totalEpisodes: Int
        get() = seasons.sumOf { it.episodes.size }

    fun allEpisodes(): List<DramaEpisode> {
        return seasons.flatMap { it.episodes }
    }
}

@Immutable
data class DramaSeason(
    val seasonNumber: Int,
    val seasonName: String = "Season $seasonNumber",
    val episodes: List<DramaEpisode> = emptyList()
)

@Immutable
data class DramaEpisode(
    val episodeId: String,
    val seasonNumber: Int = 1,
    val episodeNumberInSeason: Int,
    val absoluteEpisodeNumber: Int,
    val title: String = "Episode $episodeNumberInSeason",
    val streamUrl: String = "",
    val date: String = ""
)

/**
 * Engine for parsing, unrolling range entries, and normalizing multi-season drama series.
 */
object DramaSeasonResolver {

    private val RANGE_REGEX = Regex(
        """(?i)(?:ep(?:isode)?\.?|eps?\.?|ch(?:apter)?\.?|batch)?\s*(\d{1,4})\s*(?:-|–|—|~|\.\.|s\/d|to)\s*(\d{1,4})(?:\s*(?:end|tamat|lengkap|batch|final))?""",
        RegexOption.IGNORE_CASE
    )

    private val SINGLE_EP_REGEX = Regex(
        """(?i)(?:ep(?:isode)?\.?|eps?\.?|ch(?:apter)?\.?)\s*(\d{1,4})""",
        RegexOption.IGNORE_CASE
    )

    private val SEASON_REGEX = Regex(
        """(?i)(?:season|ssn|s)\s*(\d{1,2})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses HTML document into a structured [DramaSeries].
     */
    fun parseSeasons(doc: Document, fallbackTitle: String = ""): DramaSeries {
        val seriesTitle = doc.selectFirst("h1.entry-title, .s-title, h1, .movie-content h3.title, .movie-content .title")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: fallbackTitle.ifBlank { "Drama Series" }

        val seasons = mutableListOf<DramaSeason>()

        // 1. Detect season tabs & containers
        val seasonContainers = doc.select(
            ".tab-pane[id*='season'], .seasons-holder .season, .season-tab-content, .season-list .season-item, .hal-season, .gmr-season, .episodelist-season, .season-box"
        )

        var runningAbsoluteEp = 0

        if (seasonContainers.isNotEmpty()) {
            seasonContainers.forEachIndexed { index, container ->
                val sNum = extractSeasonNumber(container, index + 1)
                val sTitle = container.selectFirst("h2, h3, h4, .season-title, .title-season")?.text()?.trim()
                    ?: "Season $sNum"

                val elementsWithSpecificClass = container.select(
                    ".episodelist li, .eps-list li, .episode-list li, .ep-list a, .gmr-listseries a, .hal-episodes li a, .gmr-listseries li a, .eplister ul li"
                )
                val targetElements = if (elementsWithSpecificClass.isNotEmpty()) elementsWithSpecificClass else container.select("li, a")

                val episodes = parseEpisodesFromElements(targetElements, sNum, runningAbsoluteEp, doc.baseUri())
                if (episodes.isNotEmpty()) {
                    runningAbsoluteEp += episodes.size
                    seasons.add(DramaSeason(seasonNumber = sNum, seasonName = sTitle, episodes = episodes))
                }
            }
        }

        // 2. Check for season navigation tabs if no containers matched
        if (seasons.isEmpty()) {
            val seasonTabs = doc.select("ul.nav-tabs li a[data-toggle='tab'], ul.season-tabs li, .season-nav a, .tab-links a")
            if (seasonTabs.size > 1) {
                seasonTabs.forEachIndexed { index, tab ->
                    val tabText = tab.text().trim()
                    val sNum = SEASON_REGEX.find(tabText)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                    val targetId = tab.attr("href").removePrefix("#")
                    val targetContent = if (targetId.isNotBlank()) doc.getElementById(targetId) else null
                    if (targetContent != null) {
                        val rawElements = targetContent.select(".episodelist li, .eps-list li, .episode-list li, .ep-list a, .gmr-listseries a, li, a")
                        val episodes = parseEpisodesFromElements(rawElements, sNum, runningAbsoluteEp, doc.baseUri())
                        if (episodes.isNotEmpty()) {
                            runningAbsoluteEp += episodes.size
                            seasons.add(DramaSeason(seasonNumber = sNum, seasonName = tabText.ifBlank { "Season $sNum" }, episodes = episodes))
                        }
                    }
                }
            }
        }

        // 3. Flat fallback list if single-season or flat layout
        if (seasons.isEmpty()) {
            val flatElements = doc.select(
                ".episode-list li, .eps-list li, .episodelist li, .ep-list a, .gmr-listseries a, .hal-episodes li a, .episodes a, li[data-video-source], .listseries a, .eplister ul li"
            )
            val episodes = parseEpisodesFromElements(flatElements, 1, 0, doc.baseUri())
            if (episodes.isNotEmpty()) {
                seasons.add(DramaSeason(seasonNumber = 1, seasonName = "Season 1", episodes = episodes))
            }
        }

        return DramaSeries(title = seriesTitle, seasons = seasons)
    }

    /**
     * Parses from raw HTML string.
     */
    fun parseSeasonsFromHtml(html: String, baseUrl: String = "", fallbackTitle: String = ""): DramaSeries {
        val doc = Jsoup.parse(html, baseUrl)
        return parseSeasons(doc, fallbackTitle)
    }

    private fun extractSeasonNumber(container: Element, fallbackNumber: Int): Int {
        val dataSeason = container.attr("data-season").toIntOrNull()
        if (dataSeason != null && dataSeason > 0) return dataSeason

        val idDigits = container.id().filter { it.isDigit() }.toIntOrNull()
        if (idDigits != null && idDigits > 0) return idDigits

        val headerText = container.selectFirst("h2, h3, h4, .season-title, .title-season")?.text().orEmpty()
        val match = SEASON_REGEX.find(headerText)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        return fallbackNumber
    }

    private fun parseEpisodesFromElements(
        elements: org.jsoup.select.Elements,
        seasonNumber: Int,
        startAbsoluteEp: Int,
        baseUri: String
    ): List<DramaEpisode> {
        val result = mutableListOf<DramaEpisode>()
        var currentAbs = startAbsoluteEp
        var currentInSeason = 0

        val seenUrls = mutableSetOf<String>()

        for (el in elements) {
            val a = if (el.tagName() == "a") el else el.selectFirst("a")
            val href = a?.absUrl("href") ?: el.attr("data-video-source").takeIf { it.isNotBlank() } ?: ""
            if (href.isBlank()) continue

            val rawTitle = el.selectFirst(".num, .ep-num, span:first-child, .fwduvp-thumbnail-title, .epl-num")?.text()
                ?: a?.text()
                ?: el.text()

            val rawDate = el.selectFirst(".date, .time, .epl-date, time")?.text().orEmpty()

            // Check if this element represents an Episode Range ("Ep 1-16", "Batch 1-12")
            val unrolled = unrollRangeIfNeeded(rawTitle, href, seasonNumber, currentAbs, currentInSeason, rawDate)
            if (unrolled.isNotEmpty()) {
                for (ep in unrolled) {
                    if (seenUrls.add(ep.episodeId)) {
                        currentAbs++
                        currentInSeason++
                        result.add(
                            ep.copy(
                                episodeNumberInSeason = ep.episodeNumberInSeason,
                                absoluteEpisodeNumber = currentAbs
                            )
                        )
                    }
                }
            } else {
                if (seenUrls.add(href)) {
                    currentInSeason++
                    currentAbs++

                    val epNum = extractSingleEpisodeNumber(rawTitle, href, currentInSeason)
                    val cleanTitle = formatCleanEpisodeTitle(rawTitle, epNum)

                    result.add(
                        DramaEpisode(
                            episodeId = href,
                            seasonNumber = seasonNumber,
                            episodeNumberInSeason = epNum,
                            absoluteEpisodeNumber = currentAbs,
                            title = cleanTitle,
                            streamUrl = href,
                            date = rawDate
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Unrolls batch/range episode strings like "Ep 1-16 End" into individual [DramaEpisode]s.
     */
    fun unrollRangeIfNeeded(
        rawTitle: String,
        baseUrl: String,
        seasonNumber: Int,
        startAbsoluteEp: Int,
        startInSeason: Int,
        date: String = ""
    ): List<DramaEpisode> {
        val match = RANGE_REGEX.find(rawTitle) ?: return emptyList()
        val start = match.groupValues[1].toIntOrNull() ?: return emptyList()
        val end = match.groupValues[2].toIntOrNull() ?: return emptyList()

        if (start >= end || (end - start) > 100) return emptyList()

        val list = mutableListOf<DramaEpisode>()
        var abs = startAbsoluteEp
        var inSeason = startInSeason

        for (epNum in start..end) {
            abs++
            inSeason++
            val sep = if (baseUrl.contains("#")) "&" else "#"
            val epUrl = "$baseUrl${sep}ep=$epNum"
            list.add(
                DramaEpisode(
                    episodeId = epUrl,
                    seasonNumber = seasonNumber,
                    episodeNumberInSeason = epNum,
                    absoluteEpisodeNumber = abs,
                    title = "Episode $epNum",
                    streamUrl = epUrl,
                    date = date
                )
            )
        }
        return list
    }

    private fun extractSingleEpisodeNumber(rawTitle: String, href: String, fallback: Int): Int {
        val titleMatch = SINGLE_EP_REGEX.find(rawTitle)
        if (titleMatch != null) {
            val num = titleMatch.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        val digitsOnly = rawTitle.filter { it.isDigit() }.toIntOrNull()
        if (digitsOnly != null && digitsOnly in 1..9999) return digitsOnly

        val hrefDigits = Regex("""(?:ep|episode)[-_/]?(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.get(1)?.toIntOrNull()
        if (hrefDigits != null && hrefDigits > 0) return hrefDigits

        return fallback
    }

    private fun formatCleanEpisodeTitle(rawTitle: String, epNum: Int): String {
        val trimmed = rawTitle.trim()
        val lower = trimmed.lowercase()
        return if (lower.startsWith("episode") || lower.startsWith("ep ") || lower.startsWith("ep.")) {
            trimmed
        } else if (trimmed.isNotBlank() && !trimmed.all { it.isDigit() }) {
            trimmed
        } else {
            "Episode $epNum"
        }
    }

    /**
     * Converts a [DramaSeries] into UI & Room compatible [Chapter] items.
     */
    fun toChapters(series: DramaSeries): List<Chapter> {
        val isMultiSeason = series.seasons.size > 1
        return series.seasons.flatMap { season ->
            season.episodes.map { ep ->
                val displayTitle = if (isMultiSeason) {
                    "S${season.seasonNumber} ${ep.title}"
                } else {
                    ep.title
                }
                Chapter(
                    id = ep.episodeId,
                    title = displayTitle,
                    url = ep.streamUrl.ifBlank { ep.episodeId },
                    date = ep.date
                )
            }
        }
    }

    /**
     * Converts a [DramaSeries] into Room DB entity [ChapterEntry] items with ordinal index calculation.
     */
    fun toChapterEntries(
        series: DramaSeries,
        profileId: Long,
        sourceId: String,
        mangaId: String
    ): List<ChapterEntry> {
        val chapters = mutableListOf<ChapterEntry>()
        val isMultiSeason = series.seasons.size > 1

        for (season in series.seasons) {
            for (ep in season.episodes) {
                // Formula: number encoded as SeasonNumber.EpisodeNumber or Absolute index
                val encodedNumber = if (isMultiSeason) {
                    season.seasonNumber.toFloat() + (ep.episodeNumberInSeason.toFloat() / 1000f)
                } else {
                    ep.episodeNumberInSeason.toFloat()
                }

                val displayName = if (isMultiSeason) {
                    "S${season.seasonNumber} ${ep.title}"
                } else {
                    ep.title
                }

                chapters.add(
                    ChapterEntry(
                        profileId = profileId,
                        sourceId = sourceId,
                        mangaId = mangaId,
                        chapterId = ep.episodeId,
                        name = displayName,
                        url = ep.streamUrl.ifBlank { ep.episodeId },
                        dateUpload = System.currentTimeMillis(),
                        number = encodedNumber,
                        read = false,
                        bookmark = false,
                        lastPageRead = 0,
                        lastReadAt = null,
                        downloaded = false
                    )
                )
            }
        }
        return chapters
    }
}

package com.example.data.sources.direct

/** Handles EP 1, E01, Episode 1, 1, 1.5, special/OVA labels without assuming order. */
object EpisodeNumberNormalizer {
    private val patterns = listOf(
        Regex("(?i)\\bepisode\\s*([0-9]+(?:\\.[0-9]+)?)"),
        Regex("(?i)\\bep\\.?\\s*([0-9]+(?:\\.[0-9]+)?)"),
        Regex("(?i)\\bepisode[-_ ]?([0-9]+(?:\\.[0-9]+)?)"),
        Regex("(?<!\\d)([0-9]+(?:\\.[0-9]+)?)(?!\\d)")
    )

    fun parse(text: String): Double? = patterns.firstNotNullOfOrNull { p ->
        p.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun sort(episodes: List<ScrapedEpisode>): List<ScrapedEpisode> = episodes.sortedWith(
        compareBy<ScrapedEpisode> { it.number == null }
            .thenBy { it.number ?: Double.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    )
}

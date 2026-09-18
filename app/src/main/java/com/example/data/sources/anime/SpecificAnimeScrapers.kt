package com.example.data.sources.anime

import android.content.Context
import com.example.data.sources.anime.specific.*
import com.example.data.sources.base.BaseAnimeSource

// Re-exports / typealiases for all 23 anime scrapers
typealias OtakudesuScraper = com.example.data.sources.anime.specific.OtakudesuScraper
typealias KusonimeScraper = com.example.data.sources.anime.specific.KusonimeScraper
typealias MeownimeScraper = com.example.data.sources.anime.specific.MeownimeScraper
typealias AnoboyScraper = com.example.data.sources.anime.specific.AnoboyScraper
typealias AnichinScraper = com.example.data.sources.anime.specific.AnichinScraper
typealias KazefuriScraper = com.example.data.sources.anime.specific.KazefuriScraper

// Kelompok 1: Tema Katsu / AnimeVn
typealias NimegamiScraper = com.example.data.sources.anime.specific.NimegamiScraper
typealias StuckNimeScraper = com.example.data.sources.anime.specific.StuckNimeScraper
typealias YlNimeScraper = com.example.data.sources.anime.specific.YlNimeScraper
typealias WibukuScraper = com.example.data.sources.anime.specific.WibukuScraper

// Kelompok 2: Tema AnimeStream / WordPress
typealias MyAnimeKuScraper = com.example.data.sources.anime.specific.MyAnimeKuScraper
typealias KuronimeScraper = com.example.data.sources.anime.specific.KuronimeScraper
typealias AnimeIndoScraper = com.example.data.sources.anime.specific.AnimeIndoScraper
typealias AnimasuIDScraper = com.example.data.sources.anime.specific.AnimasuIDScraper
typealias AnimeMoviesScraper = com.example.data.sources.anime.specific.AnimeMoviesScraper
typealias AnimeKuIndoScraper = com.example.data.sources.anime.specific.AnimeKuIndoScraper
typealias MeownimeNetScraper = com.example.data.sources.anime.specific.MeownimeNetScraper
typealias AnimasuMovieScraper = com.example.data.sources.anime.specific.AnimasuMovieScraper

// Kelompok 3: Tema OtakuPress / Custom
typealias Anime154Scraper = com.example.data.sources.anime.specific.Anime154Scraper
typealias NontonAnimeIDScraper = com.example.data.sources.anime.specific.NontonAnimeIDScraper
typealias OtakupoiScraper = com.example.data.sources.anime.specific.OtakupoiScraper

// Kelompok 4: Tema AJAX Obfuscated
typealias AnimeKuInfoScraper = com.example.data.sources.anime.specific.AnimeKuInfoScraper
typealias MiranimeScraper = com.example.data.sources.anime.specific.MiranimeScraper
typealias GomunimeScraper = com.example.data.sources.anime.specific.GomunimeScraper
typealias AlqanimeScraper = com.example.data.sources.anime.specific.AlqanimeScraper
typealias NimeIndoScraper = com.example.data.sources.anime.specific.NimeIndoScraper
typealias SubnimeScraper = com.example.data.sources.anime.specific.SubnimeScraper

/**
 * Katalog terpadu 23 Sumber Scraper Anime dengan Resolver Multi-Server.
 */
object SpecificAnimeScrapersCatalog {
    fun createAllScrapers(context: Context): List<BaseAnimeSource> = listOf(
        // Katsu / AnimeVn
        NimegamiScraper(context),
        StuckNimeScraper(context),
        YlNimeScraper(context),
        WibukuScraper(context),

        // AnimeStream / WP
        MyAnimeKuScraper(context),
        KuronimeScraper(context),
        AnimeIndoScraper(context),
        AnimasuIDScraper(context),
        AnimeMoviesScraper(context),
        AnimeKuIndoScraper(context),
        MeownimeNetScraper(context),
        AnimasuMovieScraper(context),

        // OtakuPress / Custom
        Anime154Scraper(context),
        NontonAnimeIDScraper(context),
        OtakupoiScraper(context),

        // AJAX Obfuscated
        AnimeKuInfoScraper(context),
        MiranimeScraper(context),
        GomunimeScraper(context),
        AlqanimeScraper(context),
        NimeIndoScraper(context),
        SubnimeScraper(context)
    )
}

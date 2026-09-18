package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.sources.manga.base.MangaStreamThemeScraper

class KomikuAsiaScraper(context: Context) : MangaStreamThemeScraper(
    context = context,
    sourceId = "komiku-asia",
    sourceName = "KomikuAsia",
    baseUrl = "https://01.komiku.asia"
)

class KomiknesiaScraper(context: Context) : MangaStreamThemeScraper(
    context = context,
    sourceId = "komiknesia",
    sourceName = "KomikNesia",
    baseUrl = "https://02.komiknesia.asia"
)

class IndoManhwaScraper(context: Context) : MangaStreamThemeScraper(
    context = context,
    sourceId = "indomanhwa",
    sourceName = "IndoManhwa",
    baseUrl = "https://indomanhwa.com"
)

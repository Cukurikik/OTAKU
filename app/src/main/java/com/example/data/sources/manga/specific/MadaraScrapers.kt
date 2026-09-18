package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.sources.manga.base.MadaraThemeScraper
import com.example.data.sources.manga.base.WpMangaThemeScraper

class PemersatuScraper(context: Context) : MadaraThemeScraper(
    context = context,
    sourceId = "pemersatu",
    sourceName = "Pemersatu",
    baseUrl = "https://manga.pemersatu.org"
)

class DoujinkuScraper(context: Context) : WpMangaThemeScraper(
    context = context,
    sourceId = "doujinku",
    sourceName = "Doujinku",
    baseUrl = "https://doujinku.org"
)

class KomikdewasaScraper(context: Context) : WpMangaThemeScraper(
    context = context,
    sourceId = "komikdewasa",
    sourceName = "KomikDewasa",
    baseUrl = "https://komikdewasa.mom"
)

class YaomicScraper(context: Context) : MadaraThemeScraper(
    context = context,
    sourceId = "yaomic",
    sourceName = "Yaomic",
    baseUrl = "https://www.yaomic.com/id"
)

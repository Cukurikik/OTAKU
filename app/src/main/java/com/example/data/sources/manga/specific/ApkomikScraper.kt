package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.sources.manga.base.MangaStreamThemeScraper

class ApkomikScraper(context: Context) : MangaStreamThemeScraper(
    context = context,
    sourceId = "apkomik",
    sourceName = "Apkomik",
    baseUrl = "https://01.apkomik.com",
    mangaPath = "manga"
)

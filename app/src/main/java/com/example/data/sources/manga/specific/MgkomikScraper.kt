package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.sources.manga.base.MadaraThemeScraper

class MgkomikScraper(context: Context) : MadaraThemeScraper(
    context = context,
    sourceId = "mgkomik",
    sourceName = "MGKomik",
    baseUrl = "https://web1.mgkomik.cc",
    mangaPath = "komik"
)

package com.example.data.sources.manga.base

import android.content.Context

/**
 * WP-Manga is essentially the Madara theme CMS.
 * This class inherits from MadaraThemeScraper to provide an alternative semantic name
 * for sites that use WP-Manga plugins but aren't strictly calling it Madara.
 */
abstract class WpMangaThemeScraper(
    context: Context,
    sourceId: String,
    sourceName: String,
    baseUrl: String,
    mangaPath: String = "manga"
) : MadaraThemeScraper(context, sourceId, sourceName, baseUrl, mangaPath)

package com.example.data.sources.drama.specific

import android.content.Context
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.drama.base.WpDramaThemeScraper

class VikiScraper(context: Context) : com.example.data.sources.viki.VikiSource(context)

class LicilScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "licil",
    sourceName = "Licil",
    baseUrl = "https://digitalepiphanies.org",
    itemSelector = ".gmr-item-modulepost, .item, article",
    titleSelector = ".entry-title, h2, h3, a[title]",
    imageSelector = "img",
    linkSelector = "a[itemprop='url'], a.gmr-watch-btn, a"
)

class LokalfilmScraper(context: Context) : com.example.data.sources.lokalfilm.LokalFilmSource(context)

class JucelebrityScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "jucelebrity",
    sourceName = "JuCelebrity",
    baseUrl = "https://jucelebrity.com"
)

class LicilBaratScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "licil-barat",
    sourceName = "Licil Barat",
    baseUrl = "https://digitalepiphanies.org/category/western-series",
    itemSelector = ".gmr-item-modulepost, .item, article",
    titleSelector = ".entry-title, h2, h3, a[title]",
    imageSelector = "img",
    linkSelector = "a[itemprop='url'], a.gmr-watch-btn, a"
)

open class DrakorIdDramaScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "drakorid",
    sourceName = "DrakorID",
    baseUrl = "https://drakorid.net",
    itemSelector = ".venz ul li, .post-item, .item, article",
    titleSelector = "h2, .title, .entry-title",
    imageSelector = "img",
    linkSelector = "a"
)

// Legacy alias class
class Ip178DramaScraper(context: Context) : DrakorIdDramaScraper(context)

open class NoDrakorDramaScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "nodrakor",
    sourceName = "NoDrakor",
    baseUrl = "https://nodrakor.icu",
    itemSelector = "article, .animepost, .item",
    titleSelector = "h2, h3, .title",
    imageSelector = "img",
    linkSelector = "a"
)

// Legacy alias class
class Ip134DramaScraper(context: Context) : NoDrakorDramaScraper(context)

class DramaQuDramaScraper(context: Context) : WpDramaThemeScraper(
    context = context,
    sourceId = "dramaqu",
    sourceName = "DramaQu",
    baseUrl = "https://dramaqu.is",
    itemSelector = "article, .item, .post-item",
    titleSelector = "h2, h3, .title, .entry-title",
    imageSelector = "img",
    linkSelector = "a"
)


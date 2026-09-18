package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.sources.manga.base.MangaStreamThemeScraper
import com.example.data.sources.manga.base.MadaraThemeScraper
import com.example.data.sources.manga.base.WpMangaThemeScraper
import com.example.data.sources.manga.base.BloggerThemeScraper

class MangatoonScraper(context: Context) : MangaStreamThemeScraper(context, "mangatoon", "MangaToon", "https://komik6.mangatoon.cc")
class BacamiScraper(context: Context) : MadaraThemeScraper(context, "bacami", "Bacami", "https://bacami.net")
class ZhuximanhwaScraper(context: Context) : BloggerThemeScraper(context, "zhuximanhwa", "ZhuxiManhwa", "https://zhuximanhwa.blogspot.com")
class SxkomikScraper(context: Context) : MangaStreamThemeScraper(context, "sxkomik", "SXKomik", "https://www.sxkomik.art")
class ThriveScraper(context: Context) : MangaStreamThemeScraper(context, "thrive", "Thrive", "https://thrive.moe")
class LsScanlationScraper(context: Context) : BloggerThemeScraper(context, "lsscanlation", "LSScanlation", "https://lsscanlation.blogspot.com")
class ManhuarmtlScraper(context: Context) : MangaStreamThemeScraper(context, "manhuarmtl", "ManhuaRMTL", "https://manhuarmtl.com")
class KomikuOrgScraper(context: Context) : MangaStreamThemeScraper(context, "komikuorg", "KomikuOrg", "https://komiku.org")
class KomikPediaScraper(context: Context) : BloggerThemeScraper(context, "komikpedia", "KomikPedia", "https://www.comic.komikpedia.com")
class NgomikScraper(context: Context) : MangaStreamThemeScraper(context, "ngomik", "Ngomik", "https://02.ngomik.cc")
class KeiKomikScraper(context: Context) : MangaStreamThemeScraper(context, "keikomik", "KeiKomik", "https://keikomik.net")
class DuniaKomikScraper(context: Context) : MangaStreamThemeScraper(context, "duniakomik", "DuniaKomik", "https://duniakomik.com")

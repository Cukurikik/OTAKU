package com.example.data.sources.direct

import android.content.Context
import com.example.data.sources.ContentSource
import com.example.data.sources.manga.specific.*

object ComicDirectSourceCatalog {
    fun createSources(context: Context): List<ContentSource> {
        return listOf(
            AsuraScansScraper(context),
            MgkomikScraper(context),
            ApkomikScraper(context),
            KomikcastScraper(context),
            ShinigamiScraper(context),
            
            // MangaStream Theme
            KomikuAsiaScraper(context),
            KomiknesiaScraper(context),
            IndoManhwaScraper(context),
            MangatoonScraper(context),
            SxkomikScraper(context),
            ThriveScraper(context),
            ManhuarmtlScraper(context),
            KomikuOrgScraper(context),
            NgomikScraper(context),
            KeiKomikScraper(context),
            DuniaKomikScraper(context),
            
            // Madara / WP-Manga Theme
            BacamiScraper(context),
            YaomicScraper(context),
            PemersatuScraper(context),
            DoujinkuScraper(context),
            KomikdewasaScraper(context),
            
            // Blogger Theme
            ZhuximanhwaScraper(context),
            LsScanlationScraper(context),
            KomikPediaScraper(context)
        )
    }
}

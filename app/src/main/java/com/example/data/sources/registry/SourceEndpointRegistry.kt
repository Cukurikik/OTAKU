package com.example.data.sources.registry

import java.net.URI

/**
 * Human-maintained source registry. This is intentionally separate from the
 * executable scraper implementations so a dead domain can be replaced without
 * rewriting the UI/repository layer.
 */
data class SourceEndpoint(
    val id: String,
    val name: String,
    val mode: SourceMode,
    val baseUrl: String,
    val status: EndpointStatus = EndpointStatus.UNKNOWN,
    val notes: String = "",
    val mirrors: List<String> = emptyList()
)

enum class SourceMode { COMIC, ANIME, DRAMA }
enum class EndpointStatus { ACTIVE, MOVED, DEAD, UNKNOWN }

object SourceEndpointRegistry {
    val endpoints: List<SourceEndpoint> = listOf(
        // Comic
        SourceEndpoint(
            "mgkomik-web1", "MGKomik", SourceMode.COMIC, "https://web1.mgkomik.cc/",
            mirrors = listOf("https://mgkomik.cc/")
        ),
        SourceEndpoint(
            "asurascans", "Asura Scans", SourceMode.COMIC, "https://www.asurascans.com/",
            mirrors = listOf("https://asuracomic.net/", "https://asura.gg/")
        ),
        SourceEndpoint(
            "apkomik", "APKOMIK", SourceMode.COMIC, "https://01.apkomik.com/",
            mirrors = listOf("https://apkomik.com/")
        ),
        SourceEndpoint(
            "komik6-mangatoon", "MangaToon", SourceMode.COMIC, "https://komik6.mangatoon.cc/",
            mirrors = listOf("https://mangatoon.cc/")
        ),
        SourceEndpoint(
            "komikdewasa", "KomikDewasa", SourceMode.COMIC, "https://komikdewasa.mom/",
            mirrors = listOf("https://komikdewasa.vip/")
        ),
        SourceEndpoint(
            "bacami", "BacaMi", SourceMode.COMIC, "https://bacami.net/",
            mirrors = listOf("https://bacami.cc/")
        ),
        SourceEndpoint(
            "zhuximanhwa", "Zhuxi Manhwa", SourceMode.COMIC, "https://zhuximanhwa.blogspot.com/"
        ),
        SourceEndpoint(
            "yaomic", "Yaomic", SourceMode.COMIC, "https://www.yaomic.com/id",
            mirrors = listOf("https://yaomic.com/id")
        ),
        SourceEndpoint(
            "sxkomik", "SXKomik", SourceMode.COMIC, "https://www.sxkomik.art/",
            mirrors = listOf("https://sxkomik.art/")
        ),
        SourceEndpoint(
            "komiku-asia", "Komiku", SourceMode.COMIC, "https://01.komiku.asia/",
            mirrors = listOf("https://komiku.org/", "https://02.komiku.asia/", "https://komiku.id/")
        ),
        SourceEndpoint(
            "thrive-moe", "Thrive", SourceMode.COMIC, "https://thrive.moe/"
        ),
        SourceEndpoint(
            "komiknesia", "KomikNesia", SourceMode.COMIC, "https://02.komiknesia.asia/",
            mirrors = listOf("https://komiknesia.asia/", "https://01.komiknesia.asia/")
        ),
        SourceEndpoint(
            "komikcast", "KomikCast", SourceMode.COMIC, "https://v2.komikcast.fit/",
            mirrors = listOf("https://v3.komikcast.fit/", "https://komikcast.fit/", "https://komikcast.site/")
        ),
        SourceEndpoint(
            "shinigami", "Shinigami", SourceMode.COMIC, "https://g.shinigami.asia/",
            mirrors = listOf("https://shinigami.asia/", "https://shinigami.id/", "https://shinigami01.com/")
        ),
        SourceEndpoint(
            "indomanhwa", "IndoManhwa", SourceMode.COMIC, "https://indomanhwa.com/"
        ),
        SourceEndpoint(
            "lsscanlation", "LS Scanlation", SourceMode.COMIC, "https://lsscanlation.blogspot.com/"
        ),
        SourceEndpoint(
            "manhuarmtl", "ManhuaRMTL", SourceMode.COMIC, "https://manhuarmtl.com/"
        ),
        SourceEndpoint(
            "komikuorg", "KomikuOrg", SourceMode.COMIC, "https://komiku.org/",
            mirrors = listOf("https://01.komiku.asia/", "https://02.komiku.asia/")
        ),
        SourceEndpoint(
            "komikpedia", "KomikPedia", SourceMode.COMIC, "https://www.comic.komikpedia.com/"
        ),
        SourceEndpoint(
            "komikcast3", "KomikCast3", SourceMode.COMIC, "https://v3.komikcast.fit/",
            mirrors = listOf("https://v2.komikcast.fit/", "https://komikcast.fit/")
        ),
        SourceEndpoint(
            "ngomik", "Ngomik", SourceMode.COMIC, "https://02.ngomik.cc/",
            mirrors = listOf("https://01.ngomik.cc/", "https://ngomik.cc/")
        ),
        SourceEndpoint(
            "keikomik", "KeiKomik", SourceMode.COMIC, "https://keikomik.net/"
        ),
        SourceEndpoint(
            "duniakomik", "DuniaKomik", SourceMode.COMIC, "https://duniakomik.com/"
        ),
        SourceEndpoint(
            "pemersatu", "Pemersatu", SourceMode.COMIC, "https://manga.pemersatu.org/"
        ),
        SourceEndpoint(
            "doujinku", "Doujinku", SourceMode.COMIC, "https://doujinku.org/"
        ),
        SourceEndpoint("copymanga-repo", "CopyManga repository", SourceMode.COMIC, "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/index.min.json"),
        SourceEndpoint("tach-extension-repo", "Tach extension repository", SourceMode.COMIC, "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json"),
        SourceEndpoint("manga-repo", "Yuzono Manga repository", SourceMode.COMIC, "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json"),
        SourceEndpoint("keiyoushi-repo", "Keiyoushi repository", SourceMode.COMIC, "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json"),
        SourceEndpoint("cursed-manga-repo", "Cursed Manga repository", SourceMode.COMIC, "https://raw.githubusercontent.com/mojuru/cursed-manga-repo/repo/index.min.json"),
        SourceEndpoint("cursedyomi-repo", "CursedYomi repository", SourceMode.COMIC, "https://raw.githubusercontent.com/Claudemirovsky/cursedyomi-extensions/repo/index.min.json"),
        SourceEndpoint("secozzi-repo", "Secozzi repository", SourceMode.COMIC, "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json"),
        SourceEndpoint("yuzono-anime-repo", "Yuzono Anime repository", SourceMode.COMIC, "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json"),
        SourceEndpoint("aniyomi-fr-repo", "Aniyomi FR repository", SourceMode.COMIC, "https://codeberg.org/hollow/aniyomi-extensions-fr/media/branch/repo/index.min.json") ,

        // Anime
        SourceEndpoint(
            "kuramanime", "Kuramanime", SourceMode.ANIME, "https://v19.kuramanime.ing/",
            mirrors = listOf(
                "https://v9.kuramanime.blog/",
                "https://v9.kuramanime.work/",
                "https://kuramanime.ing/",
                "https://kuramanime.blog/",
                "https://kuramanime.work/"
            )
        ),
        SourceEndpoint(
            "animasu", "Animasu", SourceMode.ANIME, "https://v2.animasu.work/",
            mirrors = listOf(
                "https://animasuid.com/",
                "https://animasu.work/",
                "https://v2.animasu.work/anime-movie/"
            )
        ),
        SourceEndpoint(
            "animasu-movie", "Animasu Movie", SourceMode.ANIME, "https://v2.animasu.work/anime-movie/",
            mirrors = listOf("https://v2.animasu.work/", "https://animasuid.com/")
        ),
        SourceEndpoint(
            "nimegami", "Nimegami", SourceMode.ANIME, "https://nimegami.id/",
            mirrors = listOf("https://nimegami.me/")
        ),
        SourceEndpoint("animemovies", "AnimeMovies", SourceMode.ANIME, "https://animemovies.org/"),
        SourceEndpoint("stucknime", "StuckNime", SourceMode.ANIME, "https://stucknime.my.id/"),
        SourceEndpoint("ylnime", "YlNime", SourceMode.ANIME, "https://ylnime.com/"),
        SourceEndpoint(
            "anime-kuindo", "AnimeKuIndo", SourceMode.ANIME, "https://s2.anime-kuindo.com/",
            mirrors = listOf("https://animeku.info/", "https://anime-kuindo.com/")
        ),
        SourceEndpoint(
            "wibuku", "Wibuku", SourceMode.ANIME, "https://wibuku.id/",
            mirrors = listOf("https://wibuku.com/")
        ),
        SourceEndpoint("myanimeku", "MyAnimeKu", SourceMode.ANIME, "https://myanimeku.com/"),
        SourceEndpoint(
            "kuronime", "Kuronime", SourceMode.ANIME, "https://kuronime.sbs/",
            mirrors = listOf("https://kuronime.org/")
        ),
        SourceEndpoint("animeindo", "AnimeIndo", SourceMode.ANIME, "https://animeindo.me/"),
        SourceEndpoint(
            "animasuid", "AnimasuID", SourceMode.ANIME, "https://animasuid.com/",
            mirrors = listOf("https://v2.animasu.work/", "https://animasu.work/")
        ),
        SourceEndpoint("anime154", "Anime154", SourceMode.ANIME, "https://154.203.167.17/"),
        SourceEndpoint(
            "meownime-net", "Meownime Net", SourceMode.ANIME, "https://meownime.net/",
            mirrors = listOf("https://meownime.ltd/")
        ),
        SourceEndpoint(
            "nontonanimeid", "NontonAnimeID", SourceMode.ANIME, "https://nontonanimeid.my.id/",
            mirrors = listOf("https://nontonanimeid.site/", "https://nontonanimeid.tv/")
        ),
        SourceEndpoint("otakupoi", "Otakupoi", SourceMode.ANIME, "https://otakupoi.org/"),
        SourceEndpoint(
            "animeku-info", "AnimeKu Info", SourceMode.ANIME, "https://animeku.info/",
            mirrors = listOf("https://s2.anime-kuindo.com/", "https://anime-kuindo.com/")
        ),
        SourceEndpoint("miranime", "Miranime", SourceMode.ANIME, "https://miranime.net/"),
        SourceEndpoint("gomunime", "Gomunime", SourceMode.ANIME, "https://gomunime.top/"),
        SourceEndpoint("alqanime", "Alqanime", SourceMode.ANIME, "https://alqanime.si/"),
        SourceEndpoint("nimeindo", "NimeIndo", SourceMode.ANIME, "https://v1.nimeindo.org/"),
        SourceEndpoint(
            "subnime", "Subnime", SourceMode.ANIME, "https://subnime.id/",
            mirrors = listOf("https://subnime.org/")
        ),
        SourceEndpoint(
            "otakudesu", "Otakudesu", SourceMode.ANIME, "https://otakudesu.blog/",
            mirrors = listOf("https://otakudesu.cloud/", "https://otakudesu.tube/")
        ),
        SourceEndpoint(
            "samehadaku", "Samehadaku", SourceMode.ANIME, "https://v2.samehadaku.how/",
            mirrors = listOf("https://samehadaku.email/", "https://samehadaku.care/")
        ),
        SourceEndpoint(
            "anoboy", "Anoboy", SourceMode.ANIME, "https://anoboy.be/",
            mirrors = listOf("https://anoboy.show/", "https://anoboy.media/")
        ),
        SourceEndpoint(
            "kusonime", "Kusonime", SourceMode.ANIME, "https://kusonime.com/",
            mirrors = listOf("https://kusonime.me/")
        ),
        SourceEndpoint(
            "anichin", "AniChin", SourceMode.ANIME, "https://anichin.watch/",
            mirrors = listOf(
                "https://anichin.cafe/",
                "https://anichin.com.co/"
            )
        ),
        SourceEndpoint(
            "kazefuri", "Kazefuri", SourceMode.ANIME, "https://kazefuri.id/",
            mirrors = listOf("https://sv4.kazefuri.cloud/")
        ),

        // Drama
        SourceEndpoint("viki", "Viki", SourceMode.DRAMA, "https://www.viki.com/"),
        SourceEndpoint(
            "licil", "Licil", SourceMode.DRAMA, "https://licil.org/",
            mirrors = listOf("https://licil.org/barat/", "https://digitalepiphanies.org/")
        ),
        SourceEndpoint(
            "licil-barat", "Licil Barat", SourceMode.DRAMA, "https://licil.org/barat/",
            mirrors = listOf("https://licil.org/", "https://digitalepiphanies.org/")
        ),
        SourceEndpoint(
            "jucelebrity", "JuCelebrity", SourceMode.DRAMA, "https://jucelebrity.com/",
            mirrors = listOf("https://jucelebrity.net/")
        ),
        SourceEndpoint(
            "lokalfilm", "LokalFilm", SourceMode.DRAMA, "https://lokalfilm.id/",
            mirrors = listOf("https://lokalfilm.vip/", "https://lokalfilm.me/")
        ),
        SourceEndpoint("drama-ip-178", "Legacy Drama IP 178", SourceMode.DRAMA, "http://178.128.91.191/", EndpointStatus.DEAD, "IP endpoint; treat as legacy and replace with a current hostname."),
        SourceEndpoint("drama-ip-134", "Legacy Drama IP 134", SourceMode.DRAMA, "http://134.209.105.11/", EndpointStatus.DEAD, "IP endpoint; treat as legacy and replace with a current hostname.")
    )

    fun forMode(mode: SourceMode): List<SourceEndpoint> = endpoints.filter { it.mode == mode }

    fun findById(sourceId: String): SourceEndpoint? {
        val clean = sourceId.trim().lowercase()
        return endpoints.firstOrNull {
            it.id.equals(clean, ignoreCase = true) ||
            clean.contains(it.id) ||
            it.id.contains(clean) ||
            it.name.equals(clean, ignoreCase = true)
        }
    }

    fun findByHostOrUrl(url: String): SourceEndpoint? {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: url.lowercase()
        return endpoints.firstOrNull { endpoint ->
            val epHost = runCatching { URI(endpoint.baseUrl).host?.lowercase() }.getOrNull() ?: ""
            if (epHost.isNotBlank() && (host.contains(epHost) || epHost.contains(host))) return@firstOrNull true
            endpoint.mirrors.any { mirror ->
                val mHost = runCatching { URI(mirror).host?.lowercase() }.getOrNull() ?: ""
                mHost.isNotBlank() && (host.contains(mHost) || mHost.contains(host))
            }
        }
    }

    fun getAllMirrors(sourceId: String): List<String> {
        val ep = findById(sourceId) ?: findByHostOrUrl(sourceId) ?: return emptyList()
        val list = mutableListOf<String>()
        list.add(ep.baseUrl.trimEnd('/'))
        ep.mirrors.forEach { m ->
            val trimmed = m.trimEnd('/')
            if (trimmed.isNotBlank() && !list.contains(trimmed)) {
                list.add(trimmed)
            }
        }
        return list
    }

    fun getNextMirror(sourceId: String = "", currentUrlOrBase: String = ""): String? {
        val ep = if (sourceId.isNotBlank()) findById(sourceId) ?: findByHostOrUrl(sourceId)
                 else if (currentUrlOrBase.isNotBlank()) findByHostOrUrl(currentUrlOrBase)
                 else endpoints.firstOrNull() ?: return null

        if (ep == null) return null
        val all = getAllMirrors(ep.id)
        if (all.size <= 1) return null

        val currentHost = runCatching { URI(currentUrlOrBase).host?.lowercase() }.getOrNull()
            ?: runCatching { URI(ep.baseUrl).host?.lowercase() }.getOrNull() ?: ""

        // Find the first mirror that is not the current host
        return all.firstOrNull { mirror ->
            val mHost = runCatching { URI(mirror).host?.lowercase() }.getOrNull() ?: ""
            mHost.isNotBlank() && !currentHost.contains(mHost) && !mHost.contains(currentHost)
        } ?: all.firstOrNull()
    }

    fun getNextMirror(endpoint: SourceEndpoint): String? =
        getNextMirror(endpoint.id, endpoint.baseUrl)
}

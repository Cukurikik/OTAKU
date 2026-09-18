package com.example.data.sources.anime.specific

import android.content.Context
import com.example.data.sources.anime.base.WordPressAnimeStreamScraper
import com.example.data.sources.anime.base.CustomAjaxEncryptedAnimeScraper
import com.example.data.sources.anime.base.KatsuAnimeVnThemeScraper
import com.example.data.sources.anime.base.OtakuPressThemeScraper
import com.example.data.sources.anime.base.AjaxObfuscatedThemeScraper
import com.example.data.sources.anime.base.OploverzThemeScraper
import com.example.data.sources.anime.base.DonghuaThemeScraper

// ==========================================
// Oploverz Theme Based
// ==========================================
class OtakudesuScraper(context: Context) : OploverzThemeScraper(context, "otakudesu", "OtakuDesu", "https://otakudesu.blog")
class KusonimeScraper(context: Context) : OploverzThemeScraper(context, "kusonime", "Kusonime", "https://kusonime.com")
class MeownimeScraper(context: Context) : OploverzThemeScraper(context, "meownime", "Meownime", "https://meownime.net")
class AnoboyScraper(context: Context) : OploverzThemeScraper(context, "anoboy", "Anoboy", "https://anoboy.be")

// ==========================================
// Donghua Theme Based
// ==========================================
class AnichinScraper(context: Context) : DonghuaThemeScraper(context, "anichin", "Anichin", "https://anichin.watch")
class KazefuriScraper(context: Context) : DonghuaThemeScraper(context, "kazefuri", "Kazefuri", "https://kazefuri.id")

// ==========================================
// Kelompok 1: Tema Katsu / AnimeVn (select#server, div.player-embed)
// ==========================================
class NimegamiScraper(context: Context) : KatsuAnimeVnThemeScraper(context, "nimegami", "Nimegami", "https://nimegami.id")
class StuckNimeScraper(context: Context) : KatsuAnimeVnThemeScraper(context, "stucknime", "Stucknime", "https://stucknime.my.id")
class YlNimeScraper(context: Context) : KatsuAnimeVnThemeScraper(context, "ylnime", "Ylnime", "https://ylnime.com", "index.php?terbaru=%d")
class WibukuScraper(context: Context) : KatsuAnimeVnThemeScraper(context, "wibuku", "Wibuku", "https://wibuku.id")

// ==========================================
// Kelompok 2: Tema AnimeStream / WordPress (ul.server-list, iframe.lazyload)
// ==========================================
class MyAnimeKuScraper(context: Context) : WordPressAnimeStreamScraper(context, "myanimeku", "MyAnimeKu", "https://myanimeku.com")
class KuronimeScraper(context: Context) : WordPressAnimeStreamScraper(context, "kuronime", "Kuronime", "https://kuronime.sbs")
class AnimeIndoScraper(context: Context) : WordPressAnimeStreamScraper(context, "animeindo", "AnimeIndo", "https://animeindo.me")
class AnimasuIDScraper(context: Context) : WordPressAnimeStreamScraper(context, "animasuid", "Animasu ID", "https://animasuid.com")
class AnimeMoviesScraper(context: Context) : WordPressAnimeStreamScraper(context, "animemovies", "AnimeMovies", "https://animemovies.org")
class AnimeKuIndoScraper(context: Context) : WordPressAnimeStreamScraper(context, "anime-kuindo", "Anime Kuindo", "https://s2.anime-kuindo.com")
class MeownimeNetScraper(context: Context) : WordPressAnimeStreamScraper(context, "meownime-net", "Meownime Net", "https://meownime.net")
class AnimasuMovieScraper(context: Context) : CustomAjaxEncryptedAnimeScraper(context, "animasu-movie", "Animasu Movie", "https://v2.animasu.work/anime-movie/", "animasu")

// ==========================================
// Kelompok 3: Tema OtakuPress / Custom (div.mirror-stream, data-content Base64)
// ==========================================
class Anime154Scraper(context: Context) : OtakuPressThemeScraper(context, "anime154", "Streaming 154", "https://154.203.167.17")
class NontonAnimeIDScraper(context: Context) : OtakuPressThemeScraper(context, "nontonanimeid", "NontonAnimeID", "https://nontonanimeid.my.id")
class OtakupoiScraper(context: Context) : OtakuPressThemeScraper(context, "otakupoi", "OtakuPoi", "https://otakupoi.org")

// ==========================================
// Kelompok 4: Tema AJAX Obfuscated (action=get_video_url, eval packed JS)
// ==========================================
class AnimeKuInfoScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "animeku-info", "Animeku", "https://animeku.info")
class MiranimeScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "miranime", "Miranime", "https://miranime.net")
class GomunimeScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "gomunime", "Gomunime", "https://gomunime.top")
class AlqanimeScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "alqanime", "Alqanime", "https://alqanime.si")
class NimeIndoScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "nimeindo", "Nimeindo", "https://v1.nimeindo.org")
class SubnimeScraper(context: Context) : AjaxObfuscatedThemeScraper(context, "subnime", "Subnime", "https://subnime.id")



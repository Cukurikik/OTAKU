# Panduan Scraper Anime — CinemaNaga

Dokumen ini menjelaskan cara membuat source Anime yang mengikuti pola extension-style seperti Mihon/Keiyoushi/Anikyomi, tetapi tetap menggunakan engine internal CinemaNaga.

## 1. Arsitektur

Alur request:

`UI → ViewModel/Repository → ContentSource → BaseAnimeSource → WebViewFetcher/OkHttp → Jsoup → parser → ContentItem/Chapter/VideoStreamInfo`

Source Anime **tidak boleh** menaruh logika UI, database, atau player di dalam adapter. Adapter hanya bertanggung jawab terhadap:

1. URL source.
2. Search/browse.
3. Detail anime.
4. Daftar episode.
5. URL player/embed.
6. Normalisasi hasil ke model aplikasi.

Pola ini sejalan dengan model extension repository modern: source dipisahkan dari app dan extension repository menyediakan metadata/catalogue. Lihat Keiyoushi dan repository anime Yūzōnō sebagai referensi arsitektur. 

## 2. Kontrak minimum source Anime

Implementasikan:

- `getRecommendations(page)`
- `search(query, page)`
- `getDetail(itemId)`
- `getChapters(itemId)` — episode dipetakan menjadi `Chapter`.
- `getVideoStream(episodeId)` — diwarisi dari `BaseAnimeSource` atau dioverride jika struktur player khusus.

`ContentItem.type` wajib `ContentType.ANIME`.

## 3. Template source

Gunakan file:

`app/src/main/java/com/example/data/sources/templates/AnimeScraperTemplate.kt`

Template tersebut menyediakan parser selector yang dapat dikonfigurasi tanpa mengubah repository/UI.

Contoh penggunaan:

```kotlin
class ExampleAnimeSource(context: Context) : AnimeScraperTemplate(context) {
    override val sourceId = "example-anime"
    override val sourceName = "Example Anime"
    override val baseUrl = "https://example.test"

    override val selectors = AnimeSelectors(
        card = ".anime-card, article",
        title = ".title, h2, h3",
        link = "a",
        image = "img",
        description = ".synopsis, .description",
        genres = ".genres a, .genre a",
        episode = ".episode-list a, .episodes a",
        episodeTitle = "",
        episodeDate = ".date, time",
        searchCard = ".anime-card, article"
    )
}
```

## 4. Menemukan selector

Buka halaman source dan gunakan DevTools/WebView inspector.

Cari elemen:

| Data | Selector yang dicari |
|---|---|
| Poster | `img[src]`, `img[data-src]` |
| Judul | `h1`, `.title`, `.entry-title` |
| Sinopsis | `.synopsis`, `.desc`, `.description` |
| Genre | `.genre a`, `.genres a` |
| Episode | `.episodes a`, `.episodelist a`, `ul li a` |
| Player | `iframe[src]`, `iframe[data-src]` |
| Subtitle | `track[src]`, `track[kind=\"subtitles\"]` |

Jangan mengandalkan satu selector saja. Gunakan fallback selector.

## 5. URL episode

Normalisasi URL dengan:

```kotlin
val href = element.absUrl("href")
```

Jangan menggabungkan domain secara manual jika halaman dapat menggunakan relative URL.

## 6. Player Anime

Urutan extraction yang disarankan:

1. `iframe[src]`
2. `iframe[data-src]`
3. `<video src>`
4. `<source src>`
5. provider embed yang ditemukan pada halaman.
6. fallback ke `embedUrl` agar Android WebView dapat membuka player.

Jangan menganggap setiap iframe adalah MP4. Banyak player menggunakan halaman embed.

## 7. M3U8

Jika source memberikan URL `.m3u8`, masukkan sebagai `VideoQuality`.

```kotlin
VideoQuality(
    qualityLabel = "720p",
    url = streamUrl,
    isM3U8 = true
)
```

Jika playlist berisi beberapa rendition, parser dapat menormalkan resolusi menjadi 1080p/720p/480p/360p.

## 8. Subtitle

Gunakan model:

```kotlin
Subtitle(
    language = "id",
    url = subtitleUrl,
    format = "vtt"
)
```

Jika source hanya menyediakan subtitle melalui player embed, jangan memaksakan extraction langsung; simpan `embedUrl` dan biarkan player/WebView menangani halaman tersebut.

## 9. Cloudflare / JavaScript

`WebViewFetcher` sudah memiliki dua tahap:

`OkHttp → jika challenge/gagal → Android WebView`

Source tidak perlu membuat WebView sendiri kecuali ada kebutuhan khusus.

Jika source memakai JavaScript untuk membentuk DOM, gunakan WebView fallback dan parse `outerHTML` setelah `onPageFinished`.

## 10. Search

Untuk WordPress:

```text
/?s=<encoded>&post_type=anime
```

Untuk endpoint custom:

```text
/search?q=<encoded>
```

Selalu gunakan URL encoding.

## 11. Pagination

Jangan menganggap semua source memakai `?page=`. Buat konfigurasi per source:

- `/page/2/`
- `?page=2`
- `?paged=2`
- endpoint AJAX

Jika source tidak mendukung pagination, return halaman pertama secara konsisten.

## 12. Domain berpindah

Jangan mengubah `baseUrl` di UI. Source cukup memakai:

```kotlin
protected fun currentBaseUrl(): String = effectiveBaseUrl(context)
```

Pengguna/admin dapat menyimpan override melalui `SourceBaseUrlStore`.

## 13. Error handling

Bedakan:

- `UnknownHostException` → DNS/domain mati.
- `ConnectException` → server tidak menerima koneksi.
- `SSLHandshakeException` → TLS/sertifikat.
- HTTP 404 → route/domain salah.
- HTTP 403/429 → akses ditolak/rate limit.
- timeout → server lambat atau jaringan bermasalah.
- HTML kosong/challenge → gunakan WebView/fallback.

Jangan mengubah semua error menjadi `emptyList()` tanpa mengirim status ke `SourceStatusBus`.

## 14. Checklist source Anime

- [ ] `sourceId` stabil.
- [ ] `sourceName` stabil.
- [ ] `baseUrl` valid.
- [ ] `currentBaseUrl()` dipakai untuk semua request.
- [ ] Search bekerja.
- [ ] Browse/latest bekerja.
- [ ] Detail bekerja.
- [ ] Episode bekerja.
- [ ] Player/embed bekerja.
- [ ] Error dilaporkan ke `SourceStatusBus`.
- [ ] ContentType = ANIME.
- [ ] URL relative dinormalisasi dengan `absUrl`.
- [ ] Selector punya fallback.
- [ ] Tidak ada selector UI di ViewModel.

## 15. Source yang perlu diuji dari daftar proyek

Anime:

- KuraAnime: `v19.kuramanime.ing`, `v9.kuramanime.blog`, `v9.kuramanime.work`
- Otakudesu: `otakudesu.blog`
- Meownime: `meownime.ltd`
- Samehadaku: `v2.samehadaku.how`
- Anoboy: `anoboy.be`
- Kusonime: `kusonime.com`
- AniChin: `anichin.cafe`, `anichin.watch`, `anichin.com.co`
- Kazefuri: `sv4.kazefuri.cloud`, `kazefuri.id`

Selain source individual, repository anime Yūzōnō dan Secozzi dapat diperlakukan sebagai **catalogue extension**, bukan sebagai HTML source langsung. Catalogue JSON harus diparse menjadi metadata extension lalu source extension-nya yang melakukan scraping/API. Jangan memberikan URL `index.min.json` ke `WebViewFetcher` sebagai halaman anime.

## 16. Debug procedure

1. Test health endpoint.
2. Test homepage.
3. Test search.
4. Test detail.
5. Test episode list.
6. Test player.
7. Test subtitle.
8. Test domain override.
9. Test source setelah restart app.
10. Simpan HTML fixture untuk regression test.

## 17. Regression fixture

Simpan HTML yang sudah dianonimkan di:

`app/src/test/resources/sources/anime/<source-id>/`

Parser dapat diuji tanpa jaringan:

```kotlin
val document = Jsoup.parse(fixtureHtml, baseUrl)
val items = parser.parseCards(document)
assertTrue(items.isNotEmpty())
```

Ini penting karena website sering mengubah class CSS tanpa mengganti fungsi halaman.

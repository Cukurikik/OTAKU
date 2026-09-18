# Panduan Scraper Drama — CinemaNaga

Dokumen ini khusus untuk source Drama/TV series/film yang mempunyai halaman detail, episode, player, subtitle, dan kadang beberapa mirror.

## 1. Arsitektur

Alur:

`UI → Repository → ContentSource → BaseDramaSource → WebViewFetcher → Jsoup → parser → ContentItem/Chapter → VideoExtractor/Player`

Drama menggunakan `Chapter` sebagai representasi episode agar reader/history engine yang sudah ada tetap dapat dipakai tanpa membuat model episode kedua.

## 2. Model data

Gunakan:

```kotlin
ContentItem(
    id = detailUrl,
    title = title,
    source = sourceName,
    imageUrl = poster,
    type = ContentType.DRAMA,
    description = synopsis,
    genres = genres,
    sourceId = sourceId
)
```

Episode:

```kotlin
Chapter(
    id = episodeUrl,
    title = "Episode 1",
    url = episodeUrl,
    date = dateText
)
```

## 3. Template

Gunakan:

`app/src/main/java/com/example/data/sources/templates/DramaScraperTemplate.kt`

Contoh:

```kotlin
class ExampleDramaSource(context: Context) : DramaScraperTemplate(context) {
    override val sourceId = "example-drama"
    override val sourceName = "Example Drama"
    override val baseUrl = "https://example.test"

    override val selectors = DramaSelectors(
        card = ".drama-card, article, .post-card",
        title = ".title, h2, h3",
        link = "a",
        image = "img",
        description = ".synopsis, .description, .desc",
        genres = ".genre a, .genres a",
        episode = ".episode-list a, .episodes a, .episodelist a",
        episodeTitle = "",
        episodeDate = ".date, time",
        searchCard = ".drama-card, article, .post-card"
    )
}
```

## 4. Pola halaman Drama

Pola yang harus didukung parser:

### A. Listing

```text
/card
  /poster
  /title
  /detail-link
```

### B. Detail

```text
/poster
/title
/synopsis
/genres
/episodes
```

### C. Episode

```text
/episode-title
/player
/subtitle
/mirrors
```

## 5. Selector fallback

Jangan membuat parser seperti:

```kotlin
doc.select(".only-one-class")
```

Gunakan:

```kotlin
doc.select(".episode-list a, .episodes a, .episodelist a, ul.eplist a")
```

Jika struktur source benar-benar berbeda, buat selector configuration khusus source tersebut.

## 6. Episode ordering

Website sering menampilkan:

`Episode 12 → Episode 11 → ... → Episode 1`

Sedangkan UI biasanya menginginkan:

`Episode 1 → Episode 2 → ... → Episode 12`

Jangan selalu memakai `.reversed()` karena beberapa source sudah ascending. Lebih baik parse nomor episode dan sort jika angka dapat diekstrak.

Contoh konsep:

```kotlin
fun episodeNumber(title: String): Int =
    Regex("(?i)(?:episode|ep|e)\\s*[-.]?\\s*(\\d+)")
        .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
```

## 7. Player

Urutan:

1. iframe utama.
2. data-src iframe.
3. video/source HTML5.
4. mirror link.
5. embed page.
6. WebView fallback.

Contoh:

```kotlin
val embeds = doc.select("iframe[src], iframe[data-src]")
    .mapNotNull { el ->
        el.absUrl("src").ifEmpty { el.absUrl("data-src") }
            .takeIf { it.isNotBlank() }
    }
```

Jangan mengasumsikan URL iframe adalah file video.

## 8. Mirror

Model mirror sebaiknya dinormalisasi sebelum masuk player:

```text
Mirror(name, url, priority, requiresWebView)
```

Urutan prioritas dapat berdasarkan:

- HTTPS.
- M3U8 langsung.
- MP4 langsung.
- embed yang kompatibel.
- mirror terakhir sebagai WebView.

## 9. Subtitle

Cari:

```css
track[kind="subtitles"]
track[kind="captions"]
.subtitle a
.subtitles a
```

Normalisasi bahasa:

- `id`, `ind`, `Indonesia` → `id`
- `en`, `eng`, `English` → `en`
- `jp`, `jpn`, `Japanese` → `ja`
- `kr`, `kor`, `Korean` → `ko`

## 10. Source Viki

`https://www.viki.com/` diperlakukan berbeda dari source HTML sederhana.

Jangan memaksa parser WordPress seperti `.bsx` atau `.venz`.

Strategi:

1. Cari API/data JSON yang memang diberikan halaman untuk client.
2. Jika hanya tersedia melalui rendered application, gunakan WebView untuk mendapatkan DOM/data yang diperlukan.
3. Simpan metadata series/episode dalam `ContentItem`/`Chapter`.
4. Player tetap melalui provider resmi/embedded player sesuai struktur halaman.

## 11. Domain `Not Found`

Jika source Drama memberikan `Not Found`, jangan langsung menyimpulkan ISP block.

Bedakan:

| Kondisi | Diagnosis |
|---|---|
| DNS NXDOMAIN | domain tidak terdaftar/tidak resolve |
| HTTP 404 | server hidup, route tidak ada |
| HTTP 410 | resource/domain sudah retired |
| HTTP 301/302 | domain pindah |
| TLS error | sertifikat/hostname bermasalah |
| 403 | server menolak request |
| 429 | rate limit |
| timeout | server/network lambat |
| HTML challenge | bot protection / JS challenge |

## 12. Dua IP lama

Untuk:

- `http://178.128.91.191/`
- `http://134.209.105.11/`

jangan jadikan IP sebagai domain permanen. Tandai `LEGACY_IP`, lakukan health probe, dan sediakan override hostname. IP dapat berubah, virtual-host routing dapat gagal, dan TLS SNI dapat tidak cocok.

## 13. Domain override

Source harus menggunakan:

```kotlin
val base = currentBaseUrl()
```

bukan:

```kotlin
val base = baseUrl
```

untuk request runtime.

Penggantian URL:

```kotlin
SourceBaseUrlStore.set(
    context,
    sourceId,
    "https://domain-baru.example"
)
```

Reset:

```kotlin
SourceBaseUrlStore.clear(context, sourceId)
```

## 14. Source status

Ketika request gagal:

```kotlin
SourceStatusBus.reportFromException(
    sourceId,
    sourceName,
    throwable
)
```

Ketika berhasil:

```kotlin
SourceStatusBus.clear(sourceId)
```

Dengan demikian UI dapat menunjukkan:

`ACTIVE`, `MOVED`, `DEAD`, `DNS_ERROR`, `HTTP_404`, `BLOCKED`, `RATE_LIMITED`, `TIMEOUT`, atau `CHALLENGE`.

## 15. Search

Jangan hardcode WordPress search untuk semua drama. Beberapa source memakai:

- `/?s=query`
- `/search?q=query`
- `/search/query`
- API/JSON
- client-side search.

Buat search strategy per source.

## 16. Daftar source Drama yang perlu diuji

- Viki — `https://www.viki.com/`
- Licil — `https://licil.org/`
- Licil Barat — `https://licil.org/barat/`
- legacy IP — `http://178.128.91.191/`
- JuCelebrity — `https://jucelebrity.com/`
- legacy IP — `http://134.209.105.11/`
- LokalFilm — `https://lokalfilm.id/`

Status setiap URL harus dihasilkan dari health probe pada saat pengujian, bukan dipercaya selamanya dari registry.

## 17. Pipeline recovery

Jika source gagal:

```text
Request
  ↓
HTTP
  ├─ 2xx → parse
  ├─ 3xx → follow/normalize
  ├─ 404/410 → route/domain check
  ├─ 403 → WebView/browser-compatible request
  ├─ 429 → backoff/rate limit
  └─ network error → health status
       ↓
WebView fallback
       ↓
DOM parse
       ↓
Jika tetap gagal
       ↓
SourceStatusBus
       ↓
UI: Change URL / Retry / Disable
```

## 18. Jangan mencampur source dengan provider

`Source` = menemukan series/episode dan URL player.

`VideoExtractor` = mengubah embed/provider menjadi stream metadata.

`Player` = memutar stream.

`Repository` = mengelola cache/database/history.

Pemisahan ini penting supaya perubahan player tidak memaksa seluruh scraper ditulis ulang.

## 19. Regression test

Buat fixture:

`app/src/test/resources/sources/drama/<source-id>/detail.html`

`app/src/test/resources/sources/drama/<source-id>/episode.html`

Test:

```kotlin
@Test
fun parseDramaDetail() {
    val doc = Jsoup.parse(fixture, baseUrl)
    val result = parser.parseDetail(doc, detailUrl)
    assertEquals(ContentType.DRAMA, result.type)
    assertTrue(result.title.isNotBlank())
}
```

## 20. Checklist Drama

- [ ] Listing.
- [ ] Search.
- [ ] Detail.
- [ ] Genre.
- [ ] Synopsis.
- [ ] Episode.
- [ ] Episode ordering.
- [ ] Player.
- [ ] Mirror.
- [ ] Subtitle.
- [ ] Domain override.
- [ ] Health status.
- [ ] WebView fallback.
- [ ] Regression fixture.
- [ ] `ContentType.DRAMA`.

# Scraper / Source Engine Guide

## Arsitektur yang direkomendasikan

```text
UI
 │
 ▼
HomeViewModel / DetailViewModel
 │
 ▼
ContentRepository
 │
 ├── ContentSource
 │    ├── BaseWPMangaSource
 │    ├── BaseMangaStreamSource
 │    ├── BaseAnimeSource
 │    └── BaseDramaSource
 │
 ├── SourceBaseUrlStore      ← user override domain
 ├── SourceStatusBus          ← error/status UI
 ├── SourceHealthProbe        ← reachability check
 └── SourceEndpointRegistry  ← metadata + endpoint catalog
```

Pola ini mengambil konsep extension/source dari ekosistem Tachiyomi/Mihon/Keiyoushi: source merupakan adapter independen, sedangkan app mengurus library, history, download, dan UI. Keiyoushi saat ini menyediakan katalog `index.min.json`, sementara Aniyomi extension repositories juga menggunakan katalog repo untuk instalasi extension. citeturn0search1turn0search0

## Parser rule

Jangan menggabungkan selector HTML ke ViewModel. Parser harus berada di source adapter.

Contoh bentuk source:

```kotlin
class ExampleSource(context: Context) : BaseWPMangaSource(context) {
    override val sourceId = "example"
    override val sourceName = "Example"
    override val defaultBaseUrl = "https://example.invalid"

    // implement selector/parser di source adapter
}
```

## Domain berubah / Not Found

Bedakan kasus berikut:

| Kondisi | Makna | Tindakan |
|---|---|---|
| 404 | route/domain sudah tidak tersedia | cek URL baru |
| DNS failure | hostname tidak ditemukan | cari domain pengganti |
| timeout | server lambat/down | retry/backoff |
| SSL handshake | sertifikat bermasalah | jangan mematikan TLS global; ganti endpoint |
| 403/429 | anti-bot/rate limit | hormati limit, backoff, atau gunakan source resmi |
| HTML berubah | parser stale | update adapter/selector |
| redirect | domain berpindah | simpan canonical URL bila aman |

`SourceBaseUrlStore` sudah menyediakan override per-source sehingga domain volatile tidak perlu hard-code ulang di UI.

## Fallback strategy

Urutan yang disarankan:

1. gunakan URL override user jika ada;
2. cek endpoint dengan `SourceHealthProbe`;
3. jika gagal, tandai source degraded;
4. coba source alternatif dalam mode yang sama;
5. tampilkan alasan kegagalan yang dapat ditindaklanjuti;
6. jangan menganggap `emptyList()` sebagai sukses.

## Extension repositories

Repository JSON seperti Keiyoushi/Anikyomi sebaiknya diperlakukan sebagai **metadata/catalog source**, bukan langsung dianggap sebagai scraper. Metadata dapat berisi package, APK, language, version, dan source URL; executable adapter tetap harus melalui kontrak `ContentSource`. citeturn0search1turn0search2

## Catatan domain pihak ketiga

Daftar endpoint dari pengguna dimasukkan sebagai registry metadata. Status runtime harus selalu diverifikasi karena domain pihak ketiga dapat berubah, redirect, diblokir, atau hilang kapan saja.

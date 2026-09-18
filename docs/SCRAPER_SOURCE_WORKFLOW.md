# Workflow Implementasi Source Anime & Drama

## A. Buat source

1. Copy template Anime atau Drama.
2. Tentukan `sourceId` permanen.
3. Tentukan `baseUrl` terakhir yang diketahui.
4. Buat selector configuration.
5. Implementasikan listing.
6. Implementasikan search.
7. Implementasikan detail.
8. Implementasikan episode.
9. Implementasikan player/mirror.
10. Register source.

## B. Jangan copy-paste parser antar website

Dua website dapat sama-sama memakai WordPress tetapi class CSS dan struktur player berbeda. Gunakan shared helper untuk parsing, bukan shared selector yang dipaksakan.

## C. Source repository vs source website

URL `index.min.json` seperti Keiyoushi/Yūzōnō/Secozzi adalah **catalogue**, bukan halaman konten. Pipeline yang benar:

`repo JSON → extension metadata → extension/source → website/API → normalized ContentItem`

Repository extension modern memang menggunakan catalogue metadata untuk menemukan/install extension. Keiyoushi menyediakan `index.min.json`, sedangkan Yūzōnō dan Secozzi juga mendokumentasikan repo anime extension mereka. 

## D. Health check

Health check dilakukan sebelum scraper penuh:

```text
DNS
 ↓
TLS
 ↓
HTTP status
 ↓
Content-Type
 ↓
HTML sanity
 ↓
Challenge detection
```

Jangan melakukan scraping seluruh listing hanya untuk mengetahui apakah domain hidup.

## E. Fallback

`defaultBaseUrl → user override → WebView fallback → SourceStatusBus`

Tidak boleh:

`defaultBaseUrl → diam-diam ganti ke domain acak`

Domain pengganti harus berasal dari konfigurasi yang dapat diverifikasi.

## F. Maintenance

Website scraper akan berubah. Keiyoushi sendiri memiliki issue untuk perubahan domain, source rusak, dan maintenance burden; ini alasan engine harus mempunyai selector fallback, health status, fixture test, dan domain override. 

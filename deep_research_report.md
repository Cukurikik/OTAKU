# Deep Research Report & Saran Pengembangan Selanjutnya

Berdasarkan audit dan inspeksi komprehensif terhadap proyek (arsitektur `com.example.*`, konfigurasi Gradle, library yang digunakan, serta sistem enkripsi dan Scraper), berikut adalah analisis mendalam mengenai status proyek, masalah yang ada, dan saran konkrit untuk iterasi selanjutnya.

## 1. Arsitektur Ekstraksi Video & DOM (Masalah Utama Scraper Anime)

**Masalah:**  
Sesuai catatan Anda, _"Scrapper Anime Masih Tidak Bagus Dan tidak Menempatkan Ke Bagian Video Di Situs Mereka"_.
Scraper statis (`KuramanimeScraper`, `AnimasuScraper`) seringkali gagal mengambil URL video (`m3u8` / `mp4`) atau metadata karena:
1. Video disembunyikan di balik proteksi iframe berlapis.
2. Pemutar video dirender menggunakan teknik *lazy load* yang dimuat via AJAX/XHR yang dikaburkan (obfuscated JavaScript).
3. Payload JSON dari endpoint streaming hanya dikirim jika ada interaksi DOM (misalnya menekan tombol play) atau memverifikasi CAPTCHA/Turnstile terlebih dahulu secara visual.

**Analisis Saat Ini:**
Meskipun proyek sudah memiliki alat yang kuat seperti `CloudflareBypassInterceptor` (untuk menyelinap melewati WAF via Cloudflare Worker) dan `VideoExtractorEngine.kt` (mampu mendekripsi payload CryptoJS & JS Packer), alat-alat ini **masih bergantung pada temuan URL statis dari HTML awal**. Jika HTML awal tidak memuat iframe atau `data-src` (karena dirender via JS), extractor ini akan gagal bekerja. Kita memiliki `WebViewFetcher`, namun belum dikonfigurasi penuh untuk mencegat (intercept) jaringan internal secara langsung (Network Interception untuk file `m3u8`).

**Solusi & Pekerjaan Selanjutnya:**
- **Membangun Headless Video Interceptor Pipeline:**
  Kita perlu memodifikasi atau memperluas `WebViewFetcher.kt` agar tidak hanya mengambil HTML akhir, melainkan memantau *Network Requests* di dalam WebView menggunakan `shouldInterceptRequest`. Saat WebView berjalan di background (headless) dan memuat halaman anime, ia akan otomatis menangkap request XHR atau ekstensi `.m3u8` / `.mpd` / `.mp4`, lalu mengembalikannya ke `VideoExtractorEngine` beserta dengan `Cookie` dan `User-Agent` yang valid.
- **Injeksi JS Otomatis:**
  Menambahkan logika evaluasi JavaScript (`evaluateJavascript`) untuk secara otomatis mengklik elemen DOM yang memicu pemuatan video (misal: elemen `.play-button`, `#server-list li`).

## 2. Manajemen 23 Domain Hardcoded vs Sistem Ekstensi Dinamis

**Masalah:**  
Anda memberikan 23 domain anime/drama (`v19.kuramanime.ing`, `nimegami.id`, dll.) beserta 20+ URL Repositori Tachiyomi/Aniyomi. Menulis dan memelihara 23 scraper kelas Kotlin (`.kt`) yang "hardcoded" akan berujung pada kerusakan massal (*massive breakage*) ketika struktur situs mereka berubah minggu depan. 

**Analisis Saat Ini:**
Saya menemukan bahwa proyek ini *sudah* memiliki fondasi untuk memuat ekstensi Aniyomi/Tachiyomi secara native (`ExtensionManager.kt`, `TachiyomiExtensionAdapter.kt`, `ExtensionRepositoryManager.kt`). Ini adalah arsitektur yang sangat bagus!

**Solusi & Pekerjaan Selanjutnya:**
- **Penyempurnaan Integrasi Ekstensi Tachiyomi/Aniyomi:** Daripada menulis ulang scraper anime secara manual di Kotlin untuk 23 situs tersebut, tugas kita selanjutnya adalah **memastikan `TachiyomiExtensionAdapter` berjalan sempurna** untuk mengeksekusi kelas-kelas dari ekstensi eksternal (APK). Jika ekstensi dari `yuzono-anime-repo` atau `keiyoushi` sudah mendukung ke-23 situs tersebut dengan update yang konstan, kita sebaiknya mendelegasikan tugas ekstraksi (khususnya untuk situs-situs yang paling sulit) ke ekosistem ekstensi JSON dinamis.
- **Pembuatan JSON Scraper Engine Internal:** Jika kita wajib membuat native scraper tanpa APK eksternal, kita harus membuat **Universal JSON/Ajax Theme Scraper**. Scraper ini membaca konfigurasi JSON dinamis dari Firebase/Cloud, bukan hardcoded di Kotlin.

## 3. Ketahanan Koneksi Eksternal (File Hosting Extraction)

**Masalah:**  
Banyak video anime (seperti dari anoboy, meownime, kusonime) di-host di layanan pihak ketiga seperti Streamtape, Mp4Upload, FileLions, atau DoodStream. Layanan ini menerapkan rate-limit, IP-locking, atau token berbasis referer yang ketat.

**Analisis Saat Ini:**
File seperti `DoodExtractor.kt` dan `StreamtapeExtractor.kt` sudah ada. Namun, bypass untuk Blob URLs atau chunked HLS seringkali terputus di tengah jalan karena `Referer` headers tidak disematkan dengan benar di `ExoPlayer` (`Media3`).

**Solusi & Pekerjaan Selanjutnya:**
- Meningkatkan konfigurasi `ExoPlayer` di `VideoPlayerViewModel` dengan menyuntikkan `ResolvingDataSource` yang memastikan header otentikasi (Referer, Cookie) disematkan secara native pada level HTTP DataSource (menggunakan `DefaultHttpDataSource.Factory`).
- Mengimplementasikan `FFmpeg` secara natif atau mekanisme sinkronisasi chunking HLS jika pengguna ingin mengunduh video streaming tersebut untuk mode offline.

## 4. Prioritas Eksekusi (Apa yang Harus Dikerjakan Terlebih Dahulu?)

Berdasarkan prioritas urgensi perbaikan fungsional sesuai instruksi Anda:

1. **(TAHAP 1) Refactor `WebViewFetcher` menjadi Advanced Network Interceptor:** 
   Mengatasi masalah situs anime yang menyembunyikan M3U8/API di balik klik tombol dan eksekusi JS. Kita akan membuat WebView Engine yang secara pasif mendengarkan request media dan menembusnya menggunakan header injeksi.
2. **(TAHAP 2) Menguji & Memperbaiki Tachiyomi Extension Bridge:** 
   Memastikan aplikasi dapat mengunduh, menginstal, dan memetakan `AnimeSource` dari tautan repo JSON eksternal yang Anda berikan, sehingga mengurangi beban perbaikan 23 domain secara manual (OTA Updates).
3. **(TAHAP 3) Integrasi Final Player & Scraper Pipeline:**
   Menyambungkan interceptor engine dan scraper ke UI Jetpack Compose untuk memastikan pemutaran video HLS berjalan lancar dengan semua metadata (thumbnail, subtitle) terekstrak bersih tanpa elemen simulasi atau dummy.

**Kesimpulan:**
Arsitektur database, enkripsi (AES-256-GCM), dan UI aplikasi sudah berada di standar *production-grade*. Kebocoran fitur terjadi tepat pada layer ekstraksi DOM Dinamis Anime. Fokus kita berikutnya adalah murni di **Network Interception & JS Evaluation Headless Engine**.

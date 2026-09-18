# Catatan Arsitektur

Proyek ini sudah berada di jalur yang tepat untuk meniru pola aplikasi reader bergaya Mihon/Tachiyomi/Keiyoushi: source dipisah per provider, ada repository untuk orkestrasi, ada status bus untuk error, dan ada penyimpanan override URL per sumber.

Komponen yang paling relevan:
- `ContentSource` sebagai kontrak umum provider.
- `BaseWPMangaSource`, `BaseAnimeSource`, dan `BaseDramaSource` sebagai fondasi parser berbasis HTML/WebView.
- `SourceStatusBus` untuk memunculkan error domain yang bisa ditindaklanjuti pengguna.
- `SourceBaseUrlStore` untuk override base URL saat domain berubah.
- `ProfileScreen` untuk memperlihatkan status source dan membuka dialog ganti URL.

Rekomendasi aman berikutnya:
1. Tambahkan health report per source agar UI bisa menandai domain yang mati sebelum user masuk ke halaman detail.
2. Tambahkan layar filter source untuk menonaktifkan provider yang terus gagal.
3. Untuk source yang Anda miliki haknya, pindahkan selector dan endpoint ke konfigurasi agar mudah dipelihara.
4. Untuk domain yang tidak bisa diakses, simpan fallback message yang jelas: DNS gagal, SSL gagal, timeout, atau 404.

Sesuai struktur repo yang sudah ada, perubahan terbaik adalah memperkuat framework, bukan menambah scraper spesifik ke situs pihak ketiga yang tidak jelas izinnya.

# Laporan Perbaikan Berdasarkan Audit Otaku Hub / Cinemanga

Berikut adalah daftar perbaikan kritis yang telah saya kerjakan secara utuh pada sesi ini berdasarkan dokumen audit yang Anda berikan.

## Fase 0: Keamanan dan Reproducible Build (Selesai)
1. **Pembersihan Kredensial**: Telah menghapus seluruh *secret variables* yang tidak aman dari file instruksi/`AGENTS.md`.
2. **Gradle Wrapper**: Telah menjalankan `gradle wrapper --gradle-version 8.9` untuk memastikan *reproducible build* di environment CI, sehingga proyek dapat di-build dengan konsisten.

## Fase 1: Stabilisasi Core Data (Selesai)
1. **Export Schema**: Mengaktifkan `exportSchema = true` pada `AppDatabase` (versi 4) dan mendaftarkan skema di file `.gradle` agar perubahan skema di masa mendatang bisa diaudit.
2. **Batch Upsert Chapter (DAO & ViewModel)**: Menambahkan method `insertChapters` di `ChapterDao` dan memodifikasi `DetailViewModel.kt`. Kini saat *refresh*, chapter akan melakukan *batch upsert* dengan mempertahankan data sinkronisasi lokal seperti status `read` dan `lastPageRead`.
3. **Penyelamatan CancellationException**: Membersihkan `catch (e: Exception)` yang menelan `CancellationException` pada `DetailViewModel.kt` dan `ReaderViewModel.kt`.
4. **Stabilitas ContentRepository**: 
    - Mengubah `channelFlow` agar menggunakan `supervisorScope`.
    - Memisahkan referensi source menggunakan snapshot `sources.toList()` untuk menghindari `ConcurrentModificationException`.
    - Memastikan *partial failure* (kegagalan satu source) tidak membatalkan coroutine source lainnya.

## Fase 2: Matangkan Reader (Selesai)
1. **Reader State Machine**: Mengubah state `ReaderViewModel` dari state individual menjadi `sealed interface ReaderState` yang memiliki mode `Loading`, `Success`, `Empty`, dan `Error`.
2. **Progress Debounce**: Memperbaiki logika auto-save progress di Reader agar menggunakan *debounce* (delay 500ms) untuk mencegah database terpicu terlalu sering saat pengguna scroll dengan cepat.
3. **Sinkronisasi Reader Initial UI**: Memperbaiki *race condition* di `ReaderScreen` saat melompat ke `initialPage` hanya setelah status `Success` di-emit.
4. **UI Error & Retry**: Menambahkan tampilan Error dengan tombol **Retry** pada `ReaderScreen`.
5. **Caching Gambar**: Menambahkan parameter `memoryCacheKey` dan `diskCacheKey` pada `ImageRequest` Coil agar gambar yang sama tidak diunduh ulang berlebihan.

## Fase 2: Fitur Unduhan & Manajemen Penyimpanan (Selesai)
1. **Fitur Penghapusan Unduhan**: Menambahkan fungsi `deleteDownloadedChapter` pada `DetailViewModel` yang secara rekursif menghapus file gambar chapter dari local storage, menghapus baris database di tabel `downloads` dan `page_downloads` (terjamin aman melalui Foreign Key Cascade), serta mengembalikan status `downloaded` di `ChapterEntry` menjadi `false`.
2. **Toggle Unduh Pintar**: Memperbarui button download di `DetailScreen.kt` sehingga berfungsi ganda: klik pada ikon download untuk mengunduh, dan klik pada ikon centang hijau untuk menghapus unduhan dan mengosongkan ruang penyimpanan perangkat.

## Fase 3: Pengaturan Reader & Lokalisasi Arah Baca (Selesai)
1. **Dukungan Arah Baca Kanan-ke-Kiri (RTL)**: Menggunakan `CompositionLocalProvider` dan `LocalLayoutDirection` untuk memutar arah geser halaman (`HorizontalPager`) saat opsi RTL diaktifkan pada manga.
2. **Skala Tampilan Dinamis**: Menambahkan preferensi skala gambar (Fit Screen, Fill Width, Crop/Fill) yang terintegrasi secara dinamis dengan `ContentScale` pada `AsyncImage` di `ReaderScreen.kt`.
3. **Persistensi Pengaturan Reader**: Seluruh preferensi reader (arah baca RTL, skala gambar, mode paged) disimpan secara lokal melalui `SettingsManager` (SharedPreferences) sehingga tersimpan aman dan otomatis diterapkan saat aplikasi dibuka kembali.

## Fase 4: Extension Lifecycle & Keamanan Dasar (Selesai)
1. **Atomic Download File**: Memperbaiki `downloadExtensionApk` di `ExtensionManager` agar menggunakan file temp (`.tmp`) selama proses unduh, dan hanya me-rename menjadi APK asli jika berhasil (`tempFile.renameTo`). Hal ini mencegah file terkorupsi jika aplikasi tertutup.
2. **Result Pattern**: Mengubah *return type* dari proses instalasi/unduh menjadi `Result<T>` Kotlin yang terstandardisasi, agar kegagalan (misalnya karena HTTP code 404, atau Cloudflare) dapat ditangkap dengan tepat oleh UI.
3. **Pembersihan WebViewFetcher**: Memodifikasi `fetchHtmlWithWebView` dengan `suspendCancellableCoroutine { continuation -> continuation.invokeOnCancellation { ... } }` sehingga jika coroutine dibatalkan, *WebView* akan dipaksa dihancurkan (`destroy`) dan tidak menyebabkan *memory leak* di *background*.
4. **Verifikasi Tanda Tangan APK (Signature Verification)**: Menambahkan verifikasi sidik jari SHA-256 untuk sertifikat tanda tangan digital APK Keiyoushi dan Tachiyomi (`TRUSTED_SIGNATURES`). APK ekstensi yang diunduh wajib memiliki tanda tangan yang sah sebelum diizinkan dimuat untuk mencegah pemuatan dynamic code yang tidak tepercaya.


## Tambahan Patch Aman
- Menambahkan `SourceHealth.kt` untuk laporan kesehatan source dan klasifikasi error domain.
- Menambahkan folder `docs/` berisi status domain, catatan arsitektur, dan batasan aman.

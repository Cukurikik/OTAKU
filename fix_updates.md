# Fitur UpdatesScreen Diimplementasikan
1. Tabel database baru `updates` dan `UpdateDao` ditambahkan (Migrasi DB ke v9).
2. `LibraryUpdateWorker` dimodifikasi: Selain memicu notifikasi Android, worker kini juga merekam chapter/episode rilis baru ke tabel `updates`.
3. `UpdatesViewModel` dan `UpdatesScreen` dibuat:
   - Menampilkan daftar kronologis serial yang baru update (disertai tanggal, judul, chapter).
   - Memiliki fitur "Tandai sudah dibaca" dan "Hapus".
   - Klik pada item akan mengarahkan user langsung ke layar Detail manga/anime tersebut.
4. Terintegrasi ke Bottom Navigation Bar: Tab "Update" kini tersedia bersama Home, Library, History, dan Profile.

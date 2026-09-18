# Mode Anime / Komik / Drama

CineManga sekarang memiliki satu aplikasi dengan tiga Browse mode:

- **Anime** → katalog sumber yang menghasilkan `ContentType.ANIME`.
- **Komik** → katalog sumber yang menghasilkan `ContentType.COMIC`.
- **Drama** → katalog sumber yang menghasilkan `ContentType.DRAMA`.

## Alur

```text
HomeScreen
   │
   ├── ModeChip(Anime/Komik/Drama)
   │        │
   │        └── HomeViewModel.setMode()
   │                 │
   │                 ├── persist SettingsManager.hubMode
   │                 ├── reset selected source/page
   │                 └── reload repository
   │
   └── Source chips
            │
            └── hanya source yang cocok dengan mode
```

## File baru

`app/src/main/java/com/example/data/catalog/HubMode.kt`

Mendefinisikan mode tingkat aplikasi tanpa membuat tiga Activity atau tiga aplikasi berbeda.

`app/src/main/java/com/example/data/sources/registry/SourceEndpointRegistry.kt`

Menyimpan metadata endpoint per mode. Registry ini sengaja dipisahkan dari parser agar perubahan domain tidak memaksa perubahan UI/engine.

`app/src/main/java/com/example/data/sources/registry/SourceHealthProbe.kt`

Melakukan health check HTTP ringan. HEAD dicoba lebih dahulu, kemudian GET sebagai fallback. Probe tidak mengunduh media.

## Menambahkan source baru

1. Tambahkan endpoint ke `SourceEndpointRegistry`.
2. Buat implementasi `ContentSource`, `BaseWPMangaSource`, `BaseMangaStreamSource`, atau `BaseAnimeSource` yang sesuai.
3. Pastikan setiap `ContentItem` memakai `ContentType` yang benar.
4. Daftarkan source di `OtakuHubApp.reloadDynamicExtensions()` atau extension registry.
5. Uji `search`, `recommendations`, `detail`, dan playback/reader secara terpisah.

## Mengapa mode tidak dipisah menjadi tiga repository?

Mihon/ekosistem extension-style memisahkan *source* dan *app surface*. Dengan satu repository abstraction, library, history, download queue, backup, dan tracker tidak perlu diduplikasi ketika pengguna berpindah Anime → Komik → Drama.

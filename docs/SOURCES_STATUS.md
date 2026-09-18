# Status Sumber & Domain
Dokumen ini memetakan sumber yang sudah ada di proyek, lalu menandai domain yang saat ini
perlu perhatian khusus. Saya tidak menambahkan scraper spesifik untuk situs pihak ketiga
yang berisiko melanggar hak cipta; yang saya tambahkan adalah jalur fallback, diagnosa,
dan mekanisme override URL yang aman.

## Ringkasan
- Proyek ini sudah memakai pola `source`/`extension` yang terpisah per provider.
- Domain yang berubah atau mati ditangani lewat `SourceStatusBus` dan `SourceBaseUrlStore`.
- Saat server menampilkan `Not found`, strategi yang tepat adalah override URL, nonaktifkan source,
  atau migrasikan ke sumber yang sah dan stabil.

## Sumber yang ditandai bermasalah di kode saat ini
| sourceId | default base URL | status |
|---|---|---|
| `${pkgName}_${mangaSource?.id ?: animeSource?.id}` | `` | volatile / hardcoded |
| `anilist` | `` | volatile / hardcoded |
| `apkomik` | `https://01.apkomik.com` | volatile / hardcoded |
| `asurascans` | `https://www.asurascans.com` | volatile / hardcoded |
| `bacami` | `https://bacami.net` | volatile / hardcoded |
| `drakor` | `https://drakor.id` | known-dead |
| `dramaindo` | `https://dramaindo.tv` | known-dead |
| `indomanhwa` | `https://indomanhwa.com` | volatile / hardcoded |
| `komikcast` | `https://v2.komikcast.fit/` | volatile / hardcoded |
| `komikdewasa` | `https://komikdewasa.mom` | volatile / hardcoded |
| `komiknesia` | `https://02.komiknesia.asia` | volatile / hardcoded |
| `komiku` | `https://01.komiku.asia` | volatile / hardcoded |
| `mangadex` | `https://api.mangadex.org/` | volatile / hardcoded |
| `mangatoon_cc` | `https://komik6.mangatoon.cc` | volatile / hardcoded |
| `manhuarmtl` | `https://manhuarmtl.com` | volatile / hardcoded |
| `mgkomik` | `https://web1.mgkomik.cc` | volatile / hardcoded |
| `otakudesu` | `https://otakudesu.cloud` | volatile / hardcoded |
| `samehadaku` | `https://samehadaku.mom` | volatile / hardcoded |
| `shinigami` | `https://g.shinigami.asia` | volatile / hardcoded |
| `sxkomik` | `https://www.sxkomik.art` | volatile / hardcoded |
| `thrivemoe` | `https://thrive.moe` | volatile / hardcoded |
| `yaomic` | `https://www.yaomic.com/id` | volatile / hardcoded |

## Catatan praktis
- Jika domain berubah, pakai menu Profil → Ganti URL Sumber.
- Jika `SourceStatusBus` menampilkan DNS/SSL/timeout error, masalahnya biasanya ada di domain, blokir ISP, atau proteksi anti-bot.
- Untuk source yang Anda kontrol sendiri, pindahkan base URL ke konfigurasi agar bisa diubah tanpa rilis ulang.
- Untuk source pihak ketiga yang tidak Anda miliki haknya, jangan ditambahkan scraper baru; gunakan API resmi atau sumber yang memang diizinkan.

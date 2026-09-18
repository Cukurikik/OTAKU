# Source Status Matrix

## Drama

Dua endpoint berbasis IP pada daftar awal diperlakukan sebagai **legacy/dead candidates**:

- `http://178.128.91.191/`
- `http://134.209.105.11/`

Jangan mengandalkan IP tersebut sebagai canonical source. Gunakan hostname baru bila operator source menyediakan migrasi resmi.

Untuk `viki`, `licil`, `jucelebrity`, dan `lokalfilm`, registry menyimpan endpoint yang diberikan pengguna sebagai `UNKNOWN`; aplikasi harus menjalankan `SourceHealthProbe` sebelum menyebut source aktif.

## Comic / Anime

Endpoint domain juga disimpan sebagai `UNKNOWN` secara default. `UNKNOWN` berarti **belum diverifikasi oleh build**, bukan berarti mati.

### Kenapa status tidak di-hard-code ACTIVE?

Status domain web berubah cepat. Hard-code `ACTIVE` membuat UI menampilkan source sehat padahal server dapat berpindah domain beberapa jam kemudian. Health probe runtime adalah sumber kebenaran untuk konektivitas saat ini.

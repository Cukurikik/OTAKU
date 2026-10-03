package com.example.data.sources.local

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.storage.secure.SecureEncryptedMediaStorage
import okio.Buffer
import java.io.File

/**
 * CoilEncryptedStreamFetcher:
 * Fetcher kustom untuk Coil ImageLoader yang mendekode gambar terenkripsi
 * (.encpack / enc:// / encpack://) langsung dari memori tanpa menulis berkas unencrypted ke disk.
 */
class CoilEncryptedStreamFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {

    private val secureStorage = SecureEncryptedMediaStorage(options.context)

    override suspend fun fetch(): FetchResult? {
        val buffer = Buffer()

        try {
            // 1. Skema encpack://<archive_path>::<entry_name>
            if (data.startsWith("encpack://")) {
                val raw = data.removePrefix("encpack://")
                val parts = raw.split("::")
                if (parts.size != 2) return null
                val archiveFile = File(parts[0])
                val entryName = parts[1]
                val bytes = secureStorage.readEncryptedArchiveEntry(archiveFile, entryName) ?: return null
                buffer.write(bytes)
            }
            // 2. Skema enc://<file_path>
            else if (data.startsWith("enc://")) {
                val rawPath = data.removePrefix("enc://")
                val file = File(rawPath)
                if (!file.exists()) return null
                val bytes = secureStorage.getDecryptedBytes(file)
                if (bytes.isEmpty()) return null
                buffer.write(bytes)
            }
            // 3. Fallback: Berkas terenkripsi langsung (misal .encpack di storage lokal)
            else if (data.endsWith(".encpack") || data.endsWith(".dat") || data.endsWith(".bin")) {
                val file = File(data)
                if (file.exists() && secureStorage.isEncryptedMedia(file)) {
                    val bytes = secureStorage.getDecryptedBytes(file)
                    if (bytes.isEmpty()) return null
                    buffer.write(bytes)
                } else {
                    return null
                }
            } else {
                return null
            }

            if (buffer.size == 0L) return null

            return SourceResult(
                source = ImageSource(buffer, options.context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        } catch (_: Exception) {
            return null
        }
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("enc://") ||
                data.startsWith("encpack://") ||
                data.endsWith(".encpack") ||
                (data.startsWith("/") && (data.endsWith(".dat") || data.endsWith(".bin")))
            ) {
                return CoilEncryptedStreamFetcher(data, options)
            }
            return null
        }
    }
}

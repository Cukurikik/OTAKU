package com.example.data.sources.local

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.buffer
import okio.source
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class CbzFetcher(
    private val data: String,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (!data.startsWith("cbz://")) return null
        val pathAndEntry = data.removePrefix("cbz://")
        val parts = pathAndEntry.split("::")
        if (parts.size != 2) return null

        val pathOrUri = parts[0]
        val entryName = parts[1]
        val buffer = Buffer()

        try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                val inputStream = options.context.contentResolver.openInputStream(uri) ?: return null
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    var matched = false
                    while (entry != null) {
                        val name = entry.name
                        val lower = name.lowercase()
                        val isImage = !entry.isDirectory && (
                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                            lower.endsWith(".png") || lower.endsWith(".webp") || 
                            lower.endsWith(".gif") || lower.endsWith(".avif") ||
                            lower.endsWith(".dat") || lower.endsWith(".bin")
                        )

                        val entryBase = entryName.substringBeforeLast(".")
                        val nameBase = name.substringBeforeLast(".")

                        if (entryName == "cover" && isImage) {
                            zis.source().buffer().readAll(buffer)
                            matched = true
                            break
                        } else if (name == entryName || (entryBase.isNotBlank() && nameBase == entryBase && isImage)) {
                            zis.source().buffer().readAll(buffer)
                            matched = true
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    if (!matched && entryName == "cover" && buffer.size == 0L) {
                        return null
                    }
                }
            } else {
                val file = File(pathOrUri)
                if (!file.exists()) return null

                ZipFile(file).use { zipFile ->
                    if (entryName == "cover") {
                        val entry = zipFile.entries().toList()
                            .filter { !it.isDirectory && (
                                it.name.lowercase().endsWith(".jpg") || it.name.lowercase().endsWith(".jpeg") || 
                                it.name.lowercase().endsWith(".png") || it.name.lowercase().endsWith(".webp") || 
                                it.name.lowercase().endsWith(".avif") || it.name.lowercase().endsWith(".dat") || 
                                it.name.lowercase().endsWith(".bin")
                            ) }
                            .minByOrNull { it.name }
                        if (entry != null) {
                            zipFile.getInputStream(entry).use { it.source().buffer().readAll(buffer) }
                        }
                    } else {
                        var entry = zipFile.getEntry(entryName)
                        if (entry == null) {
                            val entryBase = entryName.substringBeforeLast(".")
                            entry = zipFile.entries().toList().firstOrNull { 
                                !it.isDirectory && it.name.substringBeforeLast(".") == entryBase 
                            }
                        }
                        if (entry != null) {
                            zipFile.getInputStream(entry).use { it.source().buffer().readAll(buffer) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        if (buffer.size == 0L) {
            return null
        }

        return SourceResult(
            source = ImageSource(
                source = buffer,
                context = options.context,
                metadata = null
            ),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.startsWith("cbz://")) {
                return CbzFetcher(data, options)
            }
            return null
        }
    }
}


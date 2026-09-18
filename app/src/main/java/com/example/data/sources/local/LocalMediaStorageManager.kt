package com.example.data.sources.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class LocalFolder(
    val uriString: String,
    val name: String,
    val type: ContentType // COMIC or ANIME
)

class LocalMediaStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("local_media_storage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MOUNTED_FOLDERS = "key_mounted_saf_folders"
        val SUPPORTED_COMIC_EXTENSIONS = setOf("cbz", "zip")
        val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "dat", "bin")
        val SUPPORTED_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "ts", "mov", "flv", "dat")
    }

    fun getMountedFolders(): List<LocalFolder> {
        val rawSet = prefs.getStringSet(KEY_MOUNTED_FOLDERS, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { entry ->
            val parts = entry.split("|::|")
            if (parts.size >= 3) {
                val type = try { ContentType.valueOf(parts[2]) } catch (e: Exception) { ContentType.COMIC }
                LocalFolder(parts[0], parts[1], type)
            } else null
        }
    }

    fun addMountedFolder(uri: Uri, name: String, type: ContentType) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val current = getMountedFolders().toMutableList()
        current.removeAll { it.uriString == uri.toString() }
        current.add(LocalFolder(uri.toString(), name, type))
        saveMountedFolders(current)
    }

    fun removeMountedFolder(uriString: String) {
        val current = getMountedFolders().toMutableList()
        current.removeAll { it.uriString == uriString }
        saveMountedFolders(current)
    }

    private fun saveMountedFolders(folders: List<LocalFolder>) {
        val rawSet = folders.map { "${it.uriString}|::|${it.name}|::|${it.type.name}" }.toSet()
        prefs.edit().putStringSet(KEY_MOUNTED_FOLDERS, rawSet).apply()
    }

    /**
     * Scans all mounted SAF folders plus default Download & Documents directories.
     */
    fun scanAllLocalContent(): List<ContentItem> {
        val results = mutableListOf<ContentItem>()

        // 1. Scan default Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() && downloadsDir.isDirectory) {
            results.addAll(scanStandardDirectory(downloadsDir))
        }

        // 2. Scan default OtakuHub Downloads/Comics/Anime directory if exists
        val appExternalDir = context.getExternalFilesDir(null)
        if (appExternalDir != null && appExternalDir.exists()) {
            results.addAll(scanStandardDirectory(appExternalDir))
        }

        // 3. Scan SAF mounted folders
        val mounted = getMountedFolders()
        for (folder in mounted) {
            try {
                val treeUri = Uri.parse(folder.uriString)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                if (docDir != null && docDir.exists() && docDir.isDirectory) {
                    results.addAll(scanDocumentFolder(docDir, folder.type))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Distinct by item ID
        return results.distinctBy { it.id }
    }

    private fun scanStandardDirectory(dir: File): List<ContentItem> {
        val list = mutableListOf<ContentItem>()
        val files = dir.listFiles() ?: return list

        for (file in files) {
            if (file.isDirectory) {
                // Check if directory is a manga with multiple CBZs or images
                val subFiles = file.listFiles() ?: emptyArray()
                val cbzFiles = subFiles.filter { it.extension.lowercase() in SUPPORTED_COMIC_EXTENSIONS }
                val imageFiles = subFiles.filter { it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS }
                val videoFiles = subFiles.filter { it.extension.lowercase() in SUPPORTED_VIDEO_EXTENSIONS }

                if (cbzFiles.isNotEmpty() || imageFiles.isNotEmpty()) {
                    val cover = if (cbzFiles.isNotEmpty()) {
                        "cbz://${cbzFiles.first().absolutePath}::cover"
                    } else {
                        "file://${imageFiles.first().absolutePath}"
                    }
                    list.add(
                        ContentItem(
                            id = "local_dir:${file.absolutePath}",
                            title = file.name,
                            source = "Penyimpanan Lokal",
                            imageUrl = cover,
                            type = ContentType.COMIC,
                            description = "Folder Komik: ${file.name}\n${cbzFiles.size} Bab / Berkas",
                            sourceId = "local_storage"
                        )
                    )
                } else if (videoFiles.isNotEmpty()) {
                    list.add(
                        ContentItem(
                            id = "local_video_dir:${file.absolutePath}",
                            title = file.name,
                            source = "Penyimpanan Lokal",
                            imageUrl = "",
                            type = ContentType.ANIME,
                            description = "Folder Seri Video: ${file.name}\n${videoFiles.size} Episode",
                            sourceId = "local_storage"
                        )
                    )
                }
            } else {
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_COMIC_EXTENSIONS) {
                    val comicInfo = getComicInfoFromFile(file)
                    val title = comicInfo?.series ?: comicInfo?.title ?: file.nameWithoutExtension
                    val desc = buildString {
                        if (!comicInfo?.summary.isNullOrBlank()) appendLine(comicInfo!!.summary)
                        if (!comicInfo?.author.isNullOrBlank()) appendLine("Author: ${comicInfo!!.author}")
                        if (!comicInfo?.genre.isNullOrBlank()) appendLine("Genre: ${comicInfo!!.genre}")
                        append("Berkas: ${file.name} (${file.length() / (1024 * 1024)} MB)")
                    }

                    list.add(
                        ContentItem(
                            id = "local_file:${file.absolutePath}",
                            title = title,
                            source = "Penyimpanan Lokal",
                            imageUrl = "cbz://${file.absolutePath}::cover",
                            type = ContentType.COMIC,
                            description = desc,
                            sourceId = "local_storage"
                        )
                    )
                } else if (ext in SUPPORTED_VIDEO_EXTENSIONS) {
                    list.add(
                        ContentItem(
                            id = "local_video_file:${file.absolutePath}",
                            title = file.nameWithoutExtension,
                            source = "Penyimpanan Lokal",
                            imageUrl = "",
                            type = ContentType.ANIME,
                            description = "Berkas Video: ${file.name} (${file.length() / (1024 * 1024)} MB)",
                            sourceId = "local_storage"
                        )
                    )
                }
            }
        }
        return list
    }

    private fun scanDocumentFolder(docDir: DocumentFile, defaultType: ContentType): List<ContentItem> {
        val list = mutableListOf<ContentItem>()
        val files = docDir.listFiles()

        for (file in files) {
            val name = file.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()

            if (file.isDirectory) {
                val subFiles = file.listFiles()
                val cbzFiles = subFiles.filter { it.name?.substringAfterLast('.', "")?.lowercase() in SUPPORTED_COMIC_EXTENSIONS }
                val videoFiles = subFiles.filter { it.name?.substringAfterLast('.', "")?.lowercase() in SUPPORTED_VIDEO_EXTENSIONS }
                val imageFiles = subFiles.filter { it.name?.substringAfterLast('.', "")?.lowercase() in SUPPORTED_IMAGE_EXTENSIONS }

                if (cbzFiles.isNotEmpty() || imageFiles.isNotEmpty()) {
                    val cover = if (cbzFiles.isNotEmpty()) {
                        "cbz://${cbzFiles.first().uri}::cover"
                    } else {
                        imageFiles.first().uri.toString()
                    }
                    list.add(
                        ContentItem(
                            id = "saf_dir:${file.uri}",
                            title = name,
                            source = "Penyimpanan Lokal",
                            imageUrl = cover,
                            type = ContentType.COMIC,
                            description = "Folder Dokumen: $name\n${cbzFiles.size + imageFiles.size} Berkas",
                            sourceId = "local_storage"
                        )
                    )
                } else if (videoFiles.isNotEmpty()) {
                    list.add(
                        ContentItem(
                            id = "saf_video_dir:${file.uri}",
                            title = name,
                            source = "Penyimpanan Lokal",
                            imageUrl = "",
                            type = ContentType.ANIME,
                            description = "Folder Seri Video: $name\n${videoFiles.size} Episode",
                            sourceId = "local_storage"
                        )
                    )
                }
            } else {
                if (ext in SUPPORTED_COMIC_EXTENSIONS) {
                    val comicInfo = getComicInfoFromUri(file.uri)
                    val title = comicInfo?.series ?: comicInfo?.title ?: name.substringBeforeLast('.')
                    val desc = buildString {
                        if (!comicInfo?.summary.isNullOrBlank()) appendLine(comicInfo!!.summary)
                        if (!comicInfo?.author.isNullOrBlank()) appendLine("Author: ${comicInfo!!.author}")
                        if (!comicInfo?.genre.isNullOrBlank()) appendLine("Genre: ${comicInfo!!.genre}")
                        append("Berkas: $name (${file.length() / (1024 * 1024)} MB)")
                    }

                    list.add(
                        ContentItem(
                            id = "saf_file:${file.uri}",
                            title = title,
                            source = "Penyimpanan Lokal",
                            imageUrl = "cbz://${file.uri}::cover",
                            type = ContentType.COMIC,
                            description = desc,
                            sourceId = "local_storage"
                        )
                    )
                } else if (ext in SUPPORTED_VIDEO_EXTENSIONS) {
                    list.add(
                        ContentItem(
                            id = "saf_video_file:${file.uri}",
                            title = name.substringBeforeLast('.'),
                            source = "Penyimpanan Lokal",
                            imageUrl = "",
                            type = ContentType.ANIME,
                            description = "Berkas Video: $name (${file.length() / (1024 * 1024)} MB)",
                            sourceId = "local_storage"
                        )
                    )
                }
            }
        }
        return list
    }

    fun getChaptersForItem(itemId: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        when {
            itemId.startsWith("local_file:") -> {
                val path = itemId.removePrefix("local_file:")
                val file = File(path)
                if (file.exists()) {
                    val comicInfo = getComicInfoFromFile(file)
                    val chTitle = comicInfo?.title ?: comicInfo?.number?.let { "Chapter $it" } ?: file.nameWithoutExtension
                    chapters.add(Chapter(id = itemId, title = chTitle, url = itemId))
                }
            }
            itemId.startsWith("local_dir:") -> {
                val path = itemId.removePrefix("local_dir:")
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val subFiles = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                    for (file in subFiles) {
                        val ext = file.extension.lowercase()
                        if (ext in SUPPORTED_COMIC_EXTENSIONS) {
                            val comicInfo = getComicInfoFromFile(file)
                            val chTitle = comicInfo?.title ?: comicInfo?.number?.let { "Chapter $it" } ?: file.nameWithoutExtension
                            chapters.add(Chapter(id = "local_file:${file.absolutePath}", title = chTitle, url = "local_file:${file.absolutePath}"))
                        } else if (file.isDirectory) {
                            val imgCount = file.listFiles()?.count { it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS } ?: 0
                            if (imgCount > 0) {
                                chapters.add(Chapter(id = "local_img_dir:${file.absolutePath}", title = file.name, url = "local_img_dir:${file.absolutePath}"))
                            }
                        }
                    }
                    if (chapters.isEmpty()) {
                        // Dir itself contains images directly
                        val imgCount = dir.listFiles()?.count { it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS } ?: 0
                        if (imgCount > 0) {
                            chapters.add(Chapter(id = "local_img_dir:${dir.absolutePath}", title = dir.name, url = "local_img_dir:${dir.absolutePath}"))
                        }
                    }
                }
            }
            itemId.startsWith("saf_file:") -> {
                val uriStr = itemId.removePrefix("saf_file:")
                val uri = Uri.parse(uriStr)
                val doc = DocumentFile.fromSingleUri(context, uri)
                val comicInfo = getComicInfoFromUri(uri)
                val name = doc?.name ?: "Chapter 1"
                val chTitle = comicInfo?.title ?: comicInfo?.number?.let { "Chapter $it" } ?: name.substringBeforeLast('.')
                chapters.add(Chapter(id = itemId, title = chTitle, url = itemId))
            }
            itemId.startsWith("saf_dir:") -> {
                val uriStr = itemId.removePrefix("saf_dir:")
                val uri = Uri.parse(uriStr)
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir != null && dir.exists()) {
                    val files = dir.listFiles().sortedBy { it.name }
                    for (file in files) {
                        val name = file.name ?: continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in SUPPORTED_COMIC_EXTENSIONS) {
                            val comicInfo = getComicInfoFromUri(file.uri)
                            val chTitle = comicInfo?.title ?: comicInfo?.number?.let { "Chapter $it" } ?: name.substringBeforeLast('.')
                            chapters.add(Chapter(id = "saf_file:${file.uri}", title = chTitle, url = "saf_file:${file.uri}"))
                        } else if (file.isDirectory) {
                            chapters.add(Chapter(id = "saf_img_dir:${file.uri}", title = name, url = "saf_img_dir:${file.uri}"))
                        }
                    }
                }
            }
            itemId.startsWith("local_video_file:") -> {
                val path = itemId.removePrefix("local_video_file:")
                val file = File(path)
                if (file.exists()) {
                    chapters.add(Chapter(id = itemId, title = file.nameWithoutExtension, url = itemId))
                }
            }
            itemId.startsWith("local_video_dir:") -> {
                val path = itemId.removePrefix("local_video_dir:")
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()?.filter { it.extension.lowercase() in SUPPORTED_VIDEO_EXTENSIONS }?.sortedBy { it.name } ?: emptyList()
                    files.forEach { file ->
                        chapters.add(Chapter(id = "local_video_file:${file.absolutePath}", title = file.nameWithoutExtension, url = "local_video_file:${file.absolutePath}"))
                    }
                }
            }
            itemId.startsWith("saf_video_file:") -> {
                val uriStr = itemId.removePrefix("saf_video_file:")
                val uri = Uri.parse(uriStr)
                val doc = DocumentFile.fromSingleUri(context, uri)
                chapters.add(Chapter(id = itemId, title = doc?.name?.substringBeforeLast('.') ?: "Video", url = itemId))
            }
            itemId.startsWith("saf_video_dir:") -> {
                val uriStr = itemId.removePrefix("saf_video_dir:")
                val uri = Uri.parse(uriStr)
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir != null && dir.exists()) {
                    val files = dir.listFiles().filter {
                        val ext = it.name?.substringAfterLast('.', "")?.lowercase()
                        ext in SUPPORTED_VIDEO_EXTENSIONS
                    }.sortedBy { it.name }
                    files.forEach { file ->
                        val name = file.name?.substringBeforeLast('.') ?: "Video Episode"
                        chapters.add(Chapter(id = "saf_video_file:${file.uri}", title = name, url = "saf_video_file:${file.uri}"))
                    }
                }
            }
        }
        return chapters
    }

    fun getPagesForChapter(chapterId: String): List<Page> {
        val pages = mutableListOf<Page>()
        when {
            chapterId.startsWith("local_file:") -> {
                val path = chapterId.removePrefix("local_file:")
                val file = File(path)
                if (file.exists()) {
                    try {
                        ZipFile(file).use { zip ->
                            val entries = zip.entries().toList().filter {
                                val n = it.name.lowercase()
                                !it.isDirectory && SUPPORTED_IMAGE_EXTENSIONS.any { ext -> n.endsWith(".$ext") }
                            }.sortedBy { it.name }

                            for (entry in entries) {
                                pages.add(Page(url = "cbz://${file.absolutePath}::${entry.name}"))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            chapterId.startsWith("saf_file:") -> {
                val uriStr = chapterId.removePrefix("saf_file:")
                val uri = Uri.parse(uriStr)
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val entryNames = mutableListOf<String>()
                        ZipInputStream(inputStream).use { zis ->
                            var entry: ZipEntry? = zis.nextEntry
                            while (entry != null) {
                                val n = entry.name.lowercase()
                                if (!entry.isDirectory && SUPPORTED_IMAGE_EXTENSIONS.any { ext -> n.endsWith(".$ext") }) {
                                    entryNames.add(entry.name)
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                        entryNames.sorted().forEach { name ->
                            pages.add(Page(url = "cbz://$uriStr::$name"))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            chapterId.startsWith("local_img_dir:") -> {
                val path = chapterId.removePrefix("local_img_dir:")
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val imgFiles = dir.listFiles()?.filter {
                        it.extension.lowercase() in SUPPORTED_IMAGE_EXTENSIONS
                    }?.sortedBy { it.name } ?: emptyList()
                    imgFiles.forEach { img ->
                        pages.add(Page(url = "file://${img.absolutePath}"))
                    }
                }
            }
            chapterId.startsWith("saf_img_dir:") -> {
                val uriStr = chapterId.removePrefix("saf_img_dir:")
                val uri = Uri.parse(uriStr)
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir != null && dir.exists()) {
                    val imgFiles = dir.listFiles().filter {
                        val ext = it.name?.substringAfterLast('.', "")?.lowercase()
                        ext in SUPPORTED_IMAGE_EXTENSIONS
                    }.sortedBy { it.name }
                    imgFiles.forEach { doc ->
                        pages.add(Page(url = doc.uri.toString()))
                    }
                }
            }
        }
        return pages
    }

    fun getVideoUri(episodeId: String): String {
        return when {
            episodeId.startsWith("local_video_file:") -> {
                val path = episodeId.removePrefix("local_video_file:")
                "file://$path"
            }
            episodeId.startsWith("saf_video_file:") -> {
                episodeId.removePrefix("saf_video_file:")
            }
            else -> episodeId
        }
    }

    private fun getComicInfoFromFile(file: File): ComicInfo? {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("ComicInfo.xml") ?: zip.entries().toList().firstOrNull { it.name.equals("ComicInfo.xml", ignoreCase = true) }
                if (entry != null) {
                    zip.getInputStream(entry).use { isStream ->
                        ComicInfoParser.parse(isStream)
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getComicInfoFromUri(uri: Uri): ComicInfo? {
        return try {
            val isStream = context.contentResolver.openInputStream(uri) ?: return null
            ZipInputStream(isStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("ComicInfo.xml", ignoreCase = true)) {
                        return ComicInfoParser.parse(zis)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

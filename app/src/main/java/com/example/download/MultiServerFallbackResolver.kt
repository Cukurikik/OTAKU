package com.example.download

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.data.NetworkModule
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.network.resolver.AnimeMultiServerResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * MultiServerFallbackResolver:
 * Modul verifikasi yang secara otomatis beralih ke server cermin (mirror server)
 * saat server utama mengembalikan kode status kesalahan HTTP (404, 403, 500, timeout).
 *
 * Formulasi:
 * Server_active = argmin_{s in Servers} (Latency(s) * I(Status(s) == ONLINE))
 * Path_destination = BaseDir + "/" + Sanitize(Title) + "/Episode_" + FormatTwoDigits(EpNum) + ".mp4"
 */
class MultiServerFallbackResolver(
    private val context: Context,
    private val extractorEngine: VideoExtractorEngine = VideoExtractorEngine(context),
    private val multiServerResolver: AnimeMultiServerResolver = AnimeMultiServerResolver(context)
) {

    companion object {
        private const val TAG = "MultiServerResolver"

        /**
         * Membersihkan karakter tidak valid dari judul agar aman untuk direktori/berkas.
         */
        fun sanitizeTitle(title: String): String {
            return title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        /**
         * Format nomor episode menjadi 2 digit atau lebih (misal: "01", "09", "12", "100").
         */
        fun formatTwoDigits(episodeNum: Int): String {
            return if (episodeNum in 0..9) "0$episodeNum" else "$episodeNum"
        }

        /**
         * Menghitung path destinasi terstruktur:
         * Path = BaseDir / Sanitize(Title) / Episode_XX.mp4
         */
        fun getDestinationPath(baseDir: File, animeTitle: String, episodeNum: Int): File {
            val sanitized = sanitizeTitle(animeTitle)
            val seriesDir = File(baseDir, sanitized)
            if (!seriesDir.exists()) {
                seriesDir.mkdirs()
            }
            val fileName = "Episode_${formatTwoDigits(episodeNum)}.mp4"
            return File(seriesDir, fileName)
        }
    }

    data class ResolvedStream(
        val directUrl: String,
        val isM3U8: Boolean,
        val selectedQuality: String,
        val serverName: String,
        val headers: Map<String, String>,
        val latencyMs: Long
    )

    /**
     * Menyelesaikan stream video yang optimal berdasarkan kualitas target dan keandalan server.
     */
    suspend fun resolveBestStream(
        servers: List<VideoServer>,
        targetQuality: String = "720p",
        headers: Map<String, String> = emptyMap()
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        if (servers.isEmpty()) return@withContext null

        val sortedServers = multiServerResolver.sortServersByReliability(servers)

        for (server in sortedServers) {
            val resolvedServer = multiServerResolver.resolveServerStream(server, headers)
            val candidateQualities = resolvedServer.qualities.ifEmpty {
                if (resolvedServer.embedUrl.endsWith(".mp4") || resolvedServer.embedUrl.endsWith(".m3u8")) {
                    listOf(VideoQuality(targetQuality, resolvedServer.embedUrl, resolvedServer.embedUrl.contains(".m3u8"), headers))
                } else emptyList()
            }

            if (candidateQualities.isEmpty()) continue

            // Pilih kualitas yang paling mendekati targetQuality
            val chosenQuality = pickQualityMatch(candidateQualities, targetQuality) ?: candidateQualities.first()
            val effectiveHeaders = if (chosenQuality.headers.isNotEmpty()) chosenQuality.headers else resolvedServer.headers.ifEmpty { headers }

            // Verifikasi status koneksi dan latency (Health Check)
            val latency = probeStreamLatency(chosenQuality.url, effectiveHeaders)
            if (latency >= 0) {
                Log.d(TAG, "Server '${resolvedServer.name}' ONLINE dengan latency: ${latency}ms, stream: ${chosenQuality.url}")
                return@withContext ResolvedStream(
                    directUrl = chosenQuality.url,
                    isM3U8 = chosenQuality.isM3U8 || chosenQuality.url.contains(".m3u8"),
                    selectedQuality = chosenQuality.qualityLabel,
                    serverName = resolvedServer.name,
                    headers = effectiveHeaders,
                    latencyMs = latency
                )
            } else {
                Log.w(TAG, "Server '${resolvedServer.name}' OFFLINE / Dead Link (${chosenQuality.url}). Melanjutkan ke fallback server...")
            }
        }

        null
    }

    /**
     * Memilih resolusi yang paling mendekati preferensi pengguna.
     */
    fun pickQualityMatch(qualities: List<VideoQuality>, targetQuality: String): VideoQuality? {
        if (qualities.isEmpty()) return null
        if (targetQuality.equals("Auto", ignoreCase = true)) {
            return qualities.maxByOrNull { it.qualityLabel.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 } ?: qualities.first()
        }

        val targetNum = targetQuality.filter { it.isDigit() }.toIntOrNull() ?: 720
        // Exact match
        val exact = qualities.firstOrNull { it.qualityLabel.contains(targetQuality, ignoreCase = true) }
        if (exact != null) return exact

        // Nearest match <= targetNum
        val underOrEqual = qualities.filter {
            val num = it.qualityLabel.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
            num in 1..targetNum
        }.maxByOrNull { it.qualityLabel.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

        if (underOrEqual != null) return underOrEqual

        // Nearest match overall
        return qualities.minByOrNull {
            val num = it.qualityLabel.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
            kotlin.math.abs(num - targetNum)
        }
    }

    /**
     * Menguji kelangsungan URL stream (HEAD / Range request) dan mengukur latensi.
     * Mengembalikan milidetik latency, atau -1 jika gagal/dead link.
     */
    suspend fun probeStreamLatency(streamUrl: String, headers: Map<String, String>): Long = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        try {
            val reqBuilder = Request.Builder()
                .url(streamUrl)
                .header("Range", "bytes=0-1024")
                .header("User-Agent", NetworkModule.userAgent)

            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val response = NetworkModule.okHttpClient.newCall(reqBuilder.build()).execute()
            val code = response.code
            val isSuccess = response.isSuccessful || code == 206 || code == 302 || code == 301
            response.close()

            if (isSuccess) {
                SystemClock.elapsedRealtime() - start
            } else {
                -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }
}

package com.example.casting

import android.util.Log
import com.example.data.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/**
 * Status pemutaran media DLNA pada TV.
 */
data class DlnaPlaybackState(
    val state: String = "STOPPED", // PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * DlnaCastManager:
 * Mengirimkan perintah SOAP XML UPnP AVTransport dan RenderingControl ke Smart TV
 * (Samsung Tizen, LG WebOS, Roku, Sony, dll.).
 */
object DlnaCastManager {
    private const val TAG = "DlnaCastManager"

    private const val SERVICE_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val SERVICE_RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    /**
     * Memutar media pada TV target.
     * Menghentikan playback lama jika ada, lalu memanggil SetAVTransportURI dan Play.
     */
    suspend fun playMediaOnTv(
        controlUrl: String,
        mediaUrl: String,
        title: String,
        mimeType: String = "video/mp4"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Coba hentikan playback sebelumnya
            try {
                stop(controlUrl)
            } catch (_: Exception) {}

            // 2. Siapkan DIDL-Lite Metadata
            val didlMetadata = buildDidlMetadata(mediaUrl, title, mimeType)

            // 3. Panggil SetAVTransportURI
            val setUriArgs = mapOf(
                "InstanceID" to "0",
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to didlMetadata
            )
            val setUriResult = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "SetAVTransportURI", setUriArgs)
            if (setUriResult.isFailure) {
                return@withContext Result.failure(setUriResult.exceptionOrNull() ?: Exception("SetAVTransportURI failed"))
            }

            // 4. Panggil Play
            val playArgs = mapOf(
                "InstanceID" to "0",
                "Speed" to "1"
            )
            val playResult = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "Play", playArgs)
            if (playResult.isFailure) {
                return@withContext Result.failure(playResult.exceptionOrNull() ?: Exception("Play failed"))
            }

            Log.i(TAG, "Berhasil memutar media di TV: $title -> $controlUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar media di TV: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Menjeda (pause) pemutaran di TV.
     */
    suspend fun pause(controlUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val args = mapOf("InstanceID" to "0")
        val res = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "Pause", args)
        if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Pause failed"))
    }

    /**
     * Melanjutkan (resume) pemutaran di TV.
     */
    suspend fun resume(controlUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val args = mapOf(
            "InstanceID" to "0",
            "Speed" to "1"
        )
        val res = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "Play", args)
        if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Play failed"))
    }

    /**
     * Menghentikan pemutaran di TV.
     */
    suspend fun stop(controlUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        val args = mapOf("InstanceID" to "0")
        val res = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "Stop", args)
        if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Stop failed"))
    }

    /**
     * Melakukan seeking posisi pemutaran di TV dalam milidetik.
     */
    suspend fun seek(controlUrl: String, positionMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val targetTime = formatDuration(positionMs)
        val args = mapOf(
            "InstanceID" to "0",
            "Unit" to "REL_TIME",
            "Target" to targetTime
        )
        val res = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "Seek", args)
        if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("Seek failed"))
    }

    /**
     * Mengambil informasi posisi pemutaran dan durasi saat ini dari TV.
     */
    suspend fun getPositionInfo(controlUrl: String): Result<DlnaPlaybackState> = withContext(Dispatchers.IO) {
        val args = mapOf("InstanceID" to "0")
        val res = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "GetPositionInfo", args)
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("GetPositionInfo failed"))
        }

        val xml = res.getOrNull() ?: ""
        val trackDuration = extractXmlTag(xml, "TrackDuration")
        val relTime = extractXmlTag(xml, "RelTime")

        val durationMs = parseDuration(trackDuration)
        val currentPositionMs = parseDuration(relTime)

        // Dapatkan juga status transport
        val stateRes = sendSoapAction(controlUrl, SERVICE_AV_TRANSPORT, "GetTransportInfo", args)
        val stateXml = stateRes.getOrNull() ?: ""
        val transportState = extractXmlTag(stateXml, "CurrentTransportState").ifBlank { "PLAYING" }

        Result.success(
            DlnaPlaybackState(
                state = transportState,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs
            )
        )
    }

    /**
     * Mengatur volume pada TV target (jika RenderingControl URL tersedia).
     * Rentang volume: 0 hingga 100.
     */
    suspend fun setVolume(renderingControlUrl: String, volumePercent: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val clamped = volumePercent.coerceIn(0, 100)
        val args = mapOf(
            "InstanceID" to "0",
            "Channel" to "Master",
            "DesiredVolume" to clamped.toString()
        )
        val res = sendSoapAction(renderingControlUrl, SERVICE_RENDERING_CONTROL, "SetVolume", args)
        if (res.isSuccess) Result.success(Unit) else Result.failure(res.exceptionOrNull() ?: Exception("SetVolume failed"))
    }

    /**
     * Mengirimkan SOAP Action request via HTTP POST.
     */
    private fun sendSoapAction(
        endpointUrl: String,
        serviceType: String,
        action: String,
        arguments: Map<String, String>
    ): Result<String> {
        return try {
            val soapBody = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n")
                append("  <s:Body>\n")
                append("    <u:$action xmlns:u=\"$serviceType\">\n")
                arguments.forEach { (key, value) ->
                    append("      <$key>$value</$key>\n")
                }
                append("    </u:$action>\n")
                append("  </s:Body>\n")
                append("</s:Envelope>")
            }

            val mediaType = "text/xml; charset=\"utf-8\"".toMediaType()
            val requestBody = soapBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpointUrl)
                .header("SOAPAction", "\"$serviceType#$action\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("User-Agent", "DLNADOC/1.50 UPnP/1.0")
                .post(requestBody)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success(responseBody)
            } else {
                val faultCode = extractXmlTag(responseBody, "faultcode")
                val faultString = extractXmlTag(responseBody, "faultstring")
                val errorMsg = if (faultString.isNotBlank()) "$faultCode: $faultString" else "HTTP ${response.code}"
                Log.w(TAG, "SOAP $action gagal di $endpointUrl: $errorMsg")
                Result.failure(Exception("SOAP $action failed: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception SOAP $action: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Membangun format DIDL-Lite Metadata XML ter-escape untuk kompatibilitas Smart TV.
     */
    fun buildDidlMetadata(mediaUrl: String, title: String, mimeType: String): String {
        val escapedTitle = escapeXml(title)
        val escapedUrl = escapeXml(mediaUrl)
        val upnpClass = if (mimeType.startsWith("image/")) "object.item.imageItem.photo" else "object.item.videoItem.movie"

        val innerXml = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>$escapedTitle</dc:title><upnp:class>$upnpClass</upnp:class><res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res></item></DIDL-Lite>"""

        return escapeXml(innerXml)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun extractXmlTag(xml: String, tag: String): String {
        val startPattern = "<$tag"
        val endPattern = "</$tag>"
        val startIndex = xml.indexOf(startPattern)
        if (startIndex == -1) return ""
        val tagCloseIndex = xml.indexOf('>', startIndex)
        if (tagCloseIndex == -1) return ""
        val endIndex = xml.indexOf(endPattern, tagCloseIndex)
        if (endIndex == -1) return ""
        return xml.substring(tagCloseIndex + 1, endIndex).trim()
    }

    /**
     * Mengubah durasi milidetik menjadi string HH:mm:ss.
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Mengubah string HH:mm:ss menjadi durasi milidetik.
     */
    fun parseDuration(timeStr: String): Long {
        if (timeStr.isBlank()) return 0L
        val parts = timeStr.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toDouble().toLong()
                    val m = parts[1].toDouble().toLong()
                    val s = parts[2].toDouble().toLong()
                    (h * 3600 + m * 60 + s) * 1000L
                }
                2 -> {
                    val m = parts[0].toDouble().toLong()
                    val s = parts[1].toDouble().toLong()
                    (m * 60 + s) * 1000L
                }
                1 -> (parts[0].toDouble().toLong()) * 1000L
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}

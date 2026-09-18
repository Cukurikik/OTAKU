package com.example.casting

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Representasi Smart TV atau perangkat DLNA MediaRenderer yang ditemukan di jaringan lokal.
 */
data class DlnaDevice(
    val id: String,
    val name: String,
    val locationUrl: String,
    val controlUrl: String,
    val renderingControlUrl: String? = null,
    val manufacturer: String = "",
    val modelName: String = "",
    val iconUrl: String? = null
)

/**
 * SsdpDiscoveryManager:
 * Menemukan Smart TV (Samsung Tizen, LG WebOS, Roku, Sony, Android TV, dll.)
 * menggunakan protokol SSDP (Simple Service Discovery Protocol) melalui UDP Multicast (239.255.255.250:1900).
 */
class SsdpDiscoveryManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_IP = "239.255.255.250"
        private const val SSDP_PORT = 1900

        // Target pencarian DLNA AVTransport dan MediaRenderer
        private const val ST_AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val ST_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        /**
         * Mem-parsing deskripsi XML UPnP untuk mengekstrak friendlyName dan AVTransport controlURL.
         * Menggunakan regex yang tangguh agar kompatibel di Android runtime maupun JVM unit tests.
         */
        fun parseDeviceXml(deviceId: String, locationUrl: String, xmlContent: String): DlnaDevice? {
            return try {
                fun extractTag(xml: String, tag: String): String {
                    val regex = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.IGNORE_CASE)
                    return regex.find(xml)?.groupValues?.get(1)?.trim() ?: ""
                }

                val friendlyName = extractTag(xmlContent, "friendlyName")
                val manufacturer = extractTag(xmlContent, "manufacturer")
                val modelName = extractTag(xmlContent, "modelName")

                // Ekstrak URL ikon jika ada
                val iconBlockRegex = Regex("<icon[^>]*>(.*?)</icon>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val iconBlock = iconBlockRegex.find(xmlContent)?.groupValues?.get(1) ?: ""
                val iconUrl = if (iconBlock.isNotBlank()) extractTag(iconBlock, "url") else null

                // Ekstrak services
                var avTransportControlUrl = ""
                var renderingControlUrl = ""

                val serviceRegex = Regex("<service[^>]*>(.*?)</service>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                for (match in serviceRegex.findAll(xmlContent)) {
                    val serviceBlock = match.groupValues[1]
                    val serviceType = extractTag(serviceBlock, "serviceType")
                    val controlUrl = extractTag(serviceBlock, "controlURL")

                    if (serviceType.contains("AVTransport", ignoreCase = true) && avTransportControlUrl.isBlank()) {
                        avTransportControlUrl = controlUrl
                    } else if (serviceType.contains("RenderingControl", ignoreCase = true) && renderingControlUrl.isBlank()) {
                        renderingControlUrl = controlUrl
                    }
                }

                if (avTransportControlUrl.isNotBlank()) {
                    val resolvedControlUrl = resolveUrl(locationUrl, avTransportControlUrl)
                    val resolvedRenderingUrl = if (renderingControlUrl.isNotBlank()) resolveUrl(locationUrl, renderingControlUrl) else null
                    val resolvedIconUrl = if (!iconUrl.isNullOrBlank()) resolveUrl(locationUrl, iconUrl) else null

                    DlnaDevice(
                        id = deviceId,
                        name = friendlyName.ifBlank { "Smart TV ($modelName)" },
                        locationUrl = locationUrl,
                        controlUrl = resolvedControlUrl,
                        renderingControlUrl = resolvedRenderingUrl,
                        manufacturer = manufacturer,
                        modelName = modelName,
                        iconUrl = resolvedIconUrl
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing XML: ${e.message}")
                null
            }
        }

        fun resolveUrl(baseLocation: String, path: String): String {
            return try {
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    path
                } else {
                    val base = URI(baseLocation)
                    base.resolve(path).toString()
                }
            } catch (_: Exception) {
                path
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, DlnaDevice>()

    /**
     * Memulai pemindaian perangkat Smart TV pada jaringan Wi-Fi lokal.
     */
    fun startScan(timeoutSeconds: Int = 6) {
        if (_isScanning.value) return
        _isScanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()

        scanJob = scope.launch {
            acquireMulticastLock()
            try {
                // Kirim M-SEARCH queries untuk AVTransport dan MediaRenderer
                launch { sendMsearchQuery(ST_AV_TRANSPORT, timeoutSeconds) }
                launch { sendMsearchQuery(ST_MEDIA_RENDERER, timeoutSeconds) }

                delay(timeoutSeconds * 1000L)
            } catch (e: Exception) {
                Log.e(TAG, "Error saat pemindaian SSDP: ${e.message}", e)
            } finally {
                releaseMulticastLock()
                _isScanning.value = false
            }
        }
    }

    /**
     * Menghentikan pemindaian.
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        releaseMulticastLock()
        _isScanning.value = false
    }

    private suspend fun sendMsearchQuery(serviceType: String, mxSeconds: Int) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                soTimeout = mxSeconds * 1000
                bind(InetSocketAddress(0))
            }

            val mSearchPayload = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_IP:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: $mxSeconds\r\n")
                append("ST: $serviceType\r\n")
                append("\r\n")
            }

            val data = mSearchPayload.toByteArray(Charsets.UTF_8)
            val destination = InetAddress.getByName(SSDP_IP)
            val packet = DatagramPacket(data, data.size, destination, SSDP_PORT)

            // Kirim paket M-SEARCH (ulangi 2x untuk reliabilitas UDP di jaringan Wi-Fi)
            socket.send(packet)
            delay(150)
            socket.send(packet)

            val receiveBuffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()
            val maxDuration = mxSeconds * 1000L

            while (isActive && (System.currentTimeMillis() - startTime < maxDuration)) {
                try {
                    val responsePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(responsePacket)

                    val responseText = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                    parseSsdpResponse(responseText)
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "Exception receiving SSDP packet: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengirim M-SEARCH untuk $serviceType: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (_: Throwable) {}
        }
    }

    private fun parseSsdpResponse(response: String) {
        val lines = response.lines()
        var location: String? = null
        var usn: String? = null

        for (line in lines) {
            val lower = line.lowercase()
            when {
                lower.startsWith("location:") -> {
                    location = line.substringAfter(":").trim()
                }
                lower.startsWith("usn:") -> {
                    usn = line.substringAfter(":").trim()
                }
            }
        }

        if (!location.isNullOrBlank()) {
            val deviceId = usn?.substringBefore("::") ?: location
            if (!deviceMap.containsKey(deviceId)) {
                scope.launch {
                    fetchDeviceDescription(deviceId, location)
                }
            }
        }
    }

    private suspend fun fetchDeviceDescription(deviceId: String, locationUrl: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(locationUrl)
                .header("User-Agent", "DLNADOC/1.50 UPnP/1.0")
                .build()

            val resp = NetworkModule.okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext
            val xml = resp.body?.string() ?: return@withContext

            val device = parseDeviceXml(deviceId, locationUrl, xml)
            if (device != null && device.controlUrl.isNotBlank()) {
                deviceMap[device.id] = device
                _devices.value = deviceMap.values.toList()
                Log.d(TAG, "Ditemukan Smart TV DLNA: ${device.name} (${device.modelName}) di ${device.controlUrl}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengambil deskripsi perangkat dari $locationUrl: ${e.message}")
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("DLNA_SSDP_MULTICAST_LOCK")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}

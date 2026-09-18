package com.example.network.watchparty

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.InetAddress

/**
 * mDNS LocalPeerDiscovery for Watch Party rooms.
 * Allows devices on the local LAN to broadcast and auto-discover active Watch Party rooms.
 */
class WatchPartyDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "WatchPartyDiscovery"
        const val SERVICE_TYPE = "_otakuhub_wp._tcp."
        const val SERVICE_NAME_PREFIX = "WP_Room_"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _activeRooms = MutableStateFlow<List<WatchPartyRoom>>(emptyList())
    val activeRooms: StateFlow<List<WatchPartyRoom>> = _activeRooms.asStateFlow()

    fun startHostingRoom(room: WatchPartyRoom) {
        stopHostingRoom()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX${room.roomId}"
            serviceType = SERVICE_TYPE
            port = room.wsPort
            
            // Set attributes if API >= 21 (LOLLIPOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("roomName", room.roomName)
                setAttribute("hostName", room.hostName)
                setAttribute("mediaTitle", room.mediaTitle)
                setAttribute("episodeTitle", room.episodeTitle)
                setAttribute("mediaUrl", room.mediaUrl)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Watch Party room registered via mDNS: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Watch Party registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Watch Party room unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Watch Party unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Watch Party service: ${e.message}")
        }
    }

    fun stopHostingRoom() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering Watch Party service: ${e.message}")
            }
            registrationListener = null
        }
    }

    fun startDiscoveringRooms() {
        stopDiscoveringRooms()
        _activeRooms.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Watch Party discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Watch Party service found: ${service.serviceName}")
                if (service.serviceType == SERVICE_TYPE || service.serviceName.contains(SERVICE_NAME_PREFIX)) {
                    resolveRoom(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Watch Party service lost: ${service.serviceName}")
                _activeRooms.value = _activeRooms.value.filterNot { it.roomId in service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Watch Party discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                stopDiscoveringRooms()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Watch Party discovery: ${e.message}")
        }
    }

    private fun resolveRoom(service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val hostIp = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                
                var roomName = "Watch Party Room"
                var hostName = "Host"
                var mediaTitle = ""
                var episodeTitle = ""
                var mediaUrl = ""

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val attrs = serviceInfo.attributes
                    roomName = attrs["roomName"]?.let { String(it) } ?: roomName
                    hostName = attrs["hostName"]?.let { String(it) } ?: hostName
                    mediaTitle = attrs["mediaTitle"]?.let { String(it) } ?: mediaTitle
                    episodeTitle = attrs["episodeTitle"]?.let { String(it) } ?: episodeTitle
                    mediaUrl = attrs["mediaUrl"]?.let { String(it) } ?: mediaUrl
                }

                val roomId = serviceInfo.serviceName.removePrefix(SERVICE_NAME_PREFIX)
                val room = WatchPartyRoom(
                    roomId = roomId,
                    roomName = roomName,
                    hostName = hostName,
                    hostIp = hostIp,
                    wsPort = port,
                    mediaTitle = mediaTitle,
                    episodeTitle = episodeTitle,
                    mediaUrl = mediaUrl
                )

                // Update room list
                val current = _activeRooms.value.toMutableList()
                current.removeAll { it.roomId == roomId || it.hostIp == hostIp }
                current.add(room)
                _activeRooms.value = current
            }
        }

        try {
            nsdManager.resolveService(service, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving service: ${e.message}")
        }
    }

    fun stopDiscoveringRooms() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery: ${e.message}")
            }
            discoveryListener = null
        }
    }
}

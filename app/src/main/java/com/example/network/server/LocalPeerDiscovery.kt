package com.example.network.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

data class PeerDevice(
    val serviceName: String,
    val ipAddress: String,
    val port: Int
) {
    val baseUrl get() = "http://$ipAddress:$port"
}

class LocalPeerDiscovery(private val context: Context) {

    companion object {
        private const val TAG = "LocalPeerDiscovery"
        const val SERVICE_TYPE = "_http._tcp."
        const val SERVICE_NAME_PREFIX = "OtakuHub_P2P_"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    private val _peers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peers: StateFlow<List<PeerDevice>> = _peers.asStateFlow()

    fun startRegistration(port: Int, deviceName: String = Build.MODEL) {
        stopRegistration()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX$deviceName"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering service: ${e.message}")
        }
    }

    fun stopRegistration() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering service: ${e.message}")
            }
            registrationListener = null
        }
    }

    fun startDiscovery() {
        stopDiscovery()
        _peers.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success: $service")
                if (service.serviceType == SERVICE_TYPE && service.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "service lost: $service")
                _peers.value = _peers.value.filterNot { it.serviceName == service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                stopDiscovery()
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting discovery: ${e.message}")
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Resolve Succeeded. $serviceInfo")
                val host: InetAddress? = serviceInfo.host
                if (host != null && host.hostAddress != "127.0.0.1") {
                    val port: Int = serviceInfo.port
                    val newPeer = PeerDevice(
                        serviceName = serviceInfo.serviceName.removePrefix(SERVICE_NAME_PREFIX),
                        ipAddress = host.hostAddress ?: "",
                        port = port
                    )
                    
                    val currentPeers = _peers.value.toMutableList()
                    val existingIndex = currentPeers.indexOfFirst { it.serviceName == newPeer.serviceName }
                    if (existingIndex >= 0) {
                        currentPeers[existingIndex] = newPeer
                    } else {
                        currentPeers.add(newPeer)
                    }
                    _peers.value = currentPeers
                }
            }
        }

        // We can only have one active resolve request, but for simplicity we'll just try
        try {
            nsdManager.resolveService(service, resolver)
        } catch (e: Exception) {
            Log.e(TAG, "Resolve exception: ${e.message}")
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery: ${e.message}")
            }
            discoveryListener = null
        }
    }

    fun stopAll() {
        stopRegistration()
        stopDiscovery()
    }
}

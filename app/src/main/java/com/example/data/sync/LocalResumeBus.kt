package com.example.data.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

/**
 * High-speed, zero-config local network broadcast bus (UDP)
 * for instant sub-second micro-state synchronization across devices on the same Wi-Fi.
 */
object LocalResumeBus {
    private const val TAG = "LocalResumeBus"
    private const val PORT = 8988
    private const val BUFFER_SIZE = 4096

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startListening(context: Context, onStateReceived: (CrossDeviceResumeState) -> Unit) {
        if (listenJob?.isActive == true) return

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi != null && multicastLock == null) {
                multicastLock = wifi.createMulticastLock("OtakuHubLocalResumeBus").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        listenJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                val buffer = ByteArray(BUFFER_SIZE)
                Log.d(TAG, "LocalResumeBus listening on UDP port $PORT")

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val state = CrossDeviceResumeState.fromJson(text)
                        if (state != null) {
                            onStateReceived(state)
                        }
                    } catch (e: SocketException) {
                        if (!isActive) break
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing UDP packet: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "LocalResumeBus failed to bind port $PORT: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun broadcastState(state: CrossDeviceResumeState) {
        scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                }
                val jsonBytes = state.toJson().toByteArray(Charsets.UTF_8)
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(jsonBytes, jsonBytes.size, broadcastAddress, PORT)
                socket.send(packet)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send local UDP broadcast: ${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        try {
            multicastLock?.release()
        } catch (e: Exception) {
            // ignore
        }
        multicastLock = null
    }
}

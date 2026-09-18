package com.example.network.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

class LanShareManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LanShareManager"
    }
    
    private val client = OkHttpClient()
    val localPeerDiscovery = LocalPeerDiscovery(context)

    suspend fun getSharedFiles(peerBaseUrl: String): List<SharedLanFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SharedLanFile>()
        try {
            val request = Request.Builder()
                .url("$peerBaseUrl/api/lan/shared_files")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(bodyString)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        results.add(
                            SharedLanFile(
                                name = obj.optString("name", "Unknown"),
                                path = obj.optString("path", ""),
                                size = obj.optLong("size", 0L),
                                type = obj.optString("type", "")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get shared files from $peerBaseUrl", e)
        }
        results
    }

    fun getDownloadUrl(peerBaseUrl: String, filePath: String): String {
        val encodedPath = URLEncoder.encode(filePath, "UTF-8")
        return "$peerBaseUrl/api/lan/download?path=$encodedPath"
    }

    suspend fun downloadFile(peerBaseUrl: String, filePath: String, destFile: File, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = getDownloadUrl(peerBaseUrl, filePath)
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                var bytesReadTotal = 0L
                
                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesReadTotal += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((bytesReadTotal * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file from $peerBaseUrl", e)
            false
        }
    }
}

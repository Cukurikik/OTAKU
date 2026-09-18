package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.NetworkModule
import com.example.util.WebViewHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.SecureRandom

data class MalAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

object MalPkceUtil {
    private const val CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    
    fun generateCodeVerifier(length: Int = 128): String {
        val random = SecureRandom()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(CHARACTERS[random.nextInt(CHARACTERS.length)])
        }
        return sb.toString()
    }

    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        val digest = md.digest()
        return android.util.Base64.encodeToString(
            digest, 
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalOAuthDialog(
    onTokenExtracted: (accessToken: String, refreshToken: String, expiresIn: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clientId = "6114d00ca681b7701d1e15fe11a4987e"
    
    // Generate PKCE code verifier and challenge
    val codeVerifier = remember { MalPkceUtil.generateCodeVerifier(128) }
    val codeChallenge = remember { MalPkceUtil.generateCodeChallenge(codeVerifier) }
    val state = remember { MalPkceUtil.generateCodeVerifier(16) }
    
    val authUrl = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$clientId&code_challenge=$codeChallenge&code_challenge_method=s256&state=$state"
    val redirectUriPrefixes = listOf("myanimelist://auth", "app://otakuhub/mal-auth", "https://myanimelist.net/v1/oauth2/authorize")

    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var webViewProgress by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Masuk ke MyAnimeList", fontWeight = FontWeight.Bold)
                            Text(
                                "Otentikasi OAuth 2.0 PKCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            errorMessage = null
                            webViewInstance?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Gagal Memuat Halaman",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            errorMessage = null
                            webViewInstance?.loadUrl(authUrl)
                        }) {
                            Text("Coba Lagi")
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                WebViewHelper.configureSecureSettings(this, "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36", blockImages = false)
                                
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
                                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                                        WebViewHelper.safeDestroy(view)
                                        onDismiss()
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (url != null && handleRedirect(url)) {
                                            view?.stopLoading()
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return handleRedirect(url)
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        val failingUrl = request?.url?.toString() ?: ""
                                        // Ignore intent / custom scheme errors as they are intercepted redirects
                                        if (!failingUrl.startsWith("myanimelist://") && !failingUrl.startsWith("app://")) {
                                            if (request?.isForMainFrame == true) {
                                                errorMessage = "Koneksi terputus: ${error?.description}"
                                            }
                                        }
                                    }

                                    private fun handleRedirect(url: String): Boolean {
                                        if (url.startsWith("myanimelist://") || url.startsWith("app://otakuhub/") || url.contains("code=")) {
                                            val uri = Uri.parse(url)
                                            val code = uri.getQueryParameter("code")
                                            if (!code.isNullOrEmpty()) {
                                                isLoading = true
                                                scope.launch {
                                                    val tokenResult = exchangeCodeForToken(clientId, code, codeVerifier)
                                                    if (tokenResult != null) {
                                                        onTokenExtracted(tokenResult.accessToken, tokenResult.refreshToken, tokenResult.expiresIn)
                                                    } else {
                                                        isLoading = false
                                                        Toast.makeText(context, "Gagal menukar kode otorisasi MAL", Toast.LENGTH_LONG).show()
                                                        errorMessage = "Gagal memproses token dari MyAnimeList. Silakan coba lagi."
                                                    }
                                                }
                                                return true
                                            }
                                            val error = uri.getQueryParameter("error")
                                            if (!error.isNullOrEmpty()) {
                                                val errorDescription = uri.getQueryParameter("error_description") ?: error
                                                Toast.makeText(context, "Otorisasi dibatalkan: $errorDescription", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        },
                        onRelease = { webView ->
                            WebViewHelper.safeDestroy(webView)
                        }
                    )
                }

                if (isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Menghubungkan ke MyAnimeList...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun exchangeCodeForToken(clientId: String, code: String, codeVerifier: String): MalAuthToken? {
    return withContext(Dispatchers.IO) {
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(requestBody)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val accessMatch = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                val refreshMatch = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                val expiresMatch = "\"expires_in\"\\s*:\\s*(\\d+)".toRegex().find(body)

                val accessToken = accessMatch?.groupValues?.get(1) ?: return@withContext null
                val refreshToken = refreshMatch?.groupValues?.get(1) ?: ""
                val expiresIn = expiresMatch?.groupValues?.get(1)?.toLongOrNull() ?: 2678400L

                return@withContext MalAuthToken(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun refreshMalOAuthToken(clientId: String, refreshToken: String): MalAuthToken? {
    return withContext(Dispatchers.IO) {
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(requestBody)
                .build()

            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val accessMatch = "\"access_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                val refreshMatch = "\"refresh_token\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)
                val expiresMatch = "\"expires_in\"\\s*:\\s*(\\d+)".toRegex().find(body)

                val accessToken = accessMatch?.groupValues?.get(1) ?: return@withContext null
                val newRefreshToken = refreshMatch?.groupValues?.get(1) ?: refreshToken
                val expiresIn = expiresMatch?.groupValues?.get(1)?.toLongOrNull() ?: 2678400L

                return@withContext MalAuthToken(
                    accessToken = accessToken,
                    refreshToken = newRefreshToken,
                    expiresIn = expiresIn
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

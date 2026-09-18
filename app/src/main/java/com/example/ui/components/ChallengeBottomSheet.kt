package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.interceptor.ChallengeStateManager
import com.example.network.webview.StealthWebViewClient
import com.example.util.WebViewHelper

/**
 * ChallengeBottomSheet:
 * Modal BottomSheet interaktif yang muncul saat sistem mendeteksi Cloudflare Turnstile /
 * Anti-Bot Challenge yang memerlukan interaksi manusia (timeout otomatis > 5.0 detik).
 * 
 * Menggunakan StealthWebViewClient terintegrasi dengan Stealth.js untuk menyamarkan status
 * otomatisasi, memungkinkan pengguna menyelesaikan tantangan "Verify you are human" dengan mudah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallengeBottomSheet(
    modifier: Modifier = Modifier
) {
    val activeChallenge by ChallengeStateManager.activeChallenge.collectAsStateWithLifecycle()
    val challenge = activeChallenge ?: return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPageLoading by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = {
            ChallengeStateManager.onChallengeCancelled()
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("cloudflare_challenge_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Shield",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Verifikasi Cloudflare",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = challenge.host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { ChallengeStateManager.onChallengeCancelled() },
                    modifier = Modifier.testTag("challenge_cancel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Batal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Situs target memerlukan verifikasi keamanan. Silakan sentuh kotak centang 'Verify you are human' di bawah ini untuk melanjutkan:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Loading Progress Bar
            if (isPageLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // WebView Interactive Canvas Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            WebViewHelper.configureSecureSettings(this, challenge.userAgent, blockImages = false)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    if (newProgress >= 90) {
                                        isPageLoading = false
                                    }
                                }
                            }
                            webViewClient = StealthWebViewClient(
                                onChallengeSolved = { cookies ->
                                    ChallengeStateManager.onChallengeSolved(cookies)
                                },
                                onPageFinishedCallback = {
                                    isPageLoading = false
                                }
                            )
                            loadUrl(challenge.url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != challenge.url) {
                            isPageLoading = true
                            webView.loadUrl(challenge.url)
                        }
                    },
                    onRelease = { webView ->
                        WebViewHelper.safeDestroy(webView)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("challenge_webview")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { ChallengeStateManager.onChallengeCancelled() },
                    modifier = Modifier.testTag("challenge_dismiss_btn")
                ) {
                    Text("Batal")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

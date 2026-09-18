package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.SourceHeaderStore
import com.example.data.sources.SourceStatusBus

/**
 * Dialog for comprehensive Per-Source Configuration:
 * - Domain / Base URL Override
 * - Custom User-Agent (to bypass Cloudflare/Turnstile anti-bot checks)
 * - Custom Cookie (e.g. cf_clearance, session tokens, bypass cookies)
 * - Custom Referer header
 */
@Composable
fun SourceConfigDialog(
    sourceId: String,
    sourceName: String = sourceId,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    var currentUrl by remember { mutableStateOf(SourceBaseUrlStore.get(context, sourceId, "")) }
    var userAgent by remember { mutableStateOf(SourceHeaderStore.getUserAgent(context, sourceId).orEmpty()) }
    var cookie by remember { mutableStateOf(SourceHeaderStore.getCookie(context, sourceId).orEmpty()) }
    var referer by remember { mutableStateOf(SourceHeaderStore.getReferer(context, sourceId).orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = "Konfigurasi Sumber",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Atur URL, User-Agent, dan Cookie khusus untuk sumber ini jika mengalami kendala Cloudflare / L7 blocking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Base URL / Domain
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = { currentUrl = it },
                    label = { Text("Base URL / Domain Alternatif") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )

                // 2. Custom User-Agent
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("Custom User-Agent") },
                    placeholder = { Text("Kosongkan untuk gunakan UA global") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )

                // 3. Custom Cookie
                OutlinedTextField(
                    value = cookie,
                    onValueChange = { cookie = it },
                    label = { Text("Custom Cookie (cf_clearance, dsb)") },
                    placeholder = { Text("cf_clearance=...; session=...") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Cookie, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )

                // 4. Custom Referer
                OutlinedTextField(
                    value = referer,
                    onValueChange = { referer = it },
                    label = { Text("Custom Referer (Opsional)") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )

                // Quick preset helper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            SourceHeaderStore.clear(context, sourceId)
                            SourceBaseUrlStore.set(context, sourceId, "")
                            currentUrl = ""
                            userAgent = ""
                            cookie = ""
                            referer = ""
                            Toast.makeText(context, "Header & URL di-reset ke default", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset Default", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = {
                            // Copy standard desktop Chrome User-Agent preset
                            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        }
                    ) {
                        Text("Isi UA Desktop", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SourceBaseUrlStore.set(context, sourceId, currentUrl)
                    SourceHeaderStore.setUserAgent(context, sourceId, userAgent)
                    SourceHeaderStore.setCookie(context, sourceId, cookie)
                    SourceHeaderStore.setReferer(context, sourceId, referer)
                    SourceStatusBus.clear(sourceId)
                    Toast.makeText(context, "Konfigurasi $sourceName berhasil disimpan", Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

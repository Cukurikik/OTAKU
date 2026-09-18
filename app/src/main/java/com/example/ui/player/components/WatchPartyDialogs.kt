package com.example.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.watchparty.WatchPartyRoom

@Composable
fun WatchPartyHostDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    mediaTitle: String,
    onConfirmHost: (roomName: String, hostName: String) -> Unit
) {
    if (!showDialog) return

    var roomName by remember { mutableStateOf("Nonton $mediaTitle") }
    var hostName by remember { mutableStateOf("Host Pemutar") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Buat Watch Party Lokal (Host)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Buat ruang Watch Party offline di jaringan Wi-Fi lokal. Teman/keluarga di rumah dapat bergabung secara instan tanpa internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Nama Ruang Watch Party") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("Nama Anda (Host)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Media yang Disinkronkan:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmHost(roomName.ifBlank { "Watch Party Room" }, hostName.ifBlank { "Host" })
                    onDismissRequest()
                }
            ) {
                Text("Mulai Ruang Host")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun WatchPartyJoinDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    activeRooms: List<WatchPartyRoom>,
    onRefreshRooms: () -> Unit,
    onJoinRoom: (room: WatchPartyRoom, peerName: String) -> Unit
) {
    if (!showDialog) return

    var peerName by remember { mutableStateOf("Penonton") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Gabung Watch Party LAN", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Mencari ruang Watch Party aktif di jaringan Wi-Fi lokal (mDNS Auto-Discovery)...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = peerName,
                    onValueChange = { peerName = it },
                    label = { Text("Nama Anda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ruang Aktif Ditemukan (${activeRooms.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onRefreshRooms) {
                        Icon(Icons.Default.Refresh, contentDescription = "Pindai Ulang")
                    }
                }

                if (activeRooms.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Belum ada ruang Host yang terdeteksi di jaringan lokal Wi-Fi ini.\nPastikan perangkat Host sudah membuat ruang.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(activeRooms) { room ->
                            Card(
                                onClick = {
                                    onJoinRoom(room, peerName.ifBlank { "Penonton" })
                                    onDismissRequest()
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(room.roomName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${room.hostIp}:${room.wsPort}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Host: ${room.hostName}", style = MaterialTheme.typography.bodySmall)
                                    if (room.mediaTitle.isNotBlank()) {
                                        Text("Media: ${room.mediaTitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Tutup")
            }
        }
    )
}

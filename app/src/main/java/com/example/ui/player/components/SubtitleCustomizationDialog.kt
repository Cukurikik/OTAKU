package com.example.ui.player.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SubtitleCustomizationDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    subtitleFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    subtitleTextColor: Int,
    onTextColorChange: (Int) -> Unit,
    subtitleBgColor: Int,
    onBgColorChange: (Int) -> Unit,
    subtitleOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    backgroundAudioEnabled: Boolean,
    onBackgroundAudioChange: (Boolean) -> Unit
) {
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Kustomisasi Subtitle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Font Size
                Column {
                    Text("Ukuran Teks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        listOf(14f to "Kecil", 18f to "Normal", 22f to "Besar", 26f to "X-Besar").forEach { (size, label) ->
                            FilterChip(
                                selected = subtitleFontSize == size,
                                onClick = { onFontSizeChange(size) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Text Color
                Column {
                    Text("Warna Teks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        listOf(
                            AndroidColor.WHITE to "Putih",
                            AndroidColor.YELLOW to "Kuning",
                            AndroidColor.CYAN to "Sian",
                            AndroidColor.GREEN to "Hijau"
                        ).forEach { (color, label) ->
                            FilterChip(
                                selected = subtitleTextColor == color,
                                onClick = { onTextColorChange(color) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Subtitle Offset
                Column {
                    Text("Offset Subtitle (ms)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onOffsetChange(subtitleOffsetMs - 500) },
                            modifier = Modifier
                                .background(Color.DarkGray, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-500ms", tint = Color.White)
                        }
                        Text(
                            text = "${if (subtitleOffsetMs > 0) "+" else ""}$subtitleOffsetMs ms",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(70.dp),
                            textAlign = TextAlign.Center
                        )
                        IconButton(
                            onClick = { onOffsetChange(subtitleOffsetMs + 500) },
                            modifier = Modifier
                                .background(Color.DarkGray, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+500ms", tint = Color.White)
                        }
                    }
                }

                // Background Audio Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Background Audio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text("Putar audio saat aplikasi diminimalkan", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = backgroundAudioEnabled,
                        onCheckedChange = onBackgroundAudioChange
                    )
                }

                // Background Color
                Column {
                    Text("Latar Belakang Subtitle", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        listOf(
                            AndroidColor.TRANSPARENT to "Transparan",
                            AndroidColor.argb(160, 0, 0, 0) to "Translusen",
                            AndroidColor.BLACK to "Hitam Pekat"
                        ).forEach { (bgColor, label) ->
                            FilterChip(
                                selected = subtitleBgColor == bgColor,
                                onClick = { onBgColorChange(bgColor) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Selesai")
            }
        }
    )
}

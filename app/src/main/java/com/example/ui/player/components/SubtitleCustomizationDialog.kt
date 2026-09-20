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

                // Subtitle Offset Real-Time Controller (-5.0s to +5.0s)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sinkronisasi Subtitle (Offset)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        val offsetSec = subtitleOffsetMs / 1000.0f
                        val offsetLabel = when {
                            subtitleOffsetMs > 0 -> "+${String.format(java.util.Locale.US, "%.1f", offsetSec)}s (+${subtitleOffsetMs}ms)"
                            subtitleOffsetMs < 0 -> "${String.format(java.util.Locale.US, "%.1f", offsetSec)}s (${subtitleOffsetMs}ms)"
                            else -> "0.0s (Sinkron Pas)"
                        }
                        Text(
                            text = offsetLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (subtitleOffsetMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Slider from -5.0s (-5000ms) to +5.0s (+5000ms)
                    val sliderVal = (subtitleOffsetMs / 1000f).coerceIn(-5.0f, 5.0f)
                    Slider(
                        value = sliderVal,
                        onValueChange = { newVal ->
                            val clamped = newVal.coerceIn(-5.0f, 5.0f)
                            val roundedMs = ((clamped * 10).toInt() * 100).toLong()
                            onOffsetChange(roundedMs)
                        },
                        valueRange = -5.0f..5.0f,
                        steps = 99, // 100ms steps
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Step Adjustment Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs - 1000L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("-1s", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs - 500L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs - 100L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("-0.1s", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Reset 0s Button
                        OutlinedButton(
                            onClick = { onOffsetChange(0L) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs + 100L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+0.1s", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs + 500L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onOffsetChange((subtitleOffsetMs + 1000L).coerceIn(-5000L, 5000L)) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+1s", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        text = "Formulasi: T_display = T_current + ΔT_offset (-5.0s s/d +5.0s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
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

package com.example.ui.player.components

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BrightnessDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    currentBrightness: Float,
    onBrightnessChange: (Float) -> Unit
) {
    if (!showDialog) return

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Pengaturan Kecerahan Layar", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (currentBrightness < 0f) "Kecerahan: Otomatis (Sistem)" else "Kecerahan: ${(currentBrightness * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessLow, contentDescription = null)
                    Slider(
                        value = if (currentBrightness < 0f) 0.5f else currentBrightness,
                        onValueChange = { newVal ->
                            onBrightnessChange(newVal)
                            val act = context as? Activity
                            if (act != null) {
                                val window = act.window
                                val layoutParams = window.attributes
                                layoutParams.screenBrightness = newVal
                                window.attributes = layoutParams
                            }
                        },
                        valueRange = 0.01f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onBrightnessChange(-1f)
                            val act = context as? Activity
                            if (act != null) {
                                val window = act.window
                                val layoutParams = window.attributes
                                layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                window.attributes = layoutParams
                            }
                        }
                    ) {
                        Text("Reset ke Otomatis")
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

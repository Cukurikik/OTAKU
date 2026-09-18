package com.example.ui.player.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.media.shader.AnimeShaderMode

@Composable
fun ShaderCustomizationDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    currentMode: AnimeShaderMode,
    currentIntensity: Float,
    onShaderModeSelected: (AnimeShaderMode, Float) -> Unit
) {
    if (!showDialog) return

    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }
    var intensity by remember(currentIntensity) { mutableFloatStateOf(currentIntensity) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("GLSL Video Post-Processing (GPU)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Pilih profil pemrosesan grafik GPU real-time untuk meningkatkan kualitas anime dan video kompresi rendah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Shader Modes Choice
                AnimeShaderMode.entries.forEach { mode ->
                    Card(
                        onClick = {
                            selectedMode = mode
                            onShaderModeSelected(mode, intensity)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMode == mode)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedMode == mode),
                                onClick = {
                                    selectedMode = mode
                                    onShaderModeSelected(mode, intensity)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Intensity Slider (only if mode is not OFF)
                if (selectedMode != AnimeShaderMode.OFF) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Intensitas Shader", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            Text("%.1fx".format(intensity), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = intensity,
                            onValueChange = { newIntensity ->
                                intensity = newIntensity
                                onShaderModeSelected(selectedMode, newIntensity)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14
                        )
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

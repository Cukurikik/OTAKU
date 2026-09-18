import re

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

settings_old = '''                                    settingsManager.isDoublePageMode = checked
                                    }
                                )
                            }
                        }
                        
                        HorizontalDivider()'''
settings_new = '''                                    settingsManager.isDoublePageMode = checked
                                    }
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Potong Tepi Putih (Auto-Crop)")
                            Switch(
                                checked = cropBorders,
                                onCheckedChange = { checked ->
                                    cropBorders = checked
                                    settingsManager.cropBorders = checked
                                }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        Text("Orientasi Layar", style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-1 to "Auto", 1 to "Portrait", 0 to "Landscape").forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            orientationLock = value
                                            settingsManager.orientationLock = value
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = orientationLock == value,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                        
                        HorizontalDivider()'''
content = content.replace(settings_old, settings_new)

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

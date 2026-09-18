import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    code = f.read()

# Add states at the beginning of ExoPlayerView
imports = """
import com.example.ui.player.SubtitleCue
import com.example.ui.player.SubtitleParser
"""
code = code.replace("import com.example.ui.components.SettingsDialog", "import com.example.ui.components.SettingsDialog\n" + imports)

# Find var customBrightness by remember
state_injection = """
    var showBrightnessDialog by remember { mutableStateOf(false) }
    var customBrightness by remember { mutableFloatStateOf(settingsManager.customBrightness) }
    var backgroundAudioEnabled by remember { mutableStateOf(settingsManager.backgroundAudio) }
    
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var activeExternalCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var parsedExternalSubtitles by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var activeSubtitleText by remember { mutableStateOf("") }
"""
code = re.sub(r'var showBrightnessDialog by remember \{ mutableStateOf\(false\) \}\s+var customBrightness by remember \{ mutableFloatStateOf\(settingsManager\.customBrightness\) \}', state_injection, code)

# Replace ExoPlayer subtitles attachment logic
media_item_logic = """
            if (currentSubtitles.isNotEmpty()) {
                val subtitleConfigs = currentSubtitles.map { sub ->
                    val mimeType = when (sub.format.lowercase()) {
                        "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
                        "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        "ass" -> androidx.media3.common.MimeTypes.TEXT_SSA
                        else -> androidx.media3.common.MimeTypes.TEXT_VTT
                    }
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(mimeType)
                        .setLanguage(sub.language)
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
"""
# Remove ExoPlayer's subtitle passing because we will handle it via Compose overlay
code = code.replace(media_item_logic, "\n            // We handle external subtitles manually via Compose overlay for offset support\n")

# Add launched effect for fetching subtitles
fetch_subtitles_logic = """
    // Fetch external subtitles when currentSubtitles changes
    LaunchedEffect(currentSubtitles) {
        val firstSub = currentSubtitles.firstOrNull()?.url
        if (!firstSub.isNullOrEmpty()) {
            parsedExternalSubtitles = SubtitleParser.fetchAndParse(firstSub)
        } else {
            parsedExternalSubtitles = emptyList()
        }
    }
    
    // Polling for subtitles
    LaunchedEffect(currentPlayer, subtitleOffsetMs, parsedExternalSubtitles) {
        if (parsedExternalSubtitles.isEmpty()) return@LaunchedEffect
        while(true) {
            val pos = currentPlayer.currentPosition - subtitleOffsetMs
            val active = parsedExternalSubtitles.filter { pos in it.startMs..it.endMs }
            if (active != activeExternalCues) {
                activeExternalCues = active
                activeSubtitleText = active.joinToString("\n") { it.text }
            }
            delay(100)
        }
    }
    
    // Poll for 80% progress
"""
code = code.replace("// Poll for 80% progress", fetch_subtitles_logic)

# Change ON_PAUSE logic
on_pause_old = """
                Lifecycle.Event.ON_PAUSE -> {
                    currentOnSaveProgress(currentPlayer.currentPosition, currentPlayer.duration)
                    currentPlayer.pause()
                }
"""
on_pause_new = """
                Lifecycle.Event.ON_PAUSE -> {
                    currentOnSaveProgress(currentPlayer.currentPosition, currentPlayer.duration)
                    if (!backgroundAudioEnabled) {
                        currentPlayer.pause()
                    }
                }
"""
code = code.replace(on_pause_old, on_pause_new)

# Add custom subtitle rendering above ExoPlayerView
box_start = """
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
"""
compose_subtitles = """
        // Custom Compose Subtitle Overlay
        if (activeSubtitleText.isNotEmpty()) {
            Text(
                text = activeSubtitleText,
                color = Color(subtitleTextColor),
                fontSize = subtitleFontSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp, start = 32.dp, end = 32.dp)
                    .background(Color(subtitleBgColor), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
"""
code = re.sub(r'AndroidView\(\s*modifier = Modifier\.fillMaxSize\(\)\.background\(Color\.Black\),\s*factory = \{ ctx ->', r'AndroidView(\n            modifier = Modifier.fillMaxSize().background(Color.Black),\n            factory = { ctx ->', code)
code = code.replace("if (isTvMode) {", compose_subtitles + "\n        if (isTvMode) {")

# Add settings to dialog
dialog_old = """
                    // Background Color
"""
dialog_new = """
                    // Subtitle Offset
                    Column {
                        Text("Offset Subtitle (ms)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { subtitleOffsetMs -= 500 }, modifier = Modifier.background(Color.DarkGray, CircleShape).size(36.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = "-500ms", tint = Color.White)
                            }
                            Text("${if (subtitleOffsetMs > 0) "+" else ""}$subtitleOffsetMs ms", color = Color.White, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                            IconButton(onClick = { subtitleOffsetMs += 500 }, modifier = Modifier.background(Color.DarkGray, CircleShape).size(36.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "+500ms", tint = Color.White)
                            }
                        }
                    }
                    
                    // Background Audio
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Background Audio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            Text("Putar audio saat aplikasi ditutup", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = backgroundAudioEnabled,
                            onCheckedChange = { 
                                backgroundAudioEnabled = it
                                settingsManager.backgroundAudio = it
                            }
                        )
                    }

                    // Background Color
"""
code = code.replace(dialog_old, dialog_new)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(code)

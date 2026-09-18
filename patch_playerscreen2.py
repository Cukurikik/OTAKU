import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# 1. Pass skipTimes to ExoPlayerView
content = content.replace(
    'onPlaybackError = viewModel::onPlaybackError,',
    'onPlaybackError = viewModel::onPlaybackError,\n                        skipTimes = skipTimes,'
)

# 2. Add skipTimes to ExoPlayerView signature
content = content.replace(
    'isTvMode: Boolean = false,',
    'isTvMode: Boolean = false,\n    skipTimes: List<com.example.data.trackers.AniSkipResult> = emptyList(),'
)

# 3. Add current position tracking loop and skip button overlay
# We will find the Box(modifier = Modifier.fillMaxSize()) { AndroidView(...) ... } and add the skip button inside it.

skip_button_code = '''
        // AniSkip Overlay
        var currentPositionMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(isPlayingOrBuffered) {
            while(true) {
                currentPositionMs = currentPlayer.currentPosition
                delay(1000)
            }
        }
        val currentSkip = skipTimes.firstOrNull { skip ->
            val posSeconds = currentPositionMs / 1000f
            posSeconds >= skip.interval.startTime && posSeconds <= skip.interval.endTime
        }
        
        if (currentSkip != null && !isLocked) {
            Button(
                onClick = {
                    exoPlayer.seekTo((currentSkip.interval.endTime * 1000).toLong())
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 24.dp)
            ) {
                val label = when(currentSkip.skipType) {
                    "op" -> "Skip Intro"
                    "ed" -> "Skip Outro"
                    "recap" -> "Skip Recap"
                    else -> "Skip"
                }
                Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
'''

# Find where AndroidView ends to inject our overlay
overlay_target = '''        // Custom Compose Subtitle Overlay
        if (activeSubtitleText.isNotEmpty()) {'''

content = content.replace(overlay_target, skip_button_code + '\n' + overlay_target)

# 4. Remove the hardcoded Skip Intro/Outro buttons
hardcoded_skip = '''            // Bottom Right: Quick Skip Intro / Outro Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val newPos = (currentPlayer.currentPosition + 85000).coerceAtMost(currentPlayer.duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(newPos)
                        gestureFeedbackText = "Skip Intro (+85s)"
                        showGestureFeedback = true
                        coroutineScope.launch {
                            delay(1200)
                            showGestureFeedback = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+85s Intro", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = {
                        val newPos = (currentPlayer.currentPosition + 90000).coerceAtMost(currentPlayer.duration.coerceAtLeast(0L))
                        exoPlayer.seekTo(newPos)
                        gestureFeedbackText = "Skip Outro (+90s)"
                        showGestureFeedback = true
                        coroutineScope.launch {
                            delay(1200)
                            showGestureFeedback = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+90s Outro", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }'''

content = content.replace(hardcoded_skip, '')

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)

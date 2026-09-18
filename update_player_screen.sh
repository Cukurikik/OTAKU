#!/bin/bash
cat << 'INNER' > modify_player.py
import re

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

# 1. Add state variables for exoTracks and subtitle menu
state_vars = """
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSubtitleTrackMenu by remember { mutableStateOf(false) }
    var exoTracks by remember { mutableStateOf(androidx.media3.common.Tracks.EMPTY) }
"""
content = re.sub(r'var showQualityMenu by remember \{ mutableStateOf\(false\) \}', state_vars.strip(), content)


# 2. Extract trackSelector outside remember
track_selector_decl = """
    val trackSelector = remember {
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredTextLanguage("id"))
        }
    }
    
    val exoPlayer = remember {
"""
content = re.sub(r'val exoPlayer = remember \{', track_selector_decl.strip(), content)

track_selector_inside = r'val trackSelector = androidx\.media3\.exoplayer\.trackselection\.DefaultTrackSelector\(context\)\.apply \{\s*setParameters\(buildUponParameters\(\)\.setPreferredTextLanguage\("id"\)\)\s*\}'
content = re.sub(track_selector_inside, '', content)

# 3. Add onTracksChanged
on_tracks_changed = """
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        exoTracks = tracks
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
"""
content = re.sub(r'override fun onPlaybackStateChanged\(playbackState: Int\) \{', on_tracks_changed.strip(), content)


# 4. Update the Quality Menu and add Subtitle Menu in Controls
controls_regex = r'// Quality Selector Button\s*if \(qualities\.size > 1\) \{[\s\S]*?// Bottom Right: Quick Skip Intro'
replacement_controls = """
                val videoGroups = exoTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
                val textGroups = exoTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                
                // Native Subtitle Track Selector
                if (textGroups.isNotEmpty() || subtitles.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showSubtitleTrackMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Pilih Subtitle",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(expanded = showSubtitleTrackMenu, onDismissRequest = { showSubtitleTrackMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Mati (Off)") },
                                onClick = {
                                    trackSelector.setParameters(
                                        trackSelector.buildUponParameters()
                                            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                    )
                                    showSubtitleTrackMenu = false
                                }
                            )
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val label = format.language ?: format.label ?: "Subtitle ${i + 1}"
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                            trackSelector.setParameters(
                                                trackSelector.buildUponParameters()
                                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                                    .addOverride(override)
                                            )
                                            showSubtitleTrackMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Quality Selector Button
                if (qualities.size > 1 || videoGroups.any { it.length > 1 }) {
                    Box {
                        Button(
                            onClick = { showQualityMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(currentQualityLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                            if (qualities.size > 1) {
                                qualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = { Text(q.qualityLabel) },
                                        onClick = {
                                            val currentPosition = currentPlayer.currentPosition
                                            currentUrl = q.url
                                            currentQualityLabel = q.qualityLabel
                                            showQualityMenu = false
                                            exoPlayer.seekTo(currentPosition)
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Auto") },
                                    onClick = {
                                        trackSelector.setParameters(
                                            trackSelector.buildUponParameters().clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                        )
                                        currentQualityLabel = "Auto"
                                        showQualityMenu = false
                                    }
                                )
                                videoGroups.forEach { group ->
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        val res = "${format.height}p"
                                        DropdownMenuItem(
                                            text = { Text(res) },
                                            onClick = {
                                                val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                                trackSelector.setParameters(
                                                    trackSelector.buildUponParameters()
                                                        .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                                                        .addOverride(override)
                                                )
                                                currentQualityLabel = res
                                                showQualityMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Right: Quick Skip Intro
"""
content = re.sub(controls_regex, replacement_controls.strip(), content)

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
INNER
python3 modify_player.py

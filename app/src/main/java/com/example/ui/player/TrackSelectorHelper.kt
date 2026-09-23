package com.example.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

data class TrackQualityOption(
    val label: String,
    val height: Int,
    val width: Int = 0,
    val bitrate: Int = 0,
    val group: Tracks.Group? = null,
    val trackIndex: Int = -1,
    val isAuto: Boolean = false,
    val isSelected: Boolean = false
)

@OptIn(UnstableApi::class)
object TrackSelectorHelper {

    fun getVideoQualities(tracks: Tracks): List<TrackQualityOption> {
        val options = mutableListOf<TrackQualityOption>()
        
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty()) {
            return emptyList()
        }

        var hasSpecificSelection = false
        val trackOptions = mutableListOf<TrackQualityOption>()

        videoGroups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val height = format.height
                val width = format.width
                val bitrate = format.bitrate
                val isTrackSelected = group.isTrackSelected(i)
                if (isTrackSelected) {
                    hasSpecificSelection = true
                }

                val label = buildQualityLabel(height, width, bitrate, format.label)
                trackOptions.add(
                    TrackQualityOption(
                        label = label,
                        height = height,
                        width = width,
                        bitrate = bitrate,
                        group = group,
                        trackIndex = i,
                        isAuto = false,
                        isSelected = isTrackSelected
                    )
                )
            }
        }

        // Sort tracks by resolution (height) descending, then bitrate descending
        trackOptions.sortWith(compareByDescending<TrackQualityOption> { it.height }.thenByDescending { it.bitrate })

        // Prepend Auto option
        options.add(
            TrackQualityOption(
                label = if (!hasSpecificSelection) "Auto (Rekomendasi)" else "Auto",
                height = 0,
                isAuto = true,
                isSelected = !hasSpecificSelection
            )
        )

        options.addAll(trackOptions)
        return options
    }

    private fun buildQualityLabel(height: Int, width: Int, bitrate: Int, rawLabel: String?): String {
        if (!rawLabel.isNullOrBlank() && (rawLabel.contains("p", ignoreCase = true) || rawLabel.contains("0"))) {
            return rawLabel
        }
        val resText = when {
            height >= 2160 -> "4K (${height}p)"
            height >= 1440 -> "2K (${height}p)"
            height >= 1080 -> "1080p Full HD"
            height >= 720 -> "720p HD"
            height >= 480 -> "480p SD"
            height >= 360 -> "360p"
            height > 0 -> "${height}p"
            else -> rawLabel ?: "Standard"
        }
        
        return if (bitrate > 0) {
            val mbps = bitrate / 1_000_000f
            if (mbps >= 1.0f) {
                "$resText (%.1f Mbps)".format(mbps)
            } else {
                val kbps = bitrate / 1_000
                "$resText ($kbps Kbps)"
            }
        } else {
            resText
        }
    }

    fun selectVideoQuality(trackSelector: DefaultTrackSelector, width: Int, height: Int) {
        val parameters = trackSelector.buildUponParameters()
            .setMaxVideoSize(width, height)
            .setForceHighestSupportedBitrate(false)
            .build()
        trackSelector.setParameters(parameters)
    }

    fun applyVideoQuality(
        trackSelector: DefaultTrackSelector,
        option: TrackQualityOption
    ) {
        if (option.isAuto || option.group == null || option.trackIndex < 0) {
            selectAutoVideoQuality(trackSelector)
        } else {
            val override = TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex)
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .addOverride(override)
            )
        }
    }

    fun selectAutoVideoQuality(trackSelector: DefaultTrackSelector) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        )
    }

    fun selectSubtitleTrack(
        trackSelector: DefaultTrackSelector,
        group: Tracks.Group,
        trackIndex: Int
    ) {
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .addOverride(override)
        )
    }

    fun disableSubtitles(trackSelector: DefaultTrackSelector) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }

    fun getAudioTracks(tracks: Tracks): List<AudioTrackOption> {
        return AnimeTrackSelectionEngine.getAvailableAudioTracks(tracks)
    }

    fun selectAudioTrack(
        trackSelector: DefaultTrackSelector,
        group: Tracks.Group,
        trackIndex: Int
    ) {
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .addOverride(override)
        )
    }

    fun getSubtitleTracks(
        tracks: Tracks,
        externalSubtitles: List<com.example.data.sources.Subtitle> = emptyList()
    ): List<SubtitleTrackOption> {
        return AnimeTrackSelectionEngine.getAvailableSubtitleTracks(tracks, externalSubtitles)
    }
}

package com.example.ui.player

import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.example.data.sources.Subtitle
import java.util.Locale

/**
 * Representation of an audio track candidate (Original Japanese, Indonesian Dub, English Dub, etc.)
 */
data class AudioTrackOption(
    val id: String,
    val label: String,
    val language: String,
    val sampleMimeType: String?,
    val channelCount: Int,
    val sampleRate: Int,
    val group: Tracks.Group,
    val trackIndex: Int,
    val isSelected: Boolean
)

/**
 * Representation of a subtitle track candidate (ASS / SSA, WebVTT, SRT, or Off)
 */
data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val language: String,
    val sampleMimeType: String?,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isSelected: Boolean,
    val isAss: Boolean = false,
    val isOff: Boolean = false,
    val externalUrl: String? = null
)

/**
 * High-performance Track Selection Engine designed specifically for Anime Dual-Audio (Indo Dub vs JP Original)
 * and Custom Subtitle Styling & Rendering (SSA/ASS + WebVTT).
 */
@OptIn(UnstableApi::class)
object AnimeTrackSelectionEngine {

    /**
     * Extracts all available audio tracks from ExoPlayer's current [Tracks].
     */
    fun getAvailableAudioTracks(tracks: Tracks): List<AudioTrackOption> {
        val audioOptions = mutableListOf<AudioTrackOption>()
        var globalIndex = 0

        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val lang = format.language.orEmpty().lowercase(Locale.ROOT)
                    val friendlyLabel = formatAudioLabel(format, lang, globalIndex + 1)

                    audioOptions.add(
                        AudioTrackOption(
                            id = "audio_${groupIndex}_${trackIndex}",
                            label = friendlyLabel,
                            language = lang,
                            sampleMimeType = format.sampleMimeType,
                            channelCount = format.channelCount,
                            sampleRate = format.sampleRate,
                            group = group,
                            trackIndex = trackIndex,
                            isSelected = isSelected
                        )
                    )
                    globalIndex++
                }
            }
        }
        return audioOptions
    }

    /**
     * Formats audio track label into human-readable Anime conventions (JP Original, Indo Dub, etc.).
     */
    fun formatAudioLabel(format: Format, lang: String, fallbackNumber: Int): String {
        val explicitLabel = format.label
        if (!explicitLabel.isNullOrBlank()) {
            return when {
                explicitLabel.contains("jp", ignoreCase = true) || explicitLabel.contains("japan", ignoreCase = true) ->
                    "Jepang - $explicitLabel"
                explicitLabel.contains("id", ignoreCase = true) || explicitLabel.contains("indo", ignoreCase = true) ->
                    "Indonesia (Dub Indo) - $explicitLabel"
                explicitLabel.contains("en", ignoreCase = true) || explicitLabel.contains("eng", ignoreCase = true) ->
                    "Inggris (Dub) - $explicitLabel"
                else -> explicitLabel
            }
        }

        return when {
            lang == "ja" || lang == "jpn" || lang == "jp" -> "Jepang (Audio Original)"
            lang == "id" || lang == "ind" || lang == "in" -> "Indonesia (Dub Indo)"
            lang == "en" || lang == "eng" -> "Inggris (Dub EN)"
            lang == "ko" || lang == "kor" -> "Korea (Audio Original)"
            lang == "zh" || lang == "zho" || lang == "chi" -> "Mandarin (Audio Original)"
            lang.isNotBlank() -> Locale(lang).displayLanguage.ifBlank { "Audio #$fallbackNumber ($lang)" }
            else -> "Audio Track #$fallbackNumber"
        }
    }

    /**
     * Selects an audio track by applying TrackSelectionOverride to Player and TrackSelector.
     */
    fun selectAudioTrack(
        player: Player? = null,
        trackSelector: DefaultTrackSelector? = null,
        option: AudioTrackOption
    ) {
        selectAudioTrack(player, trackSelector, option.group, option.trackIndex)
    }

    /**
     * Selects an audio track by Group and track index.
     */
    fun selectAudioTrack(
        player: Player? = null,
        trackSelector: DefaultTrackSelector? = null,
        group: Tracks.Group,
        trackIndex: Int
    ) {
        val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)

        // 1. Update Player TrackSelectionParameters (Universal Player level)
        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        }

        // 2. Update DefaultTrackSelector if available
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .addOverride(override)
            )
        }
    }

    /**
     * Discovers all available subtitle tracks (internal embedded tracks & external server subtitles).
     */
    fun getAvailableSubtitleTracks(
        tracks: Tracks,
        externalSubtitles: List<Subtitle> = emptyList()
    ): List<SubtitleTrackOption> {
        val result = mutableListOf<SubtitleTrackOption>()

        // Check if any subtitle is currently active
        var hasActiveTextTrack = false
        tracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        hasActiveTextTrack = true
                        break
                    }
                }
            }
        }

        // 1. Option "Mati (Tanpa Subtitle)"
        result.add(
            SubtitleTrackOption(
                id = "sub_off",
                label = "Mati (Tanpa Subtitle)",
                language = "",
                sampleMimeType = null,
                group = null,
                trackIndex = -1,
                isSelected = !hasActiveTextTrack,
                isOff = true
            )
        )

        // 2. Internal Subtitle tracks from MediaStream (Embedded MKV/MP4/HLS)
        var subIndex = 1
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val mime = format.sampleMimeType.orEmpty().lowercase(Locale.ROOT)
                    val isAss = mime == MimeTypes.TEXT_SSA ||
                            format.id?.contains("ass", ignoreCase = true) == true ||
                            format.label?.contains("ass", ignoreCase = true) == true

                    val lang = format.language.orEmpty().lowercase(Locale.ROOT)
                    val label = formatSubtitleLabel(format, lang, isAss, subIndex)

                    result.add(
                        SubtitleTrackOption(
                            id = "sub_${groupIndex}_${trackIndex}",
                            label = label,
                            language = lang,
                            sampleMimeType = format.sampleMimeType,
                            group = group,
                            trackIndex = trackIndex,
                            isSelected = isSelected,
                            isAss = isAss
                        )
                    )
                    subIndex++
                }
            }
        }

        // 3. Include external subtitles if provided and not duplicated
        externalSubtitles.forEachIndexed { extIdx, extSub ->
            val isAss = extSub.url.endsWith(".ass", ignoreCase = true) ||
                    extSub.url.endsWith(".ssa", ignoreCase = true) ||
                    extSub.format.equals("ass", ignoreCase = true) ||
                    extSub.format.equals("ssa", ignoreCase = true)

            val extLabel = buildString {
                append(extSub.language.ifBlank { "Subtitle Eksternal ${extIdx + 1}" })
                if (isAss) append(" (ASS Styling)")
            }

            result.add(
                SubtitleTrackOption(
                    id = "sub_ext_${extIdx}",
                    label = extLabel,
                    language = extSub.language,
                    sampleMimeType = if (isAss) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT,
                    group = null,
                    trackIndex = extIdx,
                    isSelected = false,
                    isAss = isAss,
                    externalUrl = extSub.url
                )
            )
        }

        return result
    }

    /**
     * Formats subtitle label for Anime viewers (highlighting Indonesian and ASS styles).
     */
    private fun formatSubtitleLabel(format: Format, lang: String, isAss: Boolean, number: Int): String {
        val explicit = format.label
        val prefix = if (!explicit.isNullOrBlank()) {
            explicit
        } else {
            when {
                lang == "id" || lang == "ind" || lang == "in" -> "Indonesia"
                lang == "en" || lang == "eng" -> "English"
                lang == "ja" || lang == "jpn" -> "Jepang"
                lang.isNotBlank() -> Locale(lang).displayLanguage.ifBlank { "Subtitle #$number" }
                else -> "Subtitle #$number"
            }
        }

        return if (isAss) {
            "$prefix (ASS Styling)"
        } else {
            prefix
        }
    }

    /**
     * Selects or overrides the active subtitle track.
     */
    fun selectSubtitleTrack(
        player: Player? = null,
        trackSelector: DefaultTrackSelector? = null,
        option: SubtitleTrackOption
    ) {
        if (option.isOff) {
            disableSubtitles(player, trackSelector)
            return
        }

        val group = option.group ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)

        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }

        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .addOverride(override)
            )
        }
    }

    /**
     * Disables subtitle rendering.
     */
    fun disableSubtitles(
        player: Player? = null,
        trackSelector: DefaultTrackSelector? = null
    ) {
        player?.let { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
        }
    }

    /**
     * Finds the preferred audio track option (e.g. Dub Indo or Original JP).
     */
    fun findPreferredAudioTrack(
        tracks: List<AudioTrackOption>,
        preferredLanguageCode: String = "ja"
    ): AudioTrackOption? {
        if (tracks.isEmpty()) return null
        val match = tracks.find { it.language.equals(preferredLanguageCode, ignoreCase = true) }
        if (match != null) return match

        // Heuristic fallback for "id" / "ind" or "ja" / "jpn"
        return if (preferredLanguageCode == "ja" || preferredLanguageCode == "jpn") {
            tracks.find { it.language.startsWith("ja") || it.label.contains("jepang", ignoreCase = true) || it.label.contains("japan", ignoreCase = true) }
        } else if (preferredLanguageCode == "id" || preferredLanguageCode == "ind") {
            tracks.find { it.language.startsWith("id") || it.language.startsWith("in") || it.label.contains("indo", ignoreCase = true) }
        } else {
            tracks.firstOrNull()
        } ?: tracks.firstOrNull()
    }

    /**
     * Configures the SubtitleView with custom styling, font size, and edge outline
     * to ensure ASS/SSA and VTT subtitles are never clipped and rendered with high-contrast anime aesthetic.
     */
    fun configureSubtitleStyle(
        subtitleView: SubtitleView,
        fontSizeSp: Float = 18f,
        textColor: Int = android.graphics.Color.WHITE,
        bgColor: Int = android.graphics.Color.TRANSPARENT,
        outlineColor: Int = android.graphics.Color.BLACK
    ) {
        // Enable embedded styles and font sizes to respect ASS font decorations
        subtitleView.setApplyEmbeddedStyles(true)
        subtitleView.setApplyEmbeddedFontSizes(true)

        val captionStyle = CaptionStyleCompat(
            textColor,
            bgColor,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            outlineColor,
            Typeface.DEFAULT_BOLD
        )

        subtitleView.setStyle(captionStyle)
        subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        subtitleView.setBottomPaddingFraction(0.08f)
    }
}

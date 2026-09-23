package com.example.ui.player

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.Subtitle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(UnstableApi::class)
class AnimeTrackSelectionEngineTest {

    private lateinit var context: Context
    private lateinit var trackSelector: DefaultTrackSelector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        trackSelector = DefaultTrackSelector(context)
    }

    @Test
    fun testAudioTrackDiscoveryAndFriendlyLabeling() {
        // Construct track group with Japanese and Indonesian audio
        val formatJp = Format.Builder()
            .setId("audio-jp")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("jpn")
            .setLabel("Original Japanese")
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()

        val formatId = Format.Builder()
            .setId("audio-id")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("ind")
            .setLabel("Sulih Suara Indonesia")
            .setChannelCount(2)
            .setSampleRate(44100)
            .build()

        val formatEn = Format.Builder()
            .setId("audio-en")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("eng")
            .setLabel("English Dub")
            .setChannelCount(6) // 5.1 Surround
            .setSampleRate(48000)
            .build()

        val trackGroup = TrackGroup("audio_group", formatJp, formatId, formatEn)
        val trackGroupInfo = Tracks.Group(trackGroup, false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(true, false, false))
        val tracks = Tracks(listOf(trackGroupInfo))

        val audioOptions = AnimeTrackSelectionEngine.getAvailableAudioTracks(tracks)

        assertEquals(3, audioOptions.size)

        // Japanese check
        val jpOption = audioOptions[0]
        assertEquals("jpn", jpOption.language)
        assertTrue("Label should mention Jepang", jpOption.label.contains("Jepang"))
        assertTrue("JP should be selected", jpOption.isSelected)

        // Indonesian check
        val idOption = audioOptions[1]
        assertEquals("ind", idOption.language)
        assertTrue("Label should mention Indonesia / Dub Indo", idOption.label.contains("Indonesia"))
        assertFalse("ID should not be selected initially", idOption.isSelected)

        // English check
        val enOption = audioOptions[2]
        assertEquals("eng", enOption.language)
        assertTrue("Label should mention Inggris", enOption.label.contains("Inggris"))
        assertEquals(6, enOption.channelCount)
    }

    @Test
    fun testSelectAudioTrackOverride() {
        val formatId = Format.Builder()
            .setId("audio-id")
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("ind")
            .setLabel("Indonesian Dub")
            .build()

        val trackGroup = TrackGroup("audio_group", formatId)
        val trackGroupInfo = Tracks.Group(trackGroup, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        val tracks = Tracks(listOf(trackGroupInfo))

        val audioOptions = AnimeTrackSelectionEngine.getAvailableAudioTracks(tracks)
        val targetOption = audioOptions.first()

        // Select the Indonesian audio track
        AnimeTrackSelectionEngine.selectAudioTrack(null, trackSelector, targetOption)

        val params = trackSelector.parameters
        assertFalse("Audio track must not be disabled", params.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
        val overrides = params.overrides
        assertTrue("Override should be set for audio group", overrides.containsKey(trackGroup))
    }

    @Test
    fun testSubtitleTrackDiscoveryWithAssSupport() {
        val formatAss = Format.Builder()
            .setId("sub-ass")
            .setSampleMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("ind")
            .setLabel("Indo Fansub ASS")
            .build()

        val formatVtt = Format.Builder()
            .setId("sub-vtt")
            .setSampleMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("eng")
            .setLabel("English Official")
            .build()

        val trackGroup = TrackGroup("sub_group", formatAss, formatVtt)
        val trackGroupInfo = Tracks.Group(trackGroup, false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(true, false))
        val tracks = Tracks(listOf(trackGroupInfo))

        val subList = listOf(
            Subtitle(url = "https://example.com/subs.ass", language = "Indonesia ASS", format = "ass")
        )

        val subtitleOptions = AnimeTrackSelectionEngine.getAvailableSubtitleTracks(tracks, subList)

        // Expect: 1 Off option + 2 embedded tracks + 1 external subtitle
        assertTrue("Must contain multiple subtitle choices", subtitleOptions.size >= 3)

        // Verify Off option
        val offOption = subtitleOptions.first()
        assertTrue(offOption.isOff)
        assertEquals("Mati (Tanpa Subtitle)", offOption.label)

        // Verify ASS option
        val assOption = subtitleOptions.find { it.sampleMimeType == MimeTypes.TEXT_SSA }
        assertNotNull("Must find ASS subtitle option", assOption)
        assertTrue("Should be marked as ASS", assOption!!.isAss)
        assertTrue("Label should indicate ASS Styling", assOption.label.contains("ASS Styling"))

        // Verify VTT option
        val vttOption = subtitleOptions.find { it.sampleMimeType == MimeTypes.TEXT_VTT }
        assertNotNull("Must find VTT subtitle option", vttOption)
        assertFalse("VTT should not be marked as ASS", vttOption!!.isAss)
    }

    @Test
    fun testSelectSubtitleTrackDisableAndEnable() {
        val formatAss = Format.Builder()
            .setId("sub-ass")
            .setSampleMimeType(MimeTypes.TEXT_SSA)
            .setLanguage("ind")
            .build()

        val trackGroup = TrackGroup("sub_group", formatAss)
        val trackGroupInfo = Tracks.Group(trackGroup, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))
        val tracks = Tracks(listOf(trackGroupInfo))

        val subtitleOptions = AnimeTrackSelectionEngine.getAvailableSubtitleTracks(tracks, emptyList())
        val offOption = subtitleOptions.first { it.isOff }
        val assOption = subtitleOptions.first { !it.isOff }

        // Test Disabling Subtitles
        AnimeTrackSelectionEngine.selectSubtitleTrack(null, trackSelector, offOption)
        assertTrue("Subtitle track type must be disabled", trackSelector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))

        // Test Enabling ASS Subtitle
        AnimeTrackSelectionEngine.selectSubtitleTrack(null, trackSelector, assOption)
        assertFalse("Subtitle track type must NOT be disabled", trackSelector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertTrue("Override should be set for text track", trackSelector.parameters.overrides.containsKey(trackGroup))
    }

    @Test
    fun testCustomSubtitleDecoderFactorySupport() {
        val factory = CustomSubtitleDecoderFactory()

        val assFormat = Format.Builder().setSampleMimeType(MimeTypes.TEXT_SSA).build()
        val vttFormat = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build()
        val srtFormat = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build()
        val ttmlFormat = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_TTML).build()
        val unsupportedFormat = Format.Builder().setSampleMimeType("video/mp4").build()

        assertTrue(factory.supportsFormat(assFormat))
        assertTrue(factory.supportsFormat(vttFormat))
        assertTrue(factory.supportsFormat(srtFormat))
        assertTrue(factory.supportsFormat(ttmlFormat))
        assertFalse(factory.supportsFormat(unsupportedFormat))
    }

    @Test
    fun testConfigureSubtitleStyle() {
        val subtitleView = SubtitleView(context)

        AnimeTrackSelectionEngine.configureSubtitleStyle(
            subtitleView = subtitleView,
            fontSizeSp = 22f,
            textColor = Color.WHITE,
            bgColor = Color.TRANSPARENT
        )

        // SubtitleView should have embedded styles applied without crashing
        assertNotNull(subtitleView)
    }
}

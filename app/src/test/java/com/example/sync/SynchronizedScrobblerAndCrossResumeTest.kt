package com.example.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.scrobbler.MediaSyncType
import com.example.data.scrobbler.ScrobbleConfig
import com.example.data.scrobbler.ScrobblerEngine
import com.example.data.scrobbler.SecureCredentialStore
import com.example.data.sync.CrossDeviceResumeManager
import com.example.data.sync.CrossDeviceResumeState
import com.example.data.trackers.TrackerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SynchronizedScrobblerAndCrossResumeTest {

    private lateinit var context: Context
    private lateinit var scrobblerEngine: ScrobblerEngine
    private lateinit var secureStore: SecureCredentialStore
    private lateinit var trackerManager: TrackerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val settings = com.example.data.SettingsManager(context)
        secureStore = com.example.data.scrobbler.SecureCredentialStoreImpl(context, settings)
        trackerManager = TrackerManager(context, settings)
        scrobblerEngine = ScrobblerEngine(
            context = context,
            secureStorage = secureStore,
            trackerManager = trackerManager,
            config = ScrobbleConfig(completionThreshold = 0.85)
        )
        CrossDeviceResumeManager.resetForTesting("device_phone_001")
    }

    @Test
    fun testScrobbleThresholdDetectorAt85Percent() {
        // 80% should not trigger scrobble
        val scrobbledAt80 = scrobblerEngine.checkAndTriggerScrobble(
            currentPosition = 800_000L,
            duration = 1_000_000L,
            mediaId = "anime_99",
            episodeNumber = 1,
            contentType = MediaSyncType.ANIME
        )
        assertFalse("80% progress should not trigger auto-scrobble", scrobbledAt80)

        // 87% (>= 85%) should trigger scrobble
        val scrobbledAt87 = scrobblerEngine.checkAndTriggerScrobble(
            currentPosition = 870_000L,
            duration = 1_000_000L,
            mediaId = "anime_99",
            episodeNumber = 1,
            contentType = MediaSyncType.ANIME
        )
        assertTrue("87% progress should trigger auto-scrobble", scrobbledAt87)

        // Duplicate call for same item & episode should be guarded by deduplication key
        val scrobbledDuplicate = scrobblerEngine.checkAndTriggerScrobble(
            currentPosition = 880_000L,
            duration = 1_000_000L,
            mediaId = "anime_99",
            episodeNumber = 1,
            contentType = MediaSyncType.ANIME
        )
        assertFalse("Duplicate scrobble dispatch must be guarded", scrobbledDuplicate)
    }

    @Test
    fun testCrossDeviceResumeStateFormattingAndJson() {
        val state = CrossDeviceResumeState(
            deviceId = "device_tablet_002",
            deviceName = "Android Tablet",
            itemId = "drama_101",
            sourceId = "jucelebrity",
            contentTitle = "Pertemuan di Tengah Awan",
            episodeOrChapterId = "ep_05",
            episodeOrChapterTitle = "Episode 5",
            contentType = "DRAMA",
            positionMs = 1250000L, // 20 min 50 sec
            durationMs = 2400000L,
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("20:50", state.formattedTimestamp())

        val jsonStr = state.toJson()
        val parsedState = CrossDeviceResumeState.fromJson(jsonStr)

        assertNotNull("Parsed state from JSON must not be null", parsedState)
        assertEquals("Pertemuan di Tengah Awan", parsedState?.contentTitle)
        assertEquals("device_tablet_002", parsedState?.deviceId)
        assertEquals(1250000L, parsedState?.positionMs)
    }

    @Test
    fun testCrossDeviceResumeManagerFilterAndIncomingState() {
        val myDeviceId = "device_phone_001"
        val currentTime = System.currentTimeMillis()

        // 1. Same device ID state should be ignored
        val sameDeviceState = CrossDeviceResumeState(
            deviceId = myDeviceId,
            contentTitle = "Local Playback",
            positionMs = 500000L,
            durationMs = 1000000L,
            updatedAt = currentTime
        )
        CrossDeviceResumeManager.handleIncomingState(sameDeviceState, myDeviceId)
        assertNull("Same device state must be ignored", CrossDeviceResumeManager.incomingResumeState.value)

        // 2. Finished playback (>95%) from another device should be ignored
        val finishedState = CrossDeviceResumeState(
            deviceId = "device_tv_003",
            contentTitle = "Finished Episode",
            positionMs = 980000L,
            durationMs = 1000000L,
            updatedAt = currentTime
        )
        CrossDeviceResumeManager.handleIncomingState(finishedState, myDeviceId)
        assertNull("Nearly finished (>95%) state must be ignored", CrossDeviceResumeManager.incomingResumeState.value)

        // 3. Valid active state (60% playback) from another device should be emitted
        val validState = CrossDeviceResumeState(
            deviceId = "device_tv_003",
            contentTitle = "Pertemuan di Tengah Awan",
            episodeOrChapterTitle = "Episode 3",
            positionMs = 600000L,
            durationMs = 1000000L,
            updatedAt = currentTime
        )
        CrossDeviceResumeManager.handleIncomingState(validState, myDeviceId)
        val emitted = CrossDeviceResumeManager.incomingResumeState.value
        assertNotNull("Valid state from another device must be accepted", emitted)
        assertEquals("device_tv_003", emitted?.deviceId)
        assertEquals("Pertemuan di Tengah Awan", emitted?.contentTitle)
    }
}

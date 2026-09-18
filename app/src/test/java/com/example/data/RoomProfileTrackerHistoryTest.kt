package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.models.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomProfileTrackerHistoryTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ProfileManager.init(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testProfileManagerSwitching() {
        ProfileManager.setCurrentProfile(1L, false)
        assertEquals(1L, ProfileManager.currentProfileIdValue)

        ProfileManager.setCurrentProfile(2L, true)
        assertEquals(2L, ProfileManager.currentProfileIdValue)
        assertEquals(true, ProfileManager.isKidsMode.value)

        // Reset to default
        ProfileManager.setCurrentProfile(1L, false)
    }

    @Test
    fun testMultiProfileTrackIsolation() = runBlocking {
        val mangaId = "one-piece"

        val trackProfile1 = TrackEntity(
            profileId = 1L,
            mangaId = mangaId,
            trackerId = "myanimelist",
            remoteId = 21L,
            title = "One Piece",
            lastChapterRead = 100f
        )

        val trackProfile2 = TrackEntity(
            profileId = 2L,
            mangaId = mangaId,
            trackerId = "myanimelist",
            remoteId = 21L,
            title = "One Piece",
            lastChapterRead = 500f
        )

        db.trackDao().insertTrack(trackProfile1)
        db.trackDao().insertTrack(trackProfile2)

        val profile1Tracks = db.trackDao().getTracksForManga(1L, mangaId).first()
        val profile2Tracks = db.trackDao().getTracksForManga(2L, mangaId).first()

        assertEquals(1, profile1Tracks.size)
        assertEquals(100f, profile1Tracks[0].lastChapterRead, 0.01f)
        assertEquals(1L, profile1Tracks[0].profileId)

        assertEquals(1, profile2Tracks.size)
        assertEquals(500f, profile2Tracks[0].lastChapterRead, 0.01f)
        assertEquals(2L, profile2Tracks[0].profileId)

        val syncTracks1 = db.trackDao().getTracksForMangaSync(1L, mangaId)
        val syncTracks2 = db.trackDao().getTracksForMangaSync(2L, mangaId)

        assertEquals(1, syncTracks1.size)
        assertEquals(100f, syncTracks1[0].lastChapterRead, 0.01f)

        assertEquals(1, syncTracks2.size)
        assertEquals(500f, syncTracks2[0].lastChapterRead, 0.01f)
    }

    @Test
    fun testMultiProfileHistoryIsolationAndProgress() = runBlocking {
        val itemId = "solo-leveling"

        val historyProfile1 = HistoryItem(
            profileId = 1L,
            id = itemId,
            title = "Solo Leveling",
            source = "Mgkomik",
            type = ContentType.COMIC,
            progress = 0.25f,
            lastChapterOrEpisode = "Chapter 25",
            sourceId = "mgkomik",
            timestamp = 1000L
        )

        val historyProfile2 = HistoryItem(
            profileId = 2L,
            id = itemId,
            title = "Solo Leveling",
            source = "Mgkomik",
            type = ContentType.COMIC,
            progress = 0.85f,
            lastChapterOrEpisode = "Chapter 85",
            sourceId = "mgkomik",
            timestamp = 2000L
        )

        db.historyDao().insertHistory(historyProfile1)
        db.historyDao().insertHistory(historyProfile2)

        val profile1Histories = db.historyDao().getAllHistory(1L).first()
        val profile2Histories = db.historyDao().getAllHistory(2L).first()

        assertEquals(1, profile1Histories.size)
        assertEquals(0.25f, profile1Histories[0].progress, 0.01f)
        assertEquals("Chapter 25", profile1Histories[0].lastChapterOrEpisode)

        assertEquals(1, profile2Histories.size)
        assertEquals(0.85f, profile2Histories[0].progress, 0.01f)
        assertEquals("Chapter 85", profile2Histories[0].lastChapterOrEpisode)

        val item1 = db.historyDao().getHistoryItemSync(1L, itemId)
        val item2 = db.historyDao().getHistoryItemSync(2L, itemId)

        assertNotNull(item1)
        assertEquals(0.25f, item1!!.progress, 0.01f)

        assertNotNull(item2)
        assertEquals(0.85f, item2!!.progress, 0.01f)

        val nonExistent = db.historyDao().getHistoryItemSync(3L, itemId)
        assertNull(nonExistent)
    }
}

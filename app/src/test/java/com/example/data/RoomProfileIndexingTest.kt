package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomProfileIndexingTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testMultiProfileLibraryQueryWithIndices() = runBlocking {
        val entryProfile1 = LibraryEntry(
            profileId = 1L,
            sourceId = "mgkomik",
            mangaId = "solo-leveling",
            title = "Solo Leveling",
            favorite = true
        )
        val entryProfile2 = LibraryEntry(
            profileId = 2L,
            sourceId = "asurascans",
            mangaId = "omniscient-reader",
            title = "Omniscient Reader",
            favorite = true
        )

        db.libraryDao().insertLibraryEntry(entryProfile1)
        db.libraryDao().insertLibraryEntry(entryProfile2)

        val profile1Items = db.libraryDao().getAllLibrary(1L).first()
        val profile2Items = db.libraryDao().getAllLibrary(2L).first()

        assertEquals(1, profile1Items.size)
        assertEquals("Solo Leveling", profile1Items[0].title)

        assertEquals(1, profile2Items.size)
        assertEquals("Omniscient Reader", profile2Items[0].title)
    }

    @Test
    fun testMultiProfileHistoryQueryWithIndices() = runBlocking {
        val history1 = HistoryItem(
            profileId = 1L,
            id = "history-1",
            title = "One Piece",
            source = "OtakuDesu",
            type = ContentType.ANIME,
            timestamp = 1000L
        )
        val history2 = HistoryItem(
            profileId = 2L,
            id = "history-2",
            title = "Bleach",
            source = "Kuramanime",
            type = ContentType.ANIME,
            timestamp = 2000L
        )

        db.historyDao().insertHistory(history1)
        db.historyDao().insertHistory(history2)

        val profile1History = db.historyDao().getAllHistory(1L).first()
        val profile2History = db.historyDao().getAllHistory(2L).first()

        assertEquals(1, profile1History.size)
        assertEquals("One Piece", profile1History[0].title)

        assertEquals(1, profile2History.size)
        assertEquals("Bleach", profile2History[0].title)
    }

    @Test
    fun testMultiProfileChaptersQueryWithIndices() = runBlocking {
        val ch1 = ChapterEntry(
            profileId = 1L,
            sourceId = "mgkomik",
            mangaId = "naruto",
            chapterId = "ch-1",
            name = "Chapter 1",
            url = "https://example.com/ch1",
            dateUpload = System.currentTimeMillis(),
            number = 1.0f,
            read = true
        )
        val ch2 = ChapterEntry(
            profileId = 2L,
            sourceId = "mgkomik",
            mangaId = "naruto",
            chapterId = "ch-1",
            name = "Chapter 1",
            url = "https://example.com/ch1",
            dateUpload = System.currentTimeMillis(),
            number = 1.0f,
            read = false
        )

        db.chapterDao().insertChapter(ch1)
        db.chapterDao().insertChapter(ch2)

        val p1Chapters = db.chapterDao().getChaptersList(1L, "mgkomik", "naruto")
        val p2Chapters = db.chapterDao().getChaptersList(2L, "mgkomik", "naruto")

        assertEquals(1, p1Chapters.size)
        assertEquals(true, p1Chapters[0].read)

        assertEquals(1, p2Chapters.size)
        assertEquals(false, p2Chapters[0].read)
    }
}

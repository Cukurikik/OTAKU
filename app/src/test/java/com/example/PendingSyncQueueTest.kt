package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.db.AppDatabase
import com.example.data.models.PendingSyncEntity
import com.example.data.sync.PendingSyncQueue
import com.example.data.trackers.RemoteProgressState
import com.example.data.trackers.TrackerManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PendingSyncQueueTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext<Context>()
        settingsManager = SettingsManager(context)
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrievePendingSync() = runBlocking {
        val syncDao = db.pendingSyncsDao()

        val syncItem = PendingSyncEntity(
            profileId = 1L,
            trackerType = "myanimelist",
            remoteId = 12345L,
            itemId = "manga-1",
            mediaId = "manga-1",
            progress = 10,
            updatedAt = System.currentTimeMillis()
        )

        syncDao.insert(syncItem)

        val syncs = syncDao.getAllPendingActions()
        assertEquals(1, syncs.size)

        val retrieved = syncs[0]
        assertEquals("myanimelist", retrieved.trackerType)
        assertEquals(12345L, retrieved.remoteId)
        assertEquals(10, retrieved.progress)
    }

    @Test
    fun testUpdateProgress() = runBlocking {
        val syncDao = db.pendingSyncsDao()

        val syncItem = PendingSyncEntity(
            profileId = 1L,
            trackerType = "anilist",
            remoteId = 6789L,
            itemId = "anime-2",
            mediaId = "anime-2",
            progress = 5,
            updatedAt = 1000L
        )

        val id = syncDao.insertAction(syncItem)
        
        val existing = syncDao.getPendingAction("anime-2", "anilist")
        assertNotNull(existing)
        
        syncDao.updateProgress(id, 12, 2000L)
        
        val updated = syncDao.getPendingAction("anime-2", "anilist")
        assertEquals(12, updated!!.progress)
        assertEquals(2000L, updated.updatedAt)
    }

    @Test
    fun testLastWriteWinsLocalNewer() = runBlocking {
        val syncDao = db.pendingSyncsDao()
        
        var pushedProgress = -1
        var pushedMediaId = ""

        val mockTrackerManager = object : TrackerManager(context, settingsManager) {
            override suspend fun getRemoteProgress(mediaId: String, trackerType: String): RemoteProgressState? {
                // Server timestamp is older (1000L)
                return RemoteProgressState(progress = 3, updatedAt = 1000L)
            }

            override suspend fun pushProgress(mediaId: String, trackerType: String, progress: Int, remoteId: Long): Boolean {
                pushedMediaId = mediaId
                pushedProgress = progress
                return true
            }
        }

        val queue = PendingSyncQueue(syncDao, mockTrackerManager, context)
        
        // Local action has newer timestamp (5000L)
        syncDao.insertAction(
            PendingSyncEntity(
                mediaId = "one-piece",
                trackerType = "anilist",
                progress = 10,
                updatedAt = 5000L
            )
        )

        assertEquals(1, syncDao.getAllPendingActions().size)

        // Process queue
        queue.processQueue()

        // Verify local state was pushed to remote because local was newer (LWW)
        assertEquals("one-piece", pushedMediaId)
        assertEquals(10, pushedProgress)
        // Verify action was cleared
        assertEquals(0, syncDao.getAllPendingActions().size)
    }

    @Test
    fun testLastWriteWinsRemoteNewer() = runBlocking {
        val syncDao = db.pendingSyncsDao()
        
        var updatedLocalProgress = -1

        val mockTrackerManager = object : TrackerManager(context, settingsManager) {
            override suspend fun getRemoteProgress(mediaId: String, trackerType: String): RemoteProgressState? {
                // Server timestamp is newer (10000L) with progress 15
                return RemoteProgressState(progress = 15, updatedAt = 10000L)
            }

            override suspend fun updateLocalProgress(mediaId: String, progress: Int) {
                updatedLocalProgress = progress
            }
        }

        val queue = PendingSyncQueue(syncDao, mockTrackerManager, context)
        
        // Local action has older timestamp (2000L)
        syncDao.insertAction(
            PendingSyncEntity(
                mediaId = "solo-leveling",
                trackerType = "myanimelist",
                progress = 8,
                updatedAt = 2000L
            )
        )

        // Process queue
        queue.processQueue()

        // Verify local state was updated with server's newer progress (LWW)
        assertEquals(15, updatedLocalProgress)
        // Verify action was cleared
        assertEquals(0, syncDao.getAllPendingActions().size)
    }
}


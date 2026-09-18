package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.example.workers.DownloadWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResilientDownloadEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testWorkManagerConstraints_andExponentialBackoff() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        assertTrue(constraints.requiresStorageNotLow())
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)

        val downloadData = workDataOf(
            "KEY_DOWNLOAD_URL" to "https://anime-aggregator.njirlah.workers.dev/api/stream/test.mp4",
            "KEY_CONTENT_ID" to "kuramanime||one_piece||ep_1000"
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(downloadData)
            .build()

        assertNotNull(request.id)
        assertNotNull(request.workSpec)
    }

    @Test
    fun testDownloadWorkerConstants() {
        assertEquals("KEY_DOWNLOAD_URL", DownloadWorker.KEY_DOWNLOAD_URL)
        assertEquals("KEY_CONTENT_ID", DownloadWorker.KEY_CONTENT_ID)
        assertEquals(5, DownloadWorker.MAX_RETRIES)
    }
}


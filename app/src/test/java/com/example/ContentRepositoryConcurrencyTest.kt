package com.example

import com.example.data.ContentRepository
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.BrowseFilter
import com.example.data.sources.ContentSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ContentRepositoryConcurrencyTest {

    private class MockThrottledSource(
        override val sourceId: String,
        override val sourceName: String,
        private val activeCounter: AtomicInteger,
        private val maxObservedActive: AtomicInteger
    ) : ContentSource {
        override val type: ContentType = ContentType.COMIC

        override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
            val current = activeCounter.incrementAndGet()
            maxObservedActive.updateAndGet { maxOf(it, current) }
            delay(50) // simulate network latency
            activeCounter.decrementAndGet()
            emit(listOf(ContentItem(id = "$sourceId-1", title = "$sourceName Result", source = sourceName, imageUrl = "", type = type, sourceId = sourceId)))
        }

        override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
            val current = activeCounter.incrementAndGet()
            maxObservedActive.updateAndGet { maxOf(it, current) }
            delay(50)
            activeCounter.decrementAndGet()
            emit(listOf(ContentItem(id = "$sourceId-rec", title = "$sourceName Rec", source = sourceName, imageUrl = "", type = type, sourceId = sourceId)))
        }

        override fun browse(filter: BrowseFilter, page: Int): Flow<List<ContentItem>> = flow {
            val current = activeCounter.incrementAndGet()
            maxObservedActive.updateAndGet { maxOf(it, current) }
            delay(50)
            activeCounter.decrementAndGet()
            emit(listOf(ContentItem(id = "$sourceId-browse", title = "$sourceName Browse", source = sourceName, imageUrl = "", type = type, sourceId = sourceId)))
        }

        override fun getChapters(itemId: String): Flow<List<com.example.data.models.Chapter>> = flow {
            emit(emptyList())
        }

        override fun getPages(chapterId: String): Flow<List<com.example.data.models.Page>> = flow {
            emit(emptyList())
        }

        override suspend fun getDetail(itemId: String): ContentItem? {
            return null
        }
    }

    @Test
    fun testSearchThrottlesConcurrencyToMaxFive() = runBlocking {
        val repo = ContentRepository(OkHttpClient())
        val activeCounter = AtomicInteger(0)
        val maxObservedActive = AtomicInteger(0)

        val dummySources = (1..20).map { i ->
            MockThrottledSource(
                sourceId = "source_$i",
                sourceName = "Source $i",
                activeCounter = activeCounter,
                maxObservedActive = maxObservedActive
            )
        }
        repo.sources = dummySources

        val results = mutableListOf<ContentItem>()
        repo.search("test query").collect { batch ->
            results.addAll(batch)
        }

        assertEquals(20, results.size)
        assertTrue("Max concurrent active sources should not exceed 5, was: ${maxObservedActive.get()}", maxObservedActive.get() <= 5)
        assertTrue("Should have processed with concurrency up to 5", maxObservedActive.get() > 1)
    }

    @Test
    fun testBrowseThrottlesConcurrencyToMaxFive() = runBlocking {
        val repo = ContentRepository(OkHttpClient())
        val activeCounter = AtomicInteger(0)
        val maxObservedActive = AtomicInteger(0)

        val dummySources = (1..15).map { i ->
            MockThrottledSource(
                sourceId = "source_$i",
                sourceName = "Source $i",
                activeCounter = activeCounter,
                maxObservedActive = maxObservedActive
            )
        }
        repo.sources = dummySources

        val results = mutableListOf<ContentItem>()
        repo.browse().collect { batch ->
            results.addAll(batch)
        }

        assertEquals(15, results.size)
        assertTrue("Max concurrent active sources should not exceed 5, was: ${maxObservedActive.get()}", maxObservedActive.get() <= 5)
    }
}

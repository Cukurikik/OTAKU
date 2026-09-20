package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.viki.VikiSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VikiApiSourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testVikiSourceInheritanceAndProperties() {
        val viki = VikiSource(context)
        assertEquals("viki", viki.sourceId)
        assertEquals("Viki", viki.sourceName)
        assertEquals("https://www.viki.com", viki.baseUrl)
        assertEquals(ContentType.DRAMA, viki.contentType)
        assertTrue("VikiSource must be an instance of BaseDramaSource", viki is BaseDramaSource)
    }

    @Test
    fun testExtractContainerAndVideoIds() {
        val viki = VikiSource(context)

        // Raw container IDs
        assertEquals("38421c", viki.extractContainerId("38421c"))
        assertEquals("50000c", viki.extractContainerId("50000c"))

        // Web URLs
        assertEquals("38421c", viki.extractContainerId("https://www.viki.com/tv/38421c-true-beauty"))
        assertEquals("38421c", viki.extractContainerId("https://api.viki.io/v4/containers/38421c.json"))

        // Video IDs
        assertEquals("1175841v", viki.extractVideoId("1175841v"))
        assertEquals("1175841v", viki.extractVideoId("https://www.viki.com/videos/1175841v-true-beauty-episode-1"))
        assertEquals("1175841v", viki.extractVideoId("https://api.viki.io/v4/videos/1175841v/streams.json"))
    }

    @Test
    fun testParseContainersJson() {
        val viki = VikiSource(context)

        val sampleJson = """
            {
              "response": [
                {
                  "id": "38421c",
                  "titles": {
                    "id": "Kecantikan Sejati (True Beauty)",
                    "en": "True Beauty",
                    "default": "True Beauty"
                  },
                  "descriptions": {
                    "id": "Kisah romansa komedi remaja tentang seorang siswi SMA...",
                    "en": "A high school girl who masters the art of makeup..."
                  },
                  "images": {
                    "poster": { "url": "https://img.viki.com/true_beauty_poster.jpg" },
                    "thumbnail": { "url": "https://img.viki.com/true_beauty_thumb.jpg" }
                  },
                  "genres": ["romance", "comedy", "youth"],
                  "origin": { "country": "kr" },
                  "type": "series",
                  "created_at": "2020-12-09T00:00:00Z",
                  "blocked": false
                },
                {
                  "id": "40123c",
                  "titles": {
                    "en": "Hidden Love",
                    "default": "Hidden Love"
                  },
                  "descriptions": {
                    "en": "Sang Zhi falls in love with Duan Jia Xu..."
                  },
                  "images": {
                    "poster": { "url": "https://img.viki.com/hidden_love.jpg" }
                  },
                  "genres": ["romance"],
                  "origin": { "country": "cn" },
                  "type": "series",
                  "created_at": "2023-06-20T00:00:00Z",
                  "blocked": true
                }
              ],
              "more": true
            }
        """.trimIndent()

        val items = viki.parseContainersJson(JSONObject(sampleJson))
        assertEquals(2, items.size)

        // Item 1 verification
        val item1 = items[0]
        assertEquals("38421c", item1.id)
        assertEquals("Kecantikan Sejati (True Beauty)", item1.title)
        assertEquals("https://img.viki.com/true_beauty_poster.jpg", item1.imageUrl)
        assertEquals(ContentType.DRAMA, item1.type)
        assertTrue(item1.genres.contains("Romance"))
        assertTrue(item1.genres.contains("Comedy"))
        assertTrue(item1.genres.contains("KR"))
        assertEquals("Active", item1.status)
        assertEquals("2020", item1.year)

        // Item 2 verification
        val item2 = items[1]
        assertEquals("40123c", item2.id)
        assertEquals("Hidden Love", item2.title)
        assertTrue(item2.genres.contains("CN"))
        assertEquals("Restricted", item2.status)
        assertEquals("2023", item2.year)
    }

    @Test
    fun testParseVideosJson() {
        val viki = VikiSource(context)

        val sampleVideosJson = """
            {
              "response": [
                {
                  "id": "1175841v",
                  "number": 1,
                  "titles": {
                    "id": "Episode 1",
                    "en": "Episode 1"
                  },
                  "created_at": "2020-12-09T14:00:00Z",
                  "url": {
                    "web": "https://www.viki.com/videos/1175841v-true-beauty-episode-1"
                  }
                },
                {
                  "id": "1175842v",
                  "number": 2,
                  "titles": {
                    "en": "Episode 2"
                  },
                  "created_at": "2020-12-10T14:00:00Z",
                  "url": {
                    "web": "https://www.viki.com/videos/1175842v-true-beauty-episode-2"
                  }
                }
              ],
              "more": false
            }
        """.trimIndent()

        val seasons = viki.parseVideosJson(JSONObject(sampleVideosJson))
        assertEquals(1, seasons.size)

        val s1 = seasons[0]
        assertEquals(1, s1.seasonNumber)
        assertEquals(2, s1.episodes.size)

        assertEquals(1, s1.episodes[0].episodeNumber)
        assertEquals("Episode 1", s1.episodes[0].title)
        assertEquals("https://www.viki.com/videos/1175841v-true-beauty-episode-1", s1.episodes[0].url)
        assertEquals("2020-12-09", s1.episodes[0].date)

        assertEquals(2, s1.episodes[1].episodeNumber)
        assertEquals("Episode 2", s1.episodes[1].title)
        assertEquals("https://www.viki.com/videos/1175842v-true-beauty-episode-2", s1.episodes[1].url)
    }
}

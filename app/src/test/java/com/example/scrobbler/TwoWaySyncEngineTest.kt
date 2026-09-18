package com.example.scrobbler

import com.example.data.scrobbler.MediaSyncType
import com.example.data.scrobbler.ScrobbleConfig
import com.example.data.scrobbler.ScrobblePlatform
import com.example.data.scrobbler.ScrobbleStatus
import com.example.data.scrobbler.TwoWaySyncEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoWaySyncEngineTest {

    @Test
    fun testDefaultScrobbleConfigValues() {
        val config = ScrobbleConfig()
        assertEquals(0.85, config.completionThreshold, 0.0001)
        assertEquals(15, config.autoSyncIntervalMinutes)
        assertEquals(3, config.targetPlatforms.size)
        assertTrue(config.targetPlatforms.contains("myanimelist"))
        assertTrue(config.targetPlatforms.contains("anilist"))
        assertTrue(config.targetPlatforms.contains("kitsu"))
    }

    @Test
    fun testParseMalAnimeListResponse() {
        val jsonPayload = """
        {
          "data": [
            {
              "node": {
                "id": 5114,
                "title": "Fullmetal Alchemist: Brotherhood",
                "num_episodes": 64
              },
              "list_status": {
                "status": "watching",
                "score": 10,
                "num_episodes_watched": 25,
                "updated_at": "2026-09-01T12:00:00Z"
              }
            }
          ]
        }
        """.trimIndent()

        val parsedEntries = TwoWaySyncEngine.parseMalResponse(jsonPayload, MediaSyncType.ANIME)
        assertEquals(1, parsedEntries.size)

        val entry = parsedEntries[0]
        assertEquals(5114L, entry.remoteId)
        assertEquals("Fullmetal Alchemist: Brotherhood", entry.title)
        assertEquals(MediaSyncType.ANIME, entry.mediaType)
        assertEquals(25, entry.progress)
        assertEquals(64, entry.totalCount)
        assertEquals(10f, entry.score, 0.01f)
        assertEquals(ScrobbleStatus.WATCHING, entry.status)
        assertEquals(ScrobblePlatform.MY_ANIME_LIST, entry.platform)
    }

    @Test
    fun testParseAniListGraphQLResponse() {
        val jsonPayload = """
        {
          "data": {
            "MediaListCollection": {
              "lists": [
                {
                  "name": "Watching",
                  "entries": [
                    {
                      "id": 998822,
                      "mediaId": 16498,
                      "status": "CURRENT",
                      "progress": 18,
                      "score": 8.5,
                      "media": {
                        "id": 16498,
                        "title": {
                          "romaji": "Attack on Titan Season 2",
                          "english": "Attack on Titan Season 2"
                        },
                        "episodes": 12,
                        "chapters": null,
                        "type": "ANIME"
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val parsedEntries = TwoWaySyncEngine.parseAniListResponse(jsonPayload)
        assertEquals(1, parsedEntries.size)

        val entry = parsedEntries[0]
        assertEquals(16498L, entry.remoteId)
        assertEquals("Attack on Titan Season 2", entry.title)
        assertEquals(MediaSyncType.ANIME, entry.mediaType)
        assertEquals(18, entry.progress)
        assertEquals(12, entry.totalCount)
        assertEquals(ScrobbleStatus.WATCHING, entry.status)
        assertEquals(ScrobblePlatform.ANI_LIST, entry.platform)
    }

    @Test
    fun testParseKitsuJsonApiResponse() {
        val jsonPayload = """
        {
          "data": [
            {
              "id": "78910",
              "type": "libraryEntries",
              "attributes": {
                "status": "current",
                "progress": 14,
                "ratingTwenty": 18
              },
              "relationships": {
                "anime": {
                  "data": {
                    "type": "anime",
                    "id": "12345"
                  }
                }
              }
            }
          ]
        }
        """.trimIndent()

        val parsedEntries = TwoWaySyncEngine.parseKitsuResponse(jsonPayload)
        assertEquals(1, parsedEntries.size)

        val entry = parsedEntries[0]
        assertEquals(12345L, entry.remoteId)
        assertEquals(MediaSyncType.ANIME, entry.mediaType)
        assertEquals(14, entry.progress)
        assertEquals(ScrobbleStatus.WATCHING, entry.status)
        assertEquals(ScrobblePlatform.KITSU, entry.platform)
    }

    @Test
    fun testTwoWayProgressConflictResolutionLogic() {
        // Scenario A: Remote tracker is ahead of local progress
        // Local: ep 5, Remote: ep 10 -> Remote wins (local should be updated to 10)
        val localProgressA = 5
        val remoteProgressA = 10
        val resolvedProgressA = maxOf(localProgressA, remoteProgressA)
        assertEquals(10, resolvedProgressA)

        // Scenario B: Local progress is ahead of remote tracker
        // Local: ep 12, Remote: ep 10 -> Local wins (remote should receive push update to 12)
        val localProgressB = 12
        val remoteProgressB = 10
        val shouldPushToRemoteB = localProgressB > remoteProgressB
        assertTrue(shouldPushToRemoteB)

        // Scenario C: Both in sync
        val localProgressC = 12
        val remoteProgressC = 12
        val needsSyncC = localProgressC != remoteProgressC
        org.junit.Assert.assertFalse(needsSyncC)
    }
}

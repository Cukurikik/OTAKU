package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.direct.ScraperSourceConfig
import com.example.data.sources.direct.Selectors
import com.example.data.sources.dynamic.DynamicRegexes
import com.example.data.sources.dynamic.DynamicSelectors
import com.example.data.sources.dynamic.DynamicSourceConfig
import com.example.data.sources.dynamic.DynamicSourceConfigManager
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DynamicSourceConfigTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DynamicSourceConfigManager.resetToDefaults(context)
        DynamicSourceConfigManager.init(context)
    }

    @Test
    fun testDefaultConfigsLoaded() {
        val kuramanimeCfg = DynamicSourceConfigManager.getConfig("kuramanime")
        assertNotNull("Kuramanime default config should be seeded", kuramanimeCfg)
        assertEquals("kuramanime", kuramanimeCfg?.sourceId)
        assertNotNull(kuramanimeCfg?.selectors?.card)
        assertNotNull(kuramanimeCfg?.regexes?.regexM3u8)

        val asuraCfg = DynamicSourceConfigManager.getConfig("asurascans")
        assertNotNull("AsuraScans default config should be seeded", asuraCfg)
    }

    @Test
    fun testDynamicSelectorInjectionIntoScraperSourceConfig() {
        val originalSelectors = Selectors(
            card = ".old-card",
            cardTitle = ".old-title",
            episode = ".old-episode"
        )
        val config = ScraperSourceConfig(
            sourceId = "test_source",
            sourceName = "Test Source",
            baseUrl = "https://example.com",
            searchUrl = { q, p -> "https://example.com/search" },
            popularUrl = { p -> "https://example.com/popular" },
            selectors = originalSelectors
        )

        // Before dynamic update, matches raw original selectors
        assertEquals(".old-card", config.effectiveSelectors.card)
        assertEquals(".old-title", config.effectiveSelectors.cardTitle)

        // Site redesign occurs: Remote OTA delivers new selectors
        val newDynamicConfig = DynamicSourceConfig(
            sourceId = "test_source",
            version = 105,
            selectors = DynamicSelectors(
                card = "div.new-redesigned-card",
                cardTitle = "h3.new-heading-title",
                episode = "ul.redesigned-episodes li"
            )
        )
        DynamicSourceConfigManager.updateConfig(context, newDynamicConfig)

        // Verify ScraperSourceConfig automatically resolves to the new dynamic selectors!
        assertEquals("div.new-redesigned-card", config.effectiveSelectors.card)
        assertEquals("h3.new-heading-title", config.effectiveSelectors.cardTitle)
        assertEquals("ul.redesigned-episodes li", config.effectiveSelectors.episode)

        // Verify parsing redesigned DOM without APK change
        val sampleRedesignedHtml = """
            <div class="new-redesigned-card">
                <a href="/watch/123">
                    <h3 class="new-heading-title">Solo Leveling Season 2</h3>
                </a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(sampleRedesignedHtml)
        val card = doc.selectFirst(config.effectiveSelectors.card)
        assertNotNull("Should select card using dynamic OTA selector", card)
        val title = card?.selectFirst(config.effectiveSelectors.cardTitle)?.text()
        assertEquals("Solo Leveling Season 2", title)
    }

    @Test
    fun testDynamicRegexExtraction() {
        val defaultM3u8Regex = DynamicSourceConfigManager.getRegex("test_source", "regex_m3u8", """source\s*:\s*["']([^"']+)["']""")
        
        // Update with OTA custom regex
        val dynamicConfig = DynamicSourceConfig(
            sourceId = "test_source",
            version = 102,
            regexes = DynamicRegexes(
                regexM3u8 = """hlsManifestUrl\s*=\s*["']([^"']+\.m3u8)"""
            )
        )
        DynamicSourceConfigManager.updateConfig(context, dynamicConfig)

        val updatedRegex = DynamicSourceConfigManager.getRegex("test_source", "regex_m3u8", """source\s*:\s*["']([^"']+)["']""")
        val scriptPayload = """var hlsManifestUrl = "https://cdn.example.com/live/master.m3u8";"""
        val match = updatedRegex.find(scriptPayload)
        
        assertNotNull("Should match using OTA updated regex", match)
        assertEquals("https://cdn.example.com/live/master.m3u8", match?.groupValues?.get(1))
    }
}

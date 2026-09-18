package com.example.catalog

import com.example.data.catalog.HubMode
import com.example.data.sources.registry.SourceMode
import com.example.data.sources.registry.SourceEndpointRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class HubModeTest {
    @Test fun registryContainsAllModes() {
        assertTrue(SourceEndpointRegistry.forMode(SourceMode.COMIC).isNotEmpty())
        assertTrue(SourceEndpointRegistry.forMode(SourceMode.ANIME).isNotEmpty())
        assertTrue(SourceEndpointRegistry.forMode(SourceMode.DRAMA).isNotEmpty())
    }

    @Test fun hubModesMapToContentTypes() {
        assertTrue(HubMode.COMIC.contentType.name == "COMIC")
        assertTrue(HubMode.ANIME.contentType.name == "ANIME")
        assertTrue(HubMode.DRAMA.contentType.name == "DRAMA")
    }
}

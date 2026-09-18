package com.example.catalog

import com.example.data.sources.direct.DirectSourcePolicy
import com.example.data.sources.registry.SourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectSourcePolicyTest {
    @Test fun comicAllowsExtension() {
        assertTrue(DirectSourcePolicy.acceptsExtension(SourceMode.COMIC))
    }

    @Test fun animeRejectsExtension() {
        assertFalse(DirectSourcePolicy.acceptsExtension(SourceMode.ANIME))
    }

    @Test fun dramaRejectsExtension() {
        assertFalse(DirectSourcePolicy.acceptsExtension(SourceMode.DRAMA))
    }
}

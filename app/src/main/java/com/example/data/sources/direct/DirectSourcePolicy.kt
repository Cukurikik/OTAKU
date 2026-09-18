package com.example.data.sources.direct

import com.example.data.sources.registry.SourceMode

/** Hard architectural rule: only COMIC may be extension-backed. */
object DirectSourcePolicy {
    fun implementationFor(mode: SourceMode): String = when (mode) {
        SourceMode.COMIC -> "DIRECT_SCRAPER_OR_EXTENSION"
        SourceMode.ANIME, SourceMode.DRAMA -> "DIRECT_SCRAPER_ONLY"
    }

    fun acceptsExtension(mode: SourceMode): Boolean = mode == SourceMode.COMIC
}

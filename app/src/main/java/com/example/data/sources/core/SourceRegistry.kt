package com.example.data.sources.core

import android.content.Context
import com.example.data.sources.ContentSource
import com.example.data.sources.direct.ComicDirectSourceCatalog

class SourceRegistry(private val context: Context) {
    
    val comicSources: List<ContentSource> by lazy {
        ComicDirectSourceCatalog.createSources(context)
    }

    fun getComicSource(id: String): ContentSource? {
        return comicSources.find { it.sourceId == id }
    }
}

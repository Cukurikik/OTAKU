package com.example.data.catalog

import com.example.data.models.ContentType

/** Top-level content mode. The selected mode is a UI/repository filter, not a separate app. */
enum class HubMode(val label: String, val contentType: ContentType) {
    ANIME("Anime", ContentType.ANIME),
    COMIC("Komik", ContentType.COMIC),
    DRAMA("Drama", ContentType.DRAMA);
}

#!/bin/bash
awk '
/override fun getChapters\(itemId: String\): Flow<List<Chapter>> = flow \{/ {
    print "    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {"
    print "        try {"
    print "            val doc = fetchDocument(itemId)"
    print "            val episodes = doc.select("
    print "                \".episode-list li, .eps-list li, .episodelist li, .ep-list a, .gmr-listseries a, .hal-episodes li a\""
    print "            ).mapNotNull { el ->"
    print "                val a = if (el.tagName() == \"a\") el else el.selectFirst(\"a\")"
    print "                if (a == null) return@mapNotNull null"
    print "                val href = a.absUrl(\"href\")"
    print "                val num = el.selectFirst(\".num, .ep-num, span:first-child\")?.text() ?: a.text()"
    print "                val title = if (num.lowercase().contains(\"episode\") || num.lowercase().contains(\"ep\")) num else \"Episode $num\""
    print "                Chapter(id = href, title = title.takeIf { num.isNotEmpty() } ?: a.text(), url = href)"
    print "            }"
    print "            if (episodes.isNotEmpty()) {"
    print "                SourceStatusBus.clear(sourceId)"
    print "                emit(episodes)"
    print "            } else {"
    print "                // Check if it is a single movie page"
    print "                val hasVideo = doc.selectFirst(\"iframe, .gmr-watch-btn, .cmn-btn, video, .player, #embed_holder\") != null"
    print "                if (hasVideo) {"
    print "                    SourceStatusBus.clear(sourceId)"
    print "                    emit(listOf(Chapter(id = itemId, title = \"Tonton Film\", url = itemId)))"
    print "                } else {"
    print "                    throw Exception(\"Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.\")"
    print "                }"
    print "            }"
    print "        } catch (e: kotlinx.coroutines.CancellationException) { throw e"
    print "        } catch (e: Exception) {"
    print "            SourceStatusBus.reportFromException(sourceId, sourceName, e)"
    print "            throw Exception(\"Situs ini telah mengubah strukturnya. Silakan ganti server atau hubungi developer.\")"
    print "        }"
    print "    }"
    skip = 1
    next
}
skip && /^    override fun getVideoStream/ {
    skip = 0
    print $0
    next
}
!skip { print $0 }
' app/src/main/java/com/example/data/sources/base/BaseDramaSource.kt > temp.kt && mv temp.kt app/src/main/java/com/example/data/sources/base/BaseDramaSource.kt

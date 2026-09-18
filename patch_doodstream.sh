#!/bin/bash
awk '
/embedUrl\.contains\("doodstream"\) \|\| embedUrl\.contains\("dood\."\)/ {
    print "                    embedUrl.contains(\"doodstream\") || embedUrl.contains(\"dood.\") || embedUrl.contains(\"dood\") -> extractDoodstream(embedUrl)"
    print "                    embedUrl.contains(\"streamwish\") || embedUrl.contains(\"vidhide\") || embedUrl.contains(\"filelions\") || embedUrl.contains(\"vtube\") -> extractGeneric(embedUrl, referer)"
    next
}
/private suspend fun extractDoodstream\(url: String\): VideoStreamInfo\? \{/ {
    print "    private suspend fun extractDoodstream(url: String): VideoStreamInfo? {"
    print "        val html = fetchHtml(url) ?: return null"
    print "        val passRegex = Regex(\"\"\"/pass_md5/[^\\\"'\\\\]+\"\"\")"
    print "        val pass = passRegex.find(html)?.value ?: return null"
    print "        val host = android.net.Uri.parse(url).host ?: \"dood.to\""
    print "        val md5Url = \"https://$host$pass\""
    print "        val md5Html = fetchHtml(md5Url, mapOf(\"Referer\" to url)) ?: return null"
    print "        val tokenRegex = Regex(\"\"\"[?&]token=([^&\\\"']+)\"\"\")"
    print "        val token = tokenRegex.find(html)?.groupValues?.get(1) ?: \"\""
    print "        val timestamp = System.currentTimeMillis() / 1000"
    print "        val videoUrl = \"$md5Html${(1..10).map { (\\\'a\\\'..\\\'z\\\').random() }.joinToString(\"\")}?token=$token&expiry=$timestamp\""
    print "        return VideoStreamInfo("
    print "            episodeId = url,"
    print "            qualities = listOf(VideoQuality(\"auto\", videoUrl, false, mapOf(\"Referer\" to \"https://$host/\")))"
    print "        )"
    print "    }"
    skip = 1
    next
}
skip && /^    private suspend fun fetchHtml/ {
    skip = 0
    print $0
    next
}
!skip { print $0 }
' app/src/main/java/com/example/data/sources/VideoExtractor.kt > temp.kt && mv temp.kt app/src/main/java/com/example/data/sources/VideoExtractor.kt

import re

with open('app/src/main/java/com/example/data/sources/VideoExtractor.kt', 'r') as f:
    content = f.read()

# Add routing for streamwish, vidhide, etc.
content = content.replace(
    'embedUrl.contains("doodstream") || embedUrl.contains("dood.") -> extractDoodstream(embedUrl)',
    'embedUrl.contains("doodstream") || embedUrl.contains("dood.") || embedUrl.contains("dood") -> extractDoodstream(embedUrl)\n                    embedUrl.contains("streamwish") || embedUrl.contains("vidhide") || embedUrl.contains("filelions") || embedUrl.contains("vtube") -> extractGeneric(embedUrl, referer)'
)

# Replace extractDoodstream
doodstream_pattern = r'private suspend fun extractDoodstream\(url: String\): VideoStreamInfo\? \{.*?\n    \}'
new_doodstream = '''private suspend fun extractDoodstream(url: String): VideoStreamInfo? {
        val html = fetchHtml(url) ?: return null
        val passRegex = Regex("""/pass_md5/[^\"\'\\\\]+""")
        val pass = passRegex.find(html)?.value ?: return null
        val host = android.net.Uri.parse(url).host ?: "dood.to"
        val md5Url = "https://$host$pass"
        val md5Html = fetchHtml(md5Url, mapOf("Referer" to url)) ?: return null
        val tokenRegex = Regex("""[?&]token=([^&\"']+)""")
        val token = tokenRegex.find(html)?.groupValues?.get(1) ?: Regex("""\"token\"[ :]+\"([^\"]+)\"""").find(html)?.groupValues?.get(1) ?: ""
        val timestamp = System.currentTimeMillis() / 1000
        val videoUrl = "$md5Html${(1..10).map { ('a'..'z').random() }.joinToString("")}?token=$token&expiry=$timestamp"
        return VideoStreamInfo(
            episodeId = url,
            qualities = listOf(VideoQuality("auto", videoUrl, false, mapOf("Referer" to "https://$host/")))
        )
    }'''

content = re.sub(doodstream_pattern, new_doodstream, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/sources/VideoExtractor.kt', 'w') as f:
    f.write(content)

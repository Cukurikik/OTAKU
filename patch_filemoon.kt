    private suspend fun extractFilemoon(url: String, referer: String): VideoStreamInfo? {
        val html = fetchHtml(url, mapOf("Referer" to referer)) ?: return null
        val m3u8 = Regex("""sources:\s*\[\{file:"([^"]+\.m3u8[^"]*)" """).find(html)?.groupValues?.get(1) ?: return null
        
        val subtitleTracks = mutableListOf<Subtitle>()
        val tracksRegex = Regex("""tracks:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val tracksMatch = tracksRegex.find(html)
        if (tracksMatch != null) {
            val trackBlocks = Regex("""\{([^}]*)\}""").findAll(tracksMatch.groupValues[1])
            for (block in trackBlocks) {
                val blockHtml = block.groupValues[1]
                val file = Regex("""file:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1)
                val label = Regex("""label:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1) ?: "Indonesian"
                val kind = Regex("""kind:\s*["']([^"']+)["']""").find(blockHtml)?.groupValues?.get(1)
                if (file != null && kind == "captions") {
                    subtitleTracks.add(Subtitle(language = label, url = file, format = file.substringAfterLast(".", "vtt")))
                }
            }
        }
        
        return VideoStreamInfo(
            episodeId = url,
            qualities = listOf(VideoQuality("auto", m3u8, true, mapOf("Referer" to url, "Origin" to url.substringBefore("/") + "//" + android.net.Uri.parse(url).host))),
            subtitles = subtitleTracks
        )
    }

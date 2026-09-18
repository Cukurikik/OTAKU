// just testing the regex for blogger
fun main() {
    val html = """var VIDEO_CONFIG = {"streams":[{"play_url":"https://example.com/video.mp4","format_id":22}]};"""
    val configMatch = Regex("""var\s+VIDEO_CONFIG\s*=\s*(\{.*?\});""").find(html)
    if (configMatch != null) {
        val config = configMatch.groupValues[1]
        val streamsMatch = Regex(""""streams"\s*:\s*\[(.*?)\]""").find(config)
        if (streamsMatch != null) {
            val streams = streamsMatch.groupValues[1]
            val streamBlocks = Regex("""\{([^}]+)\}""").findAll(streams)
            for (block in streamBlocks) {
                val playUrl = Regex(""""play_url"\s*:\s*"([^"]+)"""").find(block.value)?.groupValues?.get(1)
                val formatId = Regex(""""format_id"\s*:\s*(\d+)""").find(block.value)?.groupValues?.get(1)?.toIntOrNull()
                println("PlayUrl: $playUrl, FormatId: $formatId")
            }
        }
    }
}

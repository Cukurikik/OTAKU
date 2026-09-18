fun main() {
    val html = "\"streams\":[{\"play_url\":\"https://...\",\"format_id\":22}]"
    val streamsMatch = Regex("\"streams\":\\[(.*?)\\]").find(html)
    println(streamsMatch?.groupValues?.get(1))
}

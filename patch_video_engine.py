import re

with open("app/src/main/java/com/example/data/sources/VideoExtractorEngine.kt", "r") as f:
    code = f.read()

fallback_logic = """
        // Final fallback: just return the embed url and let the UI handle it (e.g. open in external browser)
        return@withContext VideoStreamInfo(episodeId = episodeId, embedUrl = episodeUrl)
"""

new_fallback_logic = """
        // Use WebView Interceptor for dynamic extraction if everything else fails
        try {
            val webViewFetcher = WebViewFetcher(context)
            val interceptedUrl = webViewFetcher.interceptVideoUrl(episodeUrl)
            
            if (interceptedUrl.contains(".m3u8") || interceptedUrl.contains(".mp4") || interceptedUrl.contains(".webm")) {
                val qualities = if (interceptedUrl.contains(".m3u8")) {
                    listOf(VideoQuality("Auto", interceptedUrl)) 
                } else {
                    listOf(VideoQuality("Lokal/Raw", interceptedUrl))
                }
                
                return@withContext VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = episodeUrl,
                    qualities = qualities
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Final fallback: just return the embed url and let the UI handle it (e.g. open in external browser)
        return@withContext VideoStreamInfo(episodeId = episodeId, embedUrl = episodeUrl)
"""

code = code.replace(fallback_logic, new_fallback_logic)

with open("app/src/main/java/com/example/data/sources/VideoExtractorEngine.kt", "w") as f:
    f.write(code)

import re

with open("app/src/main/java/com/example/data/sources/anime/base/AnimeStreamThemeScraper.kt", "r") as f:
    content = f.read()

target = """            // 4. Resolve the best playable stream using the engine
            if (qualities.isNotEmpty()) {
                    
                for (embedUrl in embedUrls) {
                    val engineInfo = com.example.data.sources.VideoExtractor.extract(embedUrl, episodeId)
                    if (engineInfo != null && engineInfo.qualities.isNotEmpty()) {
                        emit(engineInfo)
                        return@flow
                    }
                }
                
                // Fallback to passing iframe urls (will use webview)
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first(), qualities = qualities))
                return@flow
            }"""

replacement = """            // 4. Resolve the best playable stream using the engine
            if (qualities.isNotEmpty()) {
                val engine = com.example.data.sources.VideoExtractorEngine(context)    
                for (embedUrl in embedUrls) {
                    val engineInfo = engine.extractVideo(embedUrl, episodeId)
                    if (engineInfo != null && engineInfo.qualities.isNotEmpty()) {
                        emit(engineInfo)
                        return@flow
                    }
                }
                
                // Fallback to passing iframe urls (will use webview)
                // Filter out non-mp4/m3u8 from qualities if we rely on WebView fallback
                val finalQualities = qualities.filter { it.url.contains(".mp4") || it.url.contains(".m3u8") }
                if (finalQualities.isNotEmpty()) {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = finalQualities.first().url, qualities = finalQualities))
                } else {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first()))
                }
                return@flow
            }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/data/sources/anime/base/AnimeStreamThemeScraper.kt", "w") as f:
    f.write(content)

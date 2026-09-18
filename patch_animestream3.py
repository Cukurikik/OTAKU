with open("app/src/main/java/com/example/data/sources/anime/base/AnimeStreamThemeScraper.kt", "r") as f:
    content = f.read()

bad_block = """            // 4. Resolve the best playable stream using the engine
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
                val finalQualities = qualities.filter { it.url.contains(".mp4") || it.url.contains(".m3u8") }
                if (finalQualities.isNotEmpty()) {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = finalQualities.first().url, qualities = finalQualities))
                } else {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first()))
                }
                return@flow
            }
                }
                
                // Fallback to passing iframe urls (will use webview)
                emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first(), qualities = qualities))
                return@flow
            }"""

good_block = """            // 4. Resolve the best playable stream using the engine
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
                val finalQualities = qualities.filter { it.url.contains(".mp4") || it.url.contains(".m3u8") }
                if (finalQualities.isNotEmpty()) {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = finalQualities.first().url, qualities = finalQualities))
                } else {
                    emit(VideoStreamInfo(episodeId = episodeId, embedUrl = embedUrls.first()))
                }
                return@flow
            }"""

content = content.replace(bad_block, good_block)

with open("app/src/main/java/com/example/data/sources/anime/base/AnimeStreamThemeScraper.kt", "w") as f:
    f.write(content)

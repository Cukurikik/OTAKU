sed -i '39,45c\
        if (isCfChallenge) {\
            try {\
                val host = java.net.URL(url).host\
                com.example.data.NetworkModule.markAsBlocked(host)\
                doc = fetcher.fetchHtml(url)\
            } catch (e: Exception) {\
                throw Exception("Cloudflare bypass worker failed for $url: ${e.message}")\
            }\
        }' app/src/main/java/com/example/data/sources/base/BaseMangaSource.kt

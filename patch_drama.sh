sed -i '30,35c\
        if (doc == null || title.contains("just a moment") || title.contains("cloudflare") || title.contains("access denied") || title.contains("302 found") || doc.text().contains("cloudflare")) {\
            try {\
                val host = java.net.URL(url).host\
                com.example.data.NetworkModule.markAsBlocked(host)\
                doc = fetcher.fetchHtml(url)\
            } catch (e: Exception) {\
                throw Exception("Cloudflare bypass worker failed for $url: ${e.message}")\
            }\
        }' app/src/main/java/com/example/data/sources/base/BaseDramaSource.kt

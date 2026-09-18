package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeSource
import okhttp3.OkHttpClient
import okhttp3.Headers

abstract class AnimeHttpSource : AnimeSource {
    abstract val baseUrl: String
    open val client: OkHttpClient = OkHttpClient()
    open fun headersBuilder(): Headers.Builder = Headers.Builder()
    open val headers: Headers by lazy { headersBuilder().build() }
}

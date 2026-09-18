package eu.kanade.tachiyomi.source.online
import kotlinx.coroutines.launch

import eu.kanade.tachiyomi.source.asFlow
import rx.Observable

import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.ContentSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody

abstract class HttpSource : Source, ContentSource {
    abstract val baseUrl: String

    override val id: Long by lazy {
        sourceId.hashCode().toLong()
    }

    override val name: String
        get() = sourceName

    override val lang: String = "id"

    // Default headers
    open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .add("Referer", baseUrl)

    open val headers: Headers by lazy { headersBuilder().build() }

    // OkHttpClient with default setup
    open val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val hasUserAgent = request.header("User-Agent") != null
                val hasReferer = request.header("Referer") != null
                val builder = request.newBuilder()
                if (!hasUserAgent) {
                    headers["User-Agent"]?.let { builder.header("User-Agent", it) }
                }
                if (!hasReferer) {
                    headers["Referer"]?.let { builder.header("Referer", it) }
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    // Helper requests
    fun GET(url: String, headers: Headers = this.headers): Request {
        return Request.Builder()
            .url(url)
            .headers(headers)
            .build()
    }

    fun POST(url: String, headers: Headers = this.headers, body: okhttp3.RequestBody): Request {
        return Request.Builder()
            .url(url)
            .post(body)
            .headers(headers)
            .build()
    }

    // Abstract signatures matching Tachiyomi
    abstract fun popularMangaRequest(page: Int): Request
    abstract fun popularMangaParse(response: Response): MangasPage

    abstract fun latestUpdatesRequest(page: Int): Request
    abstract fun latestUpdatesParse(response: Response): MangasPage

    abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    abstract fun searchMangaParse(response: Response): MangasPage

    abstract fun mangaDetailsRequest(manga: SManga): Request
    abstract fun mangaDetailsParse(response: Response): SManga

    abstract fun chapterListRequest(manga: SManga): Request
    abstract fun chapterListParse(response: Response): List<SChapter>

    abstract fun pageListRequest(chapter: SChapter): Request
    abstract fun pageListParse(response: Response): List<eu.kanade.tachiyomi.source.model.Page>

    private fun isCloudflareChallenge(html: String): Boolean {
        val cfMarkers = listOf(
            "Just a moment...", "cf-challenge", "cloudflare",
            "Checking your browser", "_cf_chl_", "cf_clearance",
            "challenge-platform", "turnstile", "Ray ID",
            "DDoS protection by Cloudflare", "cf-browser-verification",
            "Please Wait... | Cloudflare"
        )
        return cfMarkers.any { html.contains(it, ignoreCase = true) } || html.length < 500
    }

    suspend fun executeRequest(request: Request): Response {
        try {
            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            if (response.isSuccessful) {
                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isNotEmpty() && !isCloudflareChallenge(bodyString)) {
                    val body = bodyString.toResponseBody(response.body?.contentType())
                    return response.newBuilder().body(body).build()
                }
                response.close()
            } else {
                response.close()
            }
        } catch (e: Exception) {
            // Ignored, fallback to WebView
        }

        // Fallback to WebViewFetcher
        return withContext(Dispatchers.Main) {
            val context = com.example.OtakuHubApp.instance
            val fetcher = com.example.data.sources.WebViewFetcher(context)
            val doc = fetcher.fetchHtml(request.url.toString())
            val body = doc.outerHtml().toResponseBody("text/html; charset=utf-8".toMediaTypeOrNull())
            Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }
    }

    // Source implementations using Flow and Coroutines
    override fun fetchPopularManga(page: Int): rx.Observable<MangasPage> = rx.Observable.create { subscriber ->
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = popularMangaRequest(page)
                val response = executeRequest(request)
                val parsed = popularMangaParse(response)
                subscriber.onNext(parsed)
                subscriber.onCompleted()
            } catch (e: Exception) {
                subscriber.onError(e)
            }
        }
    }

    override fun fetchLatestUpdates(page: Int): rx.Observable<MangasPage> = rx.Observable.fromCallable {
        val request = latestUpdatesRequest(page)
        val response = kotlinx.coroutines.runBlocking { executeRequest(request) }
        latestUpdatesParse(response)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): rx.Observable<MangasPage> = rx.Observable.create { subscriber ->
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = searchMangaRequest(page, query, filters)
                val response = executeRequest(request)
                val parsed = searchMangaParse(response)
                subscriber.onNext(parsed)
                subscriber.onCompleted()
            } catch (e: Exception) {
                subscriber.onError(e)
            }
        }
    }

    override fun fetchMangaDetails(manga: SManga): rx.Observable<SManga> = rx.Observable.fromCallable {
        val request = mangaDetailsRequest(manga)
        val response = kotlinx.coroutines.runBlocking { executeRequest(request) }
        mangaDetailsParse(response)
    }

    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.create { subscriber ->
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = chapterListRequest(manga)
                val response = executeRequest(request)
                val parsed = chapterListParse(response)
                subscriber.onNext(parsed)
                subscriber.onCompleted()
            } catch (e: Exception) {
                subscriber.onError(e)
            }
        }
    }

    override fun fetchPageList(chapter: SChapter): rx.Observable<List<eu.kanade.tachiyomi.source.model.Page>> = rx.Observable.create { subscriber ->
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = pageListRequest(chapter)
                val response = executeRequest(request)
                val parsed = pageListParse(response)
                subscriber.onNext(parsed)
                subscriber.onCompleted()
            } catch (e: Exception) {
                subscriber.onError(e)
            }
        }
    }

    // Bridge methods to support the parent app's standard ContentSource interface
    override fun getRecommendations(page: Int): Flow<List<ContentItem>> {
        return fetchPopularManga(1).asFlow().map { page ->
            page.mangas.map { sManga ->
                ContentItem(
                    id = sManga.url,
                    title = sManga.title,
                    source = name,
                    imageUrl = sManga.thumbnail_url ?: "",
                    type = ContentType.COMIC,
                    description = sManga.description ?: "",
                    genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                )
            }
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> {
        return fetchSearchManga(1, query, FilterList()).asFlow().map { page ->
            page.mangas.map { sManga ->
                ContentItem(
                    id = sManga.url,
                    title = sManga.title,
                    source = name,
                    imageUrl = sManga.thumbnail_url ?: "",
                    type = ContentType.COMIC,
                    description = sManga.description ?: "",
                    genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                )
            }
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val initialManga = SManga.create().apply { url = itemId }
            var detailedManga: SManga? = null
            fetchMangaDetails(initialManga).asFlow().collect { detailedManga = it }
            detailedManga?.let { sManga ->
                ContentItem(
                    id = sManga.url,
                    title = sManga.title,
                    source = name,
                    imageUrl = sManga.thumbnail_url ?: "",
                    type = ContentType.COMIC,
                    description = sManga.description ?: "",
                    genres = sManga.genre?.split(",")?.map { it.trim() } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> {
        val initialManga = SManga.create().apply { url = itemId }
        return fetchChapterList(initialManga).asFlow().map { sChapters ->
            sChapters.map { sChapter ->
                Chapter(
                    id = sChapter.url,
                    title = sChapter.name,
                    url = sChapter.url,
                    date = "" // Could format upload date if necessary
                )
            }
        }
    }

    override fun getPages(chapterId: String): Flow<List<com.example.data.models.Page>> {
        val initialChapter = SChapter.create().apply { url = chapterId }
        return fetchPageList(initialChapter).asFlow().map { sPages ->
            sPages.map { sPage ->
                com.example.data.models.Page(
                    url = sPage.imageUrl ?: sPage.url
                )
            }
        }
    }

    override fun getVideoStreamUrl(episodeId: String): Flow<String> = flow {
        emit("")
    }
}

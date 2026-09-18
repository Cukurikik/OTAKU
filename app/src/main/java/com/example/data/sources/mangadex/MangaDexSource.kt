package com.example.data.sources.mangadex

import kotlinx.coroutines.CancellationException
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ContentSource
import com.example.data.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MangaDexSource : ContentSource {
    override val sourceId: String = "mangadex"
    override val sourceName: String = "MangaDex"

    private val api: MangaDexApi = Retrofit.Builder()
        .baseUrl("https://api.mangadex.org/")
        .client(NetworkModule.okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(MangaDexApi::class.java)

    private fun mapMangaDataToContentItem(manga: MangaData): ContentItem {
        val title = manga.attributes?.title?.get("en") ?: manga.attributes?.title?.values?.firstOrNull() ?: "Unknown"
        val desc = manga.attributes?.description?.get("en") ?: ""
        
        val coverRel = manga.relationships?.find { it.type == "cover_art" }
        val fileName = coverRel?.attributes?.fileName
        val imageUrl = if (fileName != null) {
            "https://uploads.mangadex.org/covers/${manga.id}/$fileName"
        } else {
            ""
        }

        return ContentItem(
            id = manga.id,
            title = title,
            source = sourceName,
            imageUrl = imageUrl,
            type = ContentType.COMIC,
            description = desc,
            genres = listOf("Manga"), sourceId = sourceId
        )
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val offset = (page - 1) * 20
            val response = api.getMangaList(offset = offset)
            val items = response.data?.map { mapMangaDataToContentItem(it) } ?: emptyList()
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val offset = (page - 1) * 20
            val response = api.getMangaList(title = query, offset = offset)
            val items = response.data?.map { mapMangaDataToContentItem(it) } ?: emptyList()
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val response = api.getManga(itemId)
            response.data?.let { mapMangaDataToContentItem(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val response = api.getMangaFeed(itemId)
            val chapters = response.data?.filter {
                it.attributes?.pages != null && it.attributes.pages > 0 && it.attributes.externalUrl.isNullOrEmpty()
            }?.map { ch ->
                val chNum = ch.attributes?.chapter ?: "0"
                val chTitle = ch.attributes?.title
                
                val displayTitle = if (chTitle.isNullOrEmpty()) {
                    "Chapter $chNum"
                } else {
                    "Ch. $chNum - $chTitle"
                }
                
                Chapter(
                    id = ch.id,
                    title = displayTitle,
                    url = ch.id,
                    date = ch.attributes?.publishAt?.take(10) ?: ""
                )
            } ?: emptyList()
            emit(chapters)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        try {
            val response = api.getAtHomeServer(chapterId)
            val baseUrl = response.baseUrl
            val hash = response.chapter?.hash
            val data = response.chapter?.data ?: emptyList()
            if (baseUrl != null && hash != null && hash.isNotEmpty() && data.isNotEmpty()) {
                val pages = data.map { fileName ->
                    Page("$baseUrl/data/$hash/$fileName")
                }
                emit(pages)
            } else {
                emit(emptyList())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            emit(emptyList())
        }
    }

}

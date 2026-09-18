package com.example.data.sources.anime.base

import android.content.Context
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.anime.extractors.CryptoJsDecryptor
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Universal High-Precision DOM Extractor for all Anime Sources.
 * Handles:
 * - Lazy-loaded images (data-src, data-lazy-src, data-original, srcset, background-image, og:image)
 * - Detail & metadata extraction with multi-selector fallback & OpenGraph fallbacks
 * - Dynamic episode list extraction across all major themes (AnimeStream, Oploverz, Otakudesu, Donghua, Animasu, Nimegami, Kuramanime)
 * - Server & Mirror resolution (Base64 select options, player_ajax, Otakudesu mirrorstream, in-page scripts, nested iframes)
 * - Integration with VideoExtractorEngine for direct stream resolution
 */
object AnimeDomExtractor {

    // ==========================================
    // 1. Image & Cover URL Extraction
    // ==========================================

    fun extractImageUrl(el: Element?, baseUrl: String = ""): String {
        if (el == null) return ""

        val imgNode = if (el.tagName() == "img") el else el.selectFirst("img")

        // 1. Try img attributes in order of reliability
        if (imgNode != null) {
            val candidates = listOf(
                imgNode.attr("data-lazy-src"),
                imgNode.attr("data-src"),
                imgNode.attr("data-original"),
                imgNode.attr("data-srcset"),
                imgNode.attr("srcset"),
                imgNode.attr("data-setbg"),
                imgNode.attr("src")
            )

            for (candidate in candidates) {
                val clean = candidate.trim()
                if (isValidImageUrl(clean)) {
                    val resolved = parseCandidateUrl(clean, imgNode, baseUrl)
                    if (resolved.isNotBlank()) return resolved
                }
            }
        }

        // 2. Check data-setbg on descendant or element itself
        val setBgNode = if (el.hasAttr("data-setbg")) el else el.selectFirst("[data-setbg]")
        val selfSetBg = setBgNode?.attr("data-setbg")?.trim().orEmpty()
        if (isValidImageUrl(selfSetBg)) {
            val resolved = parseCandidateUrl(selfSetBg, setBgNode ?: el, baseUrl)
            if (resolved.isNotBlank()) return resolved
        }

        val styleNode = if (el.hasAttr("style") && el.attr("style").contains("url(")) el else el.selectFirst("[style*='url(']")
        val style = styleNode?.attr("style").orEmpty()
        if (style.contains("url(")) {
            val styleMatch = Regex("""url\s*\(\s*['"]?(.*?)['"]?\s*\)""").find(style)
            val styleUrl = styleMatch?.groupValues?.get(1)?.trim().orEmpty()
            if (isValidImageUrl(styleUrl)) {
                val resolved = parseCandidateUrl(styleUrl, styleNode ?: el, baseUrl)
                if (resolved.isNotBlank()) return resolved
            }
        }

        // 3. Fallback to OpenGraph / Twitter meta if el is a Document or in root
        val ogImage = el.selectFirst("meta[property='og:image'], meta[name='twitter:image']")?.attr("content")?.trim().orEmpty()
        if (isValidImageUrl(ogImage)) {
            return if (ogImage.startsWith("//")) "https:$ogImage" else ogImage
        }

        return ""
    }

    private fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.startsWith("data:image")) return false
        if (url.startsWith("data:text")) return false
        if (url.contains("blank.gif", ignoreCase = true)) return false
        if (url.contains("placeholder", ignoreCase = true) && !url.contains("cdn")) return false
        if (url.contains("1x1", ignoreCase = true)) return false
        return true
    }

    private fun parseCandidateUrl(candidate: String, el: Element, baseUrl: String): String {
        // Handle srcset format: "img1.jpg 300w, img2.jpg 600w" -> pick highest
        val url = if (candidate.contains(",") || candidate.contains(" ")) {
            val parts = candidate.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val best = parts.lastOrNull() ?: candidate
            best.substringBefore(" ").trim()
        } else {
            candidate
        }

        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> {
                if (baseUrl.isNotBlank()) {
                    val baseUri = URI(baseUrl)
                    "${baseUri.scheme}://${baseUri.host}$url"
                } else {
                    el.absUrl("src").ifEmpty { url }
                }
            }
            else -> {
                if (baseUrl.isNotBlank()) {
                    val base = baseUrl.trimEnd('/')
                    "$base/$url"
                } else {
                    el.absUrl("src").ifEmpty { url }
                }
            }
        }
    }

    // ==========================================
    // 2. Title Extraction
    // ==========================================

    fun extractTitle(el: Element?): String {
        if (el == null) return ""
        val titleNode = el.selectFirst(".tt, .title, .entry-title, h1, h2, h3, h4, h5, .album-title, .video-title, .titlelist, .product__item__text h5, [itemprop=name]")
        var text = titleNode?.text()?.trim().orEmpty()
        if (text.isBlank()) text = el.attr("title").trim()
        if (text.isBlank()) text = el.attr("alt").trim()
        if (text.isBlank()) text = el.attr("aria-label").trim()
        return text
    }

    // ==========================================
    // 3. Catalog / Recommendations Card Parsing
    // ==========================================

    fun extractCards(
        doc: Document,
        sourceId: String,
        sourceName: String,
        baseUrl: String,
        customCardSelector: String? = null
    ): List<ContentItem> {
        val defaultSelectors = listOf(
            ".listupd .bs", ".bsx", "article.bs", ".post-item", ".item",
            ".film-list .item", ".movies-list .ml-item", ".venz ul li", ".article-list li",
            ".product__item", "#animeList a", ".filter__gallery a", ".animepost",
            ".col-6", ".box-item", ".post-show ul li", ".post-article", "article.archivepost",
            "article", ".anime-item", ".film-item"
        )
        val selector = customCardSelector ?: defaultSelectors.joinToString(", ")
        val elements = doc.select(selector)

        return elements.mapNotNull { el ->
            val aNode = if (el.tagName() == "a") el else el.selectFirst("a") ?: return@mapNotNull null
            var link = aNode.absUrl("href")
            if (link.isBlank()) link = aNode.attr("href")
            if (link.isBlank() || link == "#" || link.startsWith("javascript:")) return@mapNotNull null

            if (!link.startsWith("http")) {
                link = resolveUrl(baseUrl, link)
            }

            var title = extractTitle(el)
            if (title.isBlank()) title = extractTitle(aNode)
            if (title.isBlank()) title = aNode.text().trim()
            if (title.isBlank()) return@mapNotNull null

            val imageUrl = extractImageUrl(el, baseUrl).ifBlank { extractImageUrl(aNode, baseUrl) }

            ContentItem(
                id = link,
                title = title,
                imageUrl = imageUrl,
                sourceId = sourceId,
                source = sourceName,
                type = ContentType.ANIME
            )
        }.distinctBy { it.id }
    }

    // ==========================================
    // 4. Detail & Metadata Extraction
    // ==========================================

    fun extractDetail(
        doc: Document,
        sourceId: String,
        sourceName: String,
        itemId: String,
        baseUrl: String = ""
    ): ContentItem {
        // Title
        val titleNode = doc.selectFirst("h1.entry-title, .entry-title, h1, .infox h1, .info-content h1, .post-title, .anime__details__title h3, .s-title, meta[property='og:title']")
        var title = if (titleNode?.tagName() == "meta") {
            titleNode.attr("content").trim()
        } else {
            titleNode?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) {
            title = doc.title().substringBefore(" - ").substringBefore(" | ").trim()
        }

        // Cover Image
        val imgNode = doc.selectFirst(".thumb img, .kover img, .poster img, .fotoanime img, .bigcontent img, .wp-post-image, .anime__details__pic, .set-bg, meta[property='og:image'], meta[name='twitter:image']")
        var imageUrl = extractImageUrl(imgNode, baseUrl)
        if (imageUrl.isBlank()) {
            imageUrl = extractImageUrl(doc.selectFirst("meta[property='og:image']"), baseUrl)
        }

        // Synopsis / Description
        val descNode = doc.selectFirst(".entry-content, .desc, .mindes, .sinopc, .entry-content p, .synopsis, .short-description, .infox .desc, .anime__details__text p, meta[property='og:description'], meta[name='description']")
        var desc = if (descNode?.tagName() == "meta") {
            descNode.attr("content").trim()
        } else {
            descNode?.text()?.trim().orEmpty()
        }
        desc = desc.replace(Regex("^(?:Sinopsis|Synopsis|Description)\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "")

        // Genres
        val genres = doc.select(".genxed a, .genre a, .info a[rel='tag'], .genres a, .infox a[href*='/genre/'], .anime__details__widget ul li:contains(Genre) a, .infox .spe span:contains(Genre) a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Genre", ignoreCase = true) }
            .distinct()

        return ContentItem(
            id = itemId,
            title = title,
            imageUrl = imageUrl,
            sourceId = sourceId,
            source = sourceName,
            type = ContentType.ANIME,
            description = desc,
            genres = genres
        )
    }

    // ==========================================
    // 5. Episode List Parsing
    // ==========================================

    fun extractEpisodes(
        doc: Document,
        baseUrl: String,
        customSelector: String? = null
    ): List<Chapter> {
        val episodes = mutableListOf<Chapter>()

        // 1. Kuramanime popover / data-content check
        val kuramaPopover = doc.selectFirst("#episodeLists[data-content], a#episodeLists")
        if (kuramaPopover != null) {
            val contentHtml = kuramaPopover.attr("data-content")
            if (contentHtml.isNotBlank()) {
                val popoverDoc = Jsoup.parseBodyFragment(contentHtml)
                val popoverLinks = popoverDoc.select("a[href]")
                for (a in popoverLinks) {
                    val href = resolveUrl(baseUrl, a.absUrl("href").ifEmpty { a.attr("href") })
                    val title = a.text().trim().ifEmpty { "Episode" }
                    if (href.isNotBlank()) {
                        episodes.add(Chapter(id = href, title = title, url = href))
                    }
                }
            }
        }

        // 2. Standard Episode Selectors across AnimeStream, Oploverz, Otakudesu, Animasu, etc.
        if (episodes.isEmpty()) {
            val selectors = if (!customSelector.isNullOrBlank()) {
                listOf(customSelector)
            } else {
                listOf(
                    ".eplister ul li, .eplister li",
                    ".episodelist ul li, .episodelist li",
                    ".epslist ul li, .epslist li",
                    ".episode-list ul li, .episode-list li, .list_episode li, .list_eps li",
                    "ul.servers li, .lsteps li, .list-eps li, .list-eps a",
                    "#episode_page li, ul#episode_page li",
                    ".box-list-episode a, a.ep-item, .monito li a, .smokeurl a, .smokedl a",
                    "a.btn-danger[href*='/episode/'], .ep-button, .ep-list a, #episodes a, .episode-item a, .list-episode a"
                )
            }

            for (sel in selectors) {
                val nodes = doc.select(sel)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        val a = if (node.tagName() == "a") node else node.selectFirst("a") ?: continue
                        var href = a.absUrl("href").ifEmpty { a.attr("href") }
                        if (href.isBlank() || href == "#" || href.startsWith("javascript:")) continue
                        href = resolveUrl(baseUrl, href)

                        val epNum = node.selectFirst(".epl-num, .num, .epinum, .epNum, span:first-child")?.text()?.trim().orEmpty()
                        val epTitle = node.selectFirst(".epl-title, .title, .eps, .ep-title, .left")?.text()?.trim()
                            ?: a.text().trim()

                        var title = when {
                            epNum.isNotBlank() && !epTitle.contains(epNum, ignoreCase = true) -> "$epNum - $epTitle"
                            epTitle.isNotBlank() -> epTitle
                            epNum.isNotBlank() -> "Episode $epNum"
                            else -> "Episode"
                        }

                        // Clean title
                        title = title.replace(Regex("""\s+"""), " ").trim()

                        val date = node.selectFirst(".epl-date, .dt, .date, .epdate")?.text()?.trim().orEmpty()

                        episodes.add(Chapter(id = href, title = title, url = href, date = date))
                    }
                    if (episodes.isNotEmpty()) break
                }
            }
        }

        // 3. Fallback: Search all anchor tags matching /episode/ or -episode-
        if (episodes.isEmpty()) {
            val candidateAnchors = doc.select("a[href*='/episode/'], a[href*='-episode-'], a[href*='/eps/'], a[href*='-eps-']")
            for (a in candidateAnchors) {
                var href = a.absUrl("href").ifEmpty { a.attr("href") }
                if (href.isNotBlank() && !href.startsWith("javascript:") && href != "#") {
                    href = resolveUrl(baseUrl, href)
                    val text = a.text().trim().ifEmpty { "Episode" }
                    episodes.add(Chapter(id = href, title = text, url = href))
                }
            }
        }

        // Deduplicate & determine ordering (Episode 1 first)
        val distinct = episodes.distinctBy { it.url }
        return if (isDescendingOrder(distinct)) distinct.reversed() else distinct
    }

    private fun isDescendingOrder(list: List<Chapter>): Boolean {
        if (list.size < 2) return false
        val firstNum = extractEpisodeNumber(list.first().title)
        val lastNum = extractEpisodeNumber(list.last().title)
        return firstNum != null && lastNum != null && firstNum > lastNum
    }

    private fun extractEpisodeNumber(title: String): Float? {
        val match = Regex("""(?:Episode|Ep|Eps|#)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(title)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    // ==========================================
    // 6. Video Server & Embed Extraction
    // ==========================================

    suspend fun extractAllServersAndEmbeds(
        doc: Document,
        baseUrl: String,
        episodeUrl: String,
        client: OkHttpClient = NetworkModule.okHttpClient
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val foundMirrors = mutableListOf<Pair<String, String>>()
        val seenUrls = mutableSetOf<String>()

        fun addMirror(serverName: String, url: String) {
            val cleanUrl = cleanEmbedUrl(url, baseUrl)
            if (cleanUrl.isNotBlank() && !seenUrls.contains(cleanUrl) && isValidVideoHost(cleanUrl)) {
                seenUrls.add(cleanUrl)
                val cleanName = serverName.trim().takeIf { it.isNotBlank() && !it.contains("pilih", true) } ?: "Server ${foundMirrors.size + 1}"
                foundMirrors.add(Pair(cleanName, cleanUrl))
            }
        }

        // ----------------------------------------------------
        // A. Base64 select options (<select class="mirror">)
        // ----------------------------------------------------
        val selectOptions = doc.select("select.mirror option, select#mirror option, select.select-mirror option, select#select-mirror option, .mirror select option, .server-select option, select[name='mirror'] option, select#changeServer option")
        for (opt in selectOptions) {
            val value = opt.attr("value").trim()
            val text = opt.text().trim()
            if (value.length > 10 && !value.contains(" ") && !value.startsWith("http")) {
                try {
                    val decoded = String(Base64.decode(value, Base64.DEFAULT))
                    if (decoded.contains("<iframe", ignoreCase = true)) {
                        val iframeDoc = Jsoup.parseBodyFragment(decoded)
                        val src = iframeDoc.selectFirst("iframe")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
                        if (!src.isNullOrBlank()) addMirror(text, src)
                    } else if (decoded.startsWith("http://") || decoded.startsWith("https://") || decoded.startsWith("//")) {
                        addMirror(text, decoded)
                    }
                } catch (e: Exception) {
                    // Ignore base64 decode errors
                }
            } else if (value.startsWith("http") || value.startsWith("//")) {
                addMirror(text, value)
            }
        }

        // ----------------------------------------------------
        // B. Otakudesu .mirrorstream decode (data-content)
        // ----------------------------------------------------
        val otakudesuMirrors = doc.select(".mirrorstream ul li a, .mirrorstream a")
        for (a in otakudesuMirrors) {
            val dataContent = a.attr("data-content").trim()
            val label = a.text().trim()
            val groupTitle = a.parent()?.parent()?.selectFirst("h4")?.text()?.trim() ?: "OtakuServer"
            if (dataContent.isNotBlank()) {
                try {
                    val decoded = String(Base64.decode(dataContent, Base64.DEFAULT))
                    val iframeDoc = Jsoup.parseBodyFragment(decoded)
                    val src = iframeDoc.selectFirst("iframe")?.let { it.attr("src").ifEmpty { it.attr("data-src") } }
                    if (!src.isNullOrBlank()) addMirror("$groupTitle - $label", src)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // ----------------------------------------------------
        // C. AJAX player_ajax resolution
        // ----------------------------------------------------
        var actionNonce: String? = null
        var ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"
        for (script in doc.select("script")) {
            val scriptHtml = script.html()
            if (scriptHtml.contains("player_ajax")) {
                val nonceMatch = Regex("""nonce\s*:\s*['"]([^'"]+)['"]""").find(scriptHtml)
                if (nonceMatch != null) actionNonce = nonceMatch.groupValues[1]

                val ajaxMatch = Regex("""(?:ajax_url|url)\s*:\s*['"]([^'"]*admin-ajax\.php)['"]""").find(scriptHtml)
                if (ajaxMatch != null) ajaxUrl = ajaxMatch.groupValues[1]
            }
        }

        val ajaxMirrors = doc.select(".server li, .mirror li, ul.daftarmirror li a, .megavid .server, .server-item, ul.mirror-select li, div.server a")
        for (mirror in ajaxMirrors) {
            val postId = mirror.attr("data-post").ifEmpty { mirror.attr("data-id") }
            val num = mirror.attr("data-nume").ifEmpty { mirror.attr("data-server") }
            val type = mirror.attr("data-type").ifEmpty { "embed" }

            if (postId.isNotBlank() && num.isNotBlank()) {
                try {
                    val formBuilder = FormBody.Builder()
                        .add("action", "player_ajax")
                        .add("post", postId)
                        .add("nume", num)
                        .add("type", type)
                    if (actionNonce != null) formBuilder.add("nonce", actionNonce)

                    val req = Request.Builder()
                        .url(ajaxUrl)
                        .post(formBuilder.build())
                        .header("Referer", episodeUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val iframeMatch = Regex("""src=\\?["'](.*?)\\?["']""").find(body)
                        val src = iframeMatch?.groupValues?.get(1)?.replace("\\/", "/")
                        if (!src.isNullOrBlank()) {
                            val serverName = mirror.text().trim().takeIf { it.isNotBlank() } ?: "Server ${foundMirrors.size + 1}"
                            addMirror(serverName, src)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore ajax failures
                }
            }
        }

        // ----------------------------------------------------
        // D. Data attributes on elements ([data-embed], [data-url], [data-video], [data-src])
        // ----------------------------------------------------
        val dataEmbeds = doc.select("[data-embed], [data-url], [data-video], [data-target]")
        for (el in dataEmbeds) {
            if (el.tagName() == "img" || el.tagName() == "video" || el.tagName() == "script") continue
            val candidates = listOf(
                el.attr("data-embed"),
                el.attr("data-url"),
                el.attr("data-video"),
                el.attr("data-target")
            )
            for (c in candidates) {
                val clean = c.trim()
                if (clean.startsWith("http") || clean.startsWith("//")) {
                    val name = el.text().trim().takeIf { it.isNotBlank() } ?: "Server ${foundMirrors.size + 1}"
                    addMirror(name, clean)
                }
            }
        }

        // ----------------------------------------------------
        // E. Direct & Nested <iframe> elements in page
        // ----------------------------------------------------
        val iframes = doc.select("#pembed iframe, .player-embed iframe, .video-content iframe, .responsive-embed-stream iframe, #vload iframe, .vkontakte iframe, iframe#embed_holder, iframe[src], iframe[data-src]")
        for (iframe in iframes) {
            val src = iframe.absUrl("src").ifEmpty { iframe.absUrl("data-src") }.ifEmpty { iframe.attr("src") }
            if (src.isNotBlank()) {
                addMirror("Primary Stream", src)
            }
        }

        // ----------------------------------------------------
        // F. In-page JavaScript streams and variables
        // ----------------------------------------------------
        val html = doc.html()
        // Check for direct .m3u8 / .mp4 links in script
        val m3u8Matches = Regex("""["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").findAll(html)
        for (m in m3u8Matches) {
            val streamUrl = m.groupValues[1]
            addMirror("Direct Stream", streamUrl)
        }

        // Check for unpacked or crypto AES
        if (html.contains("eval(function(p,a,c,k,e,d)")) {
            try {
                val unpacked = JsPackerUnpacker.unpack(html)
                if (unpacked != null) {
                    val unpackedIframe = Regex("""src=["'](https?://[^"']+)["']""").find(unpacked)
                    if (unpackedIframe != null) {
                        addMirror("Unpacked Server", unpackedIframe.groupValues[1])
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        foundMirrors
    }

    private fun cleanEmbedUrl(url: String, baseUrl: String): String {
        var clean = url.trim().replace("&amp;", "&")
        if (clean.startsWith("//")) clean = "https:$clean"
        if (clean.startsWith("/") && baseUrl.isNotBlank()) {
            clean = resolveUrl(baseUrl, clean)
        }
        return clean
    }

    private fun isValidVideoHost(url: String): Boolean {
        val lower = url.lowercase()
        val blockedDomains = listOf(
            "facebook.com", "google.com", "googletagmanager", "disqus.com",
            "twitter.com", "doubleclick.net", "histats.com", "recaptcha",
            "turnstile", "cloudflare.com", "wp-content/plugins", "share"
        )
        return !blockedDomains.any { lower.contains(it) }
    }

    private fun resolveUrl(baseUrl: String, relative: String): String {
        return try {
            val base = URI(baseUrl)
            base.resolve(relative).toString()
        } catch (e: Exception) {
            if (relative.startsWith("/")) {
                val base = baseUrl.trimEnd('/')
                "$base$relative"
            } else {
                "${baseUrl.trimEnd('/')}/$relative"
            }
        }
    }
}

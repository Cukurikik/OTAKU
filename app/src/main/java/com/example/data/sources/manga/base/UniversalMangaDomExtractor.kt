package com.example.data.sources.manga.base

import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Universal High-Precision DOM Extractor for all Comic/Manga Sources.
 *
 * Implements:
 * 1. Multi-Attribute Fallback Chain:
 *    - Priority 1: data-original (Custom lazy loader / high-res original)
 *    - Priority 2: data-src (Standard lazy loader)
 *    - Priority 3: data-lazy-src (WordPress Jetpack / WP-Manga)
 *    - Priority 4: data-srcset / srcset (Responsive image set)
 *    - Priority 5: src (Standard image attribute)
 * 2. <noscript> Extraction Fallback:
 *    - Parses and extracts real images wrapped inside <noscript> elements for lazy-loaders
 * 3. Blogger/Blogspot Regex Transformer:
 *    - Converts low-res / thumbnail paths (/s72-c/, /w140-h200/, /s1600/, =w140-h200, =s72-c)
 *      to /s0/ or =s0 for raw uncompressed visual quality on tablets & high-DPI screens.
 * 4. Strict Sanitization Filter:
 *    - Purges 1x1 SVGs, data:image base64 stubs, ad banners, sponsor badges, Discord/Patreon watermarks,
 *      and site widgets.
 * 5. Script Fallback Engine:
 *    - Extracts JSON payload from ts_reader, chapter_images, and inline JS arrays.
 */
object UniversalMangaDomExtractor {

    // Regex for Blogger thumbnail / low-res sizes
    private val BLOGGER_PATH_S_REGEX = Regex("""/s\d+(-[a-zA-Z0-9_-]+)?/""")
    private val BLOGGER_PATH_W_REGEX = Regex("""/w\d+-h\d+(-[a-zA-Z0-9_-]+)?/""")
    private val BLOGGER_PARAM_W_REGEX = Regex("""=w\d+-h\d+(-[a-zA-Z0-9_-]+)?""")
    private val BLOGGER_PARAM_S_REGEX = Regex("""=s\d+(-[a-zA-Z0-9_-]+)?""")

    // Filter sanitasi keywords (lowercased)
    private val AD_KEYWORDS = listOf(
        "banner", "iklan", "sponsor", "discord", "donate", "donasi",
        "trakteer", "patreon", "watermark", "badge", "promosi", "promo",
        "ads", "ad-", "ad_", "social", "facebook", "instagram", "telegram",
        "favicon", "avatar", "logo", "widget", "blank.gif", "pixel.gif", "1x1"
    )

    /**
     * Transforms Blogger/Blogspot URLs from small thumbnails (/s72-c/, /w140-h200/, /s1600/)
     * into /s0/ or =s0 (full uncompressed resolution).
     */
    fun transformBloggerUrl(url: String): String {
        if (url.isBlank()) return url
        val lower = url.lowercase()
        if (lower.contains("blogger.googleusercontent.com") ||
            lower.contains("bp.blogspot.com") ||
            lower.contains(".blogspot.com")
        ) {
            var transformed = url
            transformed = BLOGGER_PATH_S_REGEX.replace(transformed, "/s0/")
            transformed = BLOGGER_PATH_W_REGEX.replace(transformed, "/s0/")
            transformed = BLOGGER_PARAM_W_REGEX.replace(transformed, "=s0")
            transformed = BLOGGER_PARAM_S_REGEX.replace(transformed, "=s0")
            return transformed
        }
        return url
    }

    /**
     * Checks if a candidate image URL is a valid manga page or cover.
     * Sanitizes placeholders, base64 1x1 SVGs, and ad/sponsor assets.
     */
    fun isValidMangaImage(url: String): Boolean {
        if (url.isBlank()) return false
        val clean = url.trim()
        val lower = clean.lowercase()

        // 1. Data URLs & 1x1 base64 placeholders
        if (lower.startsWith("data:image") || lower.startsWith("data:text") || lower.startsWith("data:application")) {
            return false
        }

        // 2. Reject empty/pixel placeholders
        if (lower.contains("blank.gif") || lower.contains("pixel.gif") ||
            lower.contains("transparent.png") || lower.contains("1x1.") ||
            (lower.contains("placeholder.") && !lower.contains("cdn"))
        ) {
            return false
        }

        // 3. Reject Ads, banners, badges, sponsor watermarks
        for (keyword in AD_KEYWORDS) {
            val path = try { URI(clean).path?.lowercase() ?: lower } catch (_: Exception) { lower }
            if (path.contains("/$keyword") || path.contains("-$keyword") || path.contains("_$keyword") ||
                path.endsWith("/$keyword.jpg") || path.endsWith("/$keyword.png") || path.endsWith("/$keyword.webp")
            ) {
                return false
            }
            if (keyword in listOf("iklan", "trakteer", "donasi", "donate", "patreon", "join-discord") && lower.contains(keyword)) {
                return false
            }
        }

        // 4. Must be a plausible HTTP/HTTPS URL
        return clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("//") || clean.startsWith("/")
    }

    /**
     * Multi-Attribute Fallback Chain to extract clean image URL:
     * - Priority 1: data-original
     * - Priority 2: data-src
     * - Priority 3: data-lazy-src
     * - Priority 4: data-srcset / srcset
     * - Priority 5: src
     *
     * Handles <noscript> tags and applies Blogger high-res transformer.
     */
    fun extractImageUrl(element: Element?, baseUrl: String = ""): String {
        if (element == null) return ""

        val img = if (element.tagName() == "img") element else element.selectFirst("img")
        var rawUrl = ""

        if (img != null) {
            // Check <noscript> sibling or parent fallback if img has placeholder
            val noscript = img.parent()?.selectFirst("noscript")
                ?: (img.nextElementSibling()?.takeIf { it.tagName() == "noscript" } as? Element)
            val noscriptImg = noscript?.selectFirst("img")

            val candidates = mutableListOf<String>()

            // Priority 1: data-original
            candidates.add(img.attr("data-original"))
            // Priority 2: data-src
            candidates.add(img.attr("data-src"))
            // Priority 3: data-lazy-src (WordPress Jetpack / WP-Manga)
            candidates.add(img.attr("data-lazy-src"))
            // Priority 4: data-srcset / srcset
            img.attr("data-srcset").takeIf { it.isNotBlank() }?.let { srcset ->
                val highest = parseSrcset(srcset)
                if (highest.isNotBlank()) candidates.add(highest)
            }
            img.attr("srcset").takeIf { it.isNotBlank() }?.let { srcset ->
                val highest = parseSrcset(srcset)
                if (highest.isNotBlank()) candidates.add(highest)
            }
            candidates.add(img.attr("data-wp-src"))
            candidates.add(img.attr("data-url"))

            // Noscript fallback candidates
            if (noscriptImg != null) {
                candidates.add(noscriptImg.attr("data-original"))
                candidates.add(noscriptImg.attr("data-src"))
                candidates.add(noscriptImg.attr("src"))
            }

            // Priority 5: src (standard)
            candidates.add(img.attr("src"))

            for (c in candidates) {
                val clean = c.trim()
                if (isValidMangaImage(clean)) {
                    rawUrl = clean
                    break
                }
            }
        }

        // Fallback for background-image
        if (rawUrl.isBlank()) {
            val style = element.attr("style")
            if (style.contains("url(")) {
                val match = Regex("""url\s*\(\s*['"]?(.*?)['"]?\s*\)""").find(style)
                val bgUrl = match?.groupValues?.get(1)?.trim().orEmpty()
                if (isValidMangaImage(bgUrl)) {
                    rawUrl = bgUrl
                }
            }
        }

        // Fallback for meta og:image if element is document
        if (rawUrl.isBlank()) {
            val og = element.selectFirst("meta[property='og:image'], meta[name='twitter:image']")?.attr("content")?.trim().orEmpty()
            if (isValidMangaImage(og)) {
                rawUrl = og
            }
        }

        if (rawUrl.isBlank()) return ""

        val absoluteUrl = resolveAbsoluteUrl(rawUrl, img ?: element, baseUrl)
        return transformBloggerUrl(absoluteUrl)
    }

    private fun parseSrcset(srcset: String): String {
        return srcset.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { entry ->
                val parts = entry.split(Regex("\\s+"))
                parts.firstOrNull() ?: ""
            }
            .lastOrNull { isValidMangaImage(it) } ?: ""
    }

    private fun resolveAbsoluteUrl(url: String, element: Element, baseUrl: String): String {
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("http://") || url.startsWith("https://")) return url

        val abs = element.absUrl("src")
            .ifBlank { element.absUrl("data-src") }
            .ifBlank { element.absUrl("data-original") }
            .ifBlank { element.absUrl("data-lazy-src") }
        if (abs.startsWith("http://") || abs.startsWith("https://")) return abs

        if (baseUrl.isNotBlank()) {
            return try {
                val baseUri = URI(baseUrl.trimEnd('/'))
                baseUri.resolve(url).toString()
            } catch (_: Exception) {
                if (url.startsWith("/")) {
                    val root = baseUrl.substringBefore("/", "").ifBlank { baseUrl }
                    "${baseUrl.trimEnd('/')}$url"
                } else {
                    "${baseUrl.trimEnd('/')}/$url"
                }
            }
        }
        return url
    }

    /**
     * Extracts full list of chapter pages from reader Document across all comic themes
     * (MangaStream, Madara/WP-Manga, Blogger, Themesia, Custom).
     */
    fun extractChapterPages(doc: Document, baseUrl: String = ""): List<String> {
        val pages = mutableListOf<String>()

        // 1. Target standard Reader Containers across themes
        val containerSelectors = listOf(
            "#readerarea",
            ".reader-area",
            ".reading-content",
            ".entry-content",
            ".post-body",
            ".separator",
            "#Viewer-module",
            ".s1.p-20",
            ".vung-doc",
            "#vungdoc",
            ".chapter-content",
            ".read-container",
            "article"
        )

        for (selector in containerSelectors) {
            val containers = doc.select(selector)
            if (containers.isNotEmpty()) {
                for (container in containers) {
                    // Extract directly from <img> tags inside container
                    val imgElements = container.select("img")
                    for (img in imgElements) {
                        val extracted = extractImageUrl(img, baseUrl)
                        if (extracted.isNotBlank() && isValidMangaImage(extracted)) {
                            pages.add(extracted)
                        }
                    }

                    // Extract from <noscript> tags inside container (very common in WP-Manga Jetpack)
                    val noscriptElements = container.select("noscript")
                    for (ns in noscriptElements) {
                        val parsedNs = Jsoup.parseBodyFragment(ns.html())
                        for (img in parsedNs.select("img")) {
                            val extracted = extractImageUrl(img, baseUrl)
                            if (extracted.isNotBlank() && isValidMangaImage(extracted)) {
                                pages.add(extracted)
                            }
                        }
                    }
                }
            }
            if (pages.isNotEmpty()) {
                break
            }
        }

        // 2. Fallback to inline JavaScript data (ts_reader, chapter_images, sources)
        if (pages.isEmpty()) {
            val scripts = doc.select("script")
            for (script in scripts) {
                val data = script.data().ifBlank { script.html() }
                if (data.isBlank()) continue

                // Check ts_reader.run({ ... "images": [...] })
                if (data.contains("ts_reader")) {
                    val match = Regex("""["']images["']\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(data)
                    if (match != null) {
                        val imagesStr = match.groupValues[1]
                        val list = imagesStr.split(",")
                            .map { it.trim().trim('"', '\'', ' ', '\n', '\r').replace("\\/", "/") }
                            .filter { isValidMangaImage(it) }
                            .map { transformBloggerUrl(resolveAbsoluteUrl(it, script, baseUrl)) }
                        pages.addAll(list)
                        if (pages.isNotEmpty()) break
                    }
                }

                // Check chapter_images = [...] or pages = [...]
                if (data.contains("chapter_images") || data.contains("pages_list") || data.contains("sources")) {
                    val match = Regex("""(?:chapter_images|pages_list|sources)\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(data)
                    if (match != null) {
                        val imagesStr = match.groupValues[1]
                        val list = imagesStr.split(",")
                            .map { it.trim().trim('"', '\'', ' ', '\n', '\r').replace("\\/", "/") }
                            .filter { isValidMangaImage(it) }
                            .map { transformBloggerUrl(resolveAbsoluteUrl(it, script, baseUrl)) }
                        pages.addAll(list)
                        if (pages.isNotEmpty()) break
                    }
                }
            }
        }

        // 3. Fallback: scan all <img> tags in document if still empty
        if (pages.isEmpty()) {
            for (img in doc.select("img")) {
                val extracted = extractImageUrl(img, baseUrl)
                if (extracted.isNotBlank() && isValidMangaImage(extracted)) {
                    pages.add(extracted)
                }
            }
        }

        return pages.distinct()
    }

    /**
     * Standardized manga cards extraction across MangaStream, Madara, Blogger, and custom themes.
     */
    fun extractMangaCards(doc: Document, sourceId: String, sourceName: String, baseUrl: String = ""): List<ContentItem> {
        val items = mutableListOf<ContentItem>()

        // Theme card selectors
        val cardElements = doc.select(
            ".bsx, .bixbox .bs, .listupd .bs, .page-item-detail, .c-tabs-item__content, " +
            ".post-outer, .post, article, .hentry, .item, .utao, .search-wrap .tab-item, .grid-posts .post, .b-img"
        )

        for (el in cardElements) {
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a")
            val href = linkEl?.attr("href")?.trim().orEmpty()
            if (href.isBlank()) continue

            val titleEl = el.selectFirst(".tt, .title, h3, h2, .entry-title, .post-title, a[title]")
            val title = titleEl?.text()?.trim() ?: linkEl?.attr("title")?.trim() ?: linkEl?.text()?.trim() ?: "Unknown"
            if (title == "Unknown" || title.isBlank()) continue

            val img = extractImageUrl(el, baseUrl)
            val fullUrl = resolveAbsoluteUrl(href, linkEl ?: el, baseUrl)

            items.add(
                ContentItem(
                    id = fullUrl,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId
                )
            )
        }

        return items.distinctBy { it.id }
    }

    /**
     * Standardized manga detail extraction across themes.
     */
    fun extractMangaDetail(
        doc: Document,
        itemId: String,
        sourceId: String,
        sourceName: String,
        baseUrl: String = ""
    ): ContentItem {
        val title = doc.selectFirst(
            ".entry-title, h1[itemprop=name], .tt, .post-title h1, .post-title h3, h1.entry-title, h1"
        )?.text()?.trim()?.replace(Regex("HOT$"), "")?.trim() ?: "Unknown"

        val imgEl = doc.selectFirst(".summary_image img, .featured-image img, .thumb img, .infomanga img, .kover img, .bigcover img")
        val img = extractImageUrl(imgEl ?: doc, baseUrl)

        val desc = doc.selectFirst(
            ".summary__content, .description-summary p, .manga-excerpt, .entry-content, .desc, .description, .sinopsis, [itemprop=description]"
        )?.text()?.trim() ?: ""

        val genres = doc.select(".genres-content a, .genre-links a, .mgen a, .genres a, .genre-info a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        return ContentItem(
            id = itemId,
            title = title,
            source = sourceName,
            imageUrl = img,
            description = desc,
            genres = genres,
            type = ContentType.COMIC,
            sourceId = sourceId
        )
    }

    /**
     * Standardized chapter list extraction across themes.
     */
    fun extractChapters(doc: Document, baseUrl: String = ""): List<Chapter> {
        val chapterElements = doc.select(
            ".wp-manga-chapter a, .chapter-link a, li.listing-item a, #chapterlist li, .eplister li, .cl ul li, .lchx, .bxcl ul li"
        )

        return chapterElements.mapNotNull { el ->
            val a = if (el.tagName() == "a") el else el.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null

            val chapNum = el.selectFirst(".chapternum, .epnum")?.text()?.trim() ?: a.text().trim()
            val date = el.selectFirst(".chapterdate, .rightoff, .chapter-release-date i, .chapter-release-date a")?.text()?.trim() ?: ""

            val fullUrl = resolveAbsoluteUrl(href, a, baseUrl)
            Chapter(id = fullUrl, title = chapNum.ifBlank { "Chapter" }, url = fullUrl, date = date)
        }.distinctBy { it.id }
    }
}

# Scraper Selector Cookbook — Anime & Drama

## Listing card

```kotlin
val cards = doc.select("article, .post-card, .anime-card, .drama-card")
```

## Link

```kotlin
val href = card.selectFirst("a")?.absUrl("href").orEmpty()
```

## Image

```kotlin
val image = card.selectFirst("img")?.let {
    it.absUrl("data-src").ifBlank { it.absUrl("src") }
}.orEmpty()
```

## Episode

```kotlin
val episodes = doc.select(".episodes a, .episode-list a, .episodelist a")
```

## Player

```kotlin
val embeds = doc.select("iframe[src], iframe[data-src]")
    .mapNotNull { it.absUrl("src").ifBlank { it.absUrl("data-src") }.takeIf(String::isNotBlank) }
```

## HTML5 video

```kotlin
val direct = doc.select("video source[src], video[src]")
    .mapNotNull { it.absUrl("src").takeIf(String::isNotBlank) }
```

## Subtitle

```kotlin
val subtitles = doc.select("track[kind=subtitles][src], track[kind=captions][src]")
```

## Important

Selector di atas adalah **fallback patterns**, bukan klaim bahwa setiap source memakai class tersebut. Selector final harus diverifikasi dari HTML source yang sedang diuji.

# Anime Direct Scraper — Implementation Guide

## Goal

Implement every Anime source as a first-class `DirectScraperContract` source. The source is a website adapter, not an extension.

## Source lifecycle

```text
AnimeDirectSourceCatalog
        |
        v
AnimeDramaScraperEngine
        |
        +--> search
        +--> popular
        +--> detail
        +--> episodes
        +--> resolveVideo
        |
        v
Home/Detail/Player ViewModel
```

## Step 1 — Register the domain

Add a `ScraperSourceConfig` to `AnimeDirectSourceCatalog`.

The config contains:

- stable `sourceId`
- display name
- current base URL
- search URL builder
- popular URL builder
- selectors

The URL must be replaceable without changing the ViewModel.

## Step 2 — Implement search

The parser should return only lightweight cards:

```kotlin
ScrapedCard(
    id = url,
    url = url,
    title = title,
    imageUrl = image
)
```

Do not fetch the detail page for every search card.

## Step 3 — Implement detail

Resolve:

```text
title
poster
description
genres
status
year
```

Map the result to `ContentItem(type = ContentType.ANIME)` at the repository boundary.

## Step 4 — Implement episodes

Use `EpisodeNumberNormalizer`.

Important:

- never assume DOM order
- preserve original episode labels
- deduplicate by URL
- keep specials/OVAs identifiable
- do not discard episode 0 or decimal episodes

## Step 5 — Implement player discovery

Player discovery is not the same as media extraction.

```text
episode page
  -> iframe/embed discovered
  -> supported resolver
  -> VideoStreamInfo
  -> Media3/WebView
```

If only an embed is available, return the embed URL. Do not pretend it is an MP4.

## Step 6 — JS/WebView fallback

Use WebView only when normal HTTP HTML is insufficient because the page populates its DOM client-side.

The WebView path should:

- wait for DOM readiness
- enforce a timeout
- capture final URL
- return rendered HTML/DOM
- close the WebView on success/failure

## Step 7 — Search pagination

Use explicit page boundaries. A source returning an empty page is not automatically dead.

```text
page 1 -> cards
page 2 -> cards
page N -> empty -> stop
```

## Step 8 — Source health

A successful homepage request does not prove the source works.

Smoke-test:

```text
homepage
search
one detail page
one episode page
one player/embed
```

## Step 9 — Failure handling

Map failures to the engine's health states:

```text
HTTP_404
HTTP_403
HTTP_429
HTTP_5XX
DNS
TLS
TIMEOUT
EMPTY_HTML
SELECTOR_DRIFT
PLAYER_MISSING
```

## Step 10 — Fixture tests

Create one HTML fixture per parser stage:

```text
fixtures/anime/<source>/search.html
fixtures/anime/<source>/detail.html
fixtures/anime/<source>/episodes.html
fixtures/anime/<source>/player.html
```

This lets selector changes be tested without repeatedly requesting a live website.

## Source-specific maintenance

The following supplied Anime domains are represented in the catalog:

- KuraAnime variants
- Otakudesu
- Meownime
- Samehadaku
- Anoboy
- Kusonime
- AniChin variants
- Kazefuri variants

The catalog's generic selectors are a starting point, not a claim that every domain currently exposes identical HTML. Each source must be validated with fixtures and a live smoke test before being marked healthy.

## Recommended adapter layout

```text
sources/anime/<source>/
  <Source>Source.kt
  <Source>Selectors.kt
  <Source>Fixtures.kt (test only)
```

The source class should contain only source-specific behavior; common networking, health, episode normalization and player orchestration stay in shared engine code.

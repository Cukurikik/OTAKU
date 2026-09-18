# Drama Direct Scraper — Implementation Guide

Drama is **direct-scraper only** in CinemaNaga. There is no extension fallback path.

## Domain model

Drama is not just "Anime with a different label". A drama source can contain:

- TV series
- Korean/Chinese/Japanese dramas
- Western series
- movies
- specials
- multiple seasons
- multiple episode mirrors
- subtitle variants

Therefore the Drama scraper needs a season/episode-aware model even if the current database exposes episodes through `Chapter`.

## Recommended pipeline

```text
Source
 |
 +-- catalog/search
 |
 +-- title detail
 |      +-- metadata
 |      +-- seasons
 |
 +-- season selection
 |
 +-- episode list
 |      +-- number
 |      +-- title
 |      +-- release date
 |
 +-- episode page
 |      +-- player/embed
 |      +-- subtitle tracks
 |      +-- mirrors
 |
 +-- resolver
        +-- HLS
        +-- MP4
        +-- supported embed
        +-- WebView fallback
```

## Search

Search should return `ScrapedCard` only. Avoid opening every result detail page.

If the website does not provide a reliable search endpoint, use its browse/index pages rather than guessing arbitrary URLs.

## Detail

Minimum fields:

```text
title
poster
description
genres
status
year
```

Preferred additional fields:

```text
country
language
season
alternative titles
cast
```

Only fields supported by the current database should be persisted directly; the rest can remain source metadata until the schema is expanded.

## Episode model

Drama episode labels can be:

```text
Episode 1
E01
Ep. 1
S01E01
Episode 1 - Part 1
Special
OVA
Finale
```

Normalize the numeric part but retain the original label.

For multi-season pages, never merge Season 1 Episode 1 with Season 2 Episode 1. The stable episode key should include the season context.

## Mirrors

A page may expose:

```text
Server 1
Server 2
Server 3
Download
Watch now
```

Represent these as candidate playback endpoints. The UI can select a mirror without changing the underlying episode identity.

## Subtitles

When subtitle tracks are explicitly exposed by the player/page, normalize:

```text
language
label
url
format
isDefault
```

Prefer WebVTT when directly supported by Media3; retain SRT as a supported conversion/input format if the app's subtitle pipeline supports it.

Do not manufacture subtitle URLs.

## Viki

Treat Viki as a special source configuration rather than assuming it follows the same HTML structure as Indonesian WordPress-style drama sites. The adapter should isolate Viki-specific metadata/API/JS behavior from generic selectors.

## Domain failures

For a domain supplied as `Not Found`:

```text
404
 |
 +-- retry once with normalized URL
 |
 +-- if still 404 -> mark endpoint degraded
 |
 +-- check configured replacement/override
 |
 +-- never rewrite sourceId
```

For legacy raw IPs:

```text
http://178.128.91.191/
http://134.209.105.11/
```

keep them as legacy records only. Do not assume an IP endpoint is the current domain. Require a verified replacement before enabling it.

## WebView fallback

Use WebView when:

- page requires client-side rendering
- player is inserted after load
- normal HTTP response lacks the required DOM

Do not use WebView to bypass authentication, paywalls, CAPTCHA, or access restrictions.

## Player resolver contract

```text
resolveVideo(episodeUrl)
  -> direct HLS/MP4
  -> supported embed
  -> null
```

`null` must become a user-visible "player unavailable" state rather than an infinite retry loop.

## Health matrix

A Drama source is `HEALTHY` only when:

```text
homepage = OK
search/detail = OK
episode extraction = OK
player discovery = OK
```

A source can be `PARTIAL` if only the player is broken.

## Source catalogue

The supplied Drama catalogue is represented for:

- Viki
- Licil
- Licil Barat
- JuCelebrity
- LokalFilm

The two supplied IP addresses are classified as legacy endpoints. They are not treated as verified live sources.

## Recommended adapter layout

```text
sources/drama/<source>/
  <Source>Source.kt
  <Source>Selectors.kt
  <Source>Player.kt
  <Source>Fixtures.kt (test only)
```

This keeps player changes independent from catalog/detail changes.

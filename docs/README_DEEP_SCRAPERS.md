# Deep Scraper Patch — v3

This patch clarifies the architecture that was previously too broad.

## Correct model

**Comic** supports two independent mechanisms:

1. direct scraper
2. Mihon/Tachiyomi-compatible extension

**Anime** supports:

1. direct scraper only

**Drama** supports:

1. direct scraper only

## New code

```text
sources/direct/
  DirectScraperContract.kt
  DirectSourcePolicy.kt
  AnimeDramaScraperEngine.kt
  ScraperSourceConfig.kt
  EpisodeNumberNormalizer.kt
  SourceHealthState.kt

sources/anime/
  AnimeDirectSourceCatalog.kt

sources/drama/
  DramaDirectSourceCatalog.kt
```

## Important limitation

The supplied source domains are catalogued, but a domain list is not equivalent to a verified parser. Websites can change HTML, routes, JavaScript behavior and player providers. The patch therefore separates:

- reusable engine
- source-specific selectors
- source-specific URL builders
- health checks
- fixtures

That makes a broken source fixable without touching the rest of the app.

## Web research basis

The architecture follows the observed separation in Mihon/Keiyoushi-style extension repositories and Aniyomi's anime extension ecosystem, while deliberately keeping CinemaNaga Anime/Drama as direct scrapers. citeturn0search0turn0search1turn0search2

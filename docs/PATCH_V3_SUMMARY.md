# CinemaNaga Deep Anime/Drama Scraper Patch v3

## Main correction

The previous patch did not make the Comic/Anime/Drama implementation boundary strict enough.

This patch changes the architecture to:

```text
COMIC  = Direct scraper OR Mihon/Tachiyomi extension
ANIME  = Direct scraper only
DRAMA  = Direct scraper only
```

## Code changes

### New direct scraper engine

- `DirectScraperContract.kt`
- `AnimeDramaScraperEngine.kt`
- `ScraperSourceConfig.kt`
- `EpisodeNumberNormalizer.kt`
- `SourceHealthState.kt`
- `DirectSourcePolicy.kt`

### Anime

- `AnimeDirectSourceCatalog.kt`
- direct scraper configs for all supplied Anime domains
- search/detail/episode/player pipeline

### Drama

- `DramaDirectSourceCatalog.kt`
- direct scraper configs for supplied Drama domains
- season/mirror/subtitle design documented
- legacy IP endpoints treated as legacy, not verified live sources

### Extension boundary

`ExtensionManager` was changed so extension repositories are restricted to `MANGA`.

`getAnimeExtensions()` now returns an empty result because Anime is direct-scraper-only.

`loadExtensionSources()` no longer loads `AnimeSourceFactory` / `AnimeSource` extension instances.

The existing comic extension adapter remains available for Comic mode.

## Documentation added

- `docs/ANIME_DRAMA_ARCHITECTURE_DEEP.md`
- `docs/SOURCE_IMPLEMENTATION_POLICY.md`
- `docs/ANIME_EXTENSION_REFERENCE.md`
- `docs/ANIME_DRAMA_DEEP_CHECKLIST.md`
- `docs/DOMAIN_FAILURE_PLAYBOOK.md`
- `docs/scrapers/anime/ANIME_SCRAPER_IMPLEMENTATION_GUIDE.md`
- `docs/scrapers/drama/DRAMA_SCRAPER_IMPLEMENTATION_GUIDE.md`
- `docs/README_DEEP_SCRAPERS.md`

## Important build note

The supplied project archive contains an invalid/corrupt `gradle/wrapper/gradle-wrapper.jar`. Therefore this environment cannot execute Gradle compilation against the archive until the wrapper JAR is replaced/regenerated.

The new code was added as source files, but a successful Gradle build should be performed after repairing the wrapper.

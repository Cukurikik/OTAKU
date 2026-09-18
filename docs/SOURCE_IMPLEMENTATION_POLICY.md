# Source Implementation Policy

## Mandatory rule

```kotlin
Comic  -> Direct scraper OR Mihon/Tachiyomi-style extension
Anime  -> Direct scraper ONLY
Drama  -> Direct scraper ONLY
```

This rule must be enforced at the factory boundary, not only in the UI.

### Why

A UI toggle is not a security/architecture boundary. If the repository can still instantiate an extension adapter for Anime/Drama, a later refactor can accidentally violate the intended design.

## Factory behavior

```text
request source
    |
    v
ContentType / SourceMode
    |
    +-- COMIC -> extension adapter allowed
    |           direct scraper allowed
    |
    +-- ANIME -> direct scraper only
    |
    +-- DRAMA -> direct scraper only
```

## Extension repositories

Repository JSON files such as Keiyoushi's `index.min.json` describe extension packages and source metadata; they are not a universal Anime/Drama scraping API. The current Keiyoushi repository also demonstrates that extension maintenance and compatibility are ongoing concerns. citeturn0search7turn0search6

## Failure behavior

If an Anime/Drama source accidentally resolves to an extension implementation:

```text
throw UnsupportedOperationException
```

or return a typed configuration error that the UI can display.

Do not silently fall back to a comic extension.

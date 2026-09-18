# Deep Architecture — Comic vs Anime vs Drama

## 1. Non-negotiable source model

CinemaNaga uses three content modes, but they do **not** share the same source mechanism:

| Mode | Direct scraper | Mihon/Tachiyomi extension | Reason |
|---|---:|---:|---|
| Comic | YES | YES | Existing comic ecosystem already exposes extension packages/catalogues and direct HTML/API sources. |
| Anime | YES | NO | Anime source lifecycle is handled by CinemaNaga's own scraper + video pipeline. |
| Drama | YES | NO | Drama source lifecycle is handled by CinemaNaga's own scraper + video pipeline. |

This is an architectural rule, not merely a UI preference. The extension adapter must therefore be rejected whenever the active mode is Anime or Drama.

The Mihon/Keiyoushi ecosystem uses installable APK extensions and a repository catalogue. Aniyomi similarly distributes anime-capable extensions as APKs. CinemaNaga should consume those only on the Comic/extension branch; it should not silently turn Anime/Drama into extension sources. citeturn0search0turn0search1turn0search7

## 2. Layered pipeline

```text
UI Mode Selector
      |
      v
ModeRouter
      |
      +---------------- COMIC ----------------+
      |                                       |
      |                       DirectComicSource / ExtensionAdapter
      |
      +---------------- ANIME ----------------+
      |                                       |
      |                         DirectScraperEngine
      |                              |
      |               HTML -> detail -> episodes -> player
      |                              |
      |                         VideoResolver
      |
      +---------------- DRAMA ----------------+
                                              |
                                  DirectScraperEngine
                                              |
                              HTML -> detail -> episodes -> player
                                              |
                                        VideoResolver
```

## 3. Anime/Drama scraper stages

Every source follows the same deterministic stages:

1. **Endpoint selection** — use configured base URL or user override.
2. **Reachability check** — DNS/TLS/HTTP/redirect classification.
3. **Request** — normal HTTP/WebView request; respect source requirements.
4. **HTML normalization** — resolve relative URLs and image attributes.
5. **Card extraction** — home/popular/search pages.
6. **Detail extraction** — title, poster, description, genres, status, year.
7. **Episode extraction** — URL, label, normalized episode number.
8. **Episode ordering** — numeric ordering, specials separated where necessary.
9. **Player discovery** — iframe/video/source/data-src attributes.
10. **Video resolution** — direct media URL or supported embed fallback.
11. **Subtitle discovery** — WebVTT/SRT links when exposed by the page/player.
12. **Playback** — Media3 for supported direct media; WebView for an embed that cannot be resolved safely.
13. **Telemetry** — source health and parser failure classification.
14. **Cache** — cache metadata, not uncontrolled media responses.

## 4. Why a generic scraper is insufficient

Different anime/drama sites frequently change one of four things independently:

- URL structure
- CSS class names
- JavaScript-generated episode lists
- Player/embed provider

Therefore the engine is shared, but **selectors and URL builders belong to each source configuration**. A single universal selector should only be a fallback.

## 5. Required source contract

Each direct Anime/Drama source must expose:

```text
search(query, page)
recommendations(page)
detail(itemUrl)
episodes(itemUrl)
resolveVideo(episodeUrl)
```

No method may call the extension manager.

## 6. Search strategy

Use this order:

```text
native search URL
      |
      +-- successful result -> parse cards
      |
      +-- empty result -> retry page 1 only if pagination was requested
      |
      +-- unsupported search -> browse/popular fallback
```

Never convert an empty search into "all content" without explicitly marking it as a fallback.

## 7. Detail strategy

The detail parser should prefer semantic metadata:

1. `og:title`
2. page `h1`
3. source-specific title selector

Poster:

1. `og:image`
2. `data-src`
3. `src`

Description:

1. JSON-LD description when present
2. source selector
3. empty string

## 8. Episode strategy

Episode parsing must never depend on DOM order alone.

Normalize:

```text
Episode 1      -> 1
EP 01          -> 1
E01            -> 1
Episode 1.5    -> 1.5
Special 1      -> special bucket
OVA            -> special bucket
```

Then sort numerically and keep the original title/URL.

## 9. Player strategy

The player layer is deliberately separate from page parsing:

```text
Episode HTML
  -> iframe src
  -> iframe data-src
  -> video source
  -> source-specific player metadata
  -> supported resolver
  -> direct Media3 URL OR WebView embed
```

Do not assume that an iframe URL is itself an MP4/M3U8 URL.

## 10. JS-rendered pages

When the initial HTTP document contains no cards/episodes/player but the page is reachable:

```text
HTTP fetch
  |
  +-- parser finds data -> done
  |
  +-- parser empty -> WebView rendering
                    |
                    +-- DOM populated -> parse rendered DOM
                    +-- still empty -> SELECTOR_DRIFT / JS_DEPENDENCY
```

WebView is a rendering fallback, not a substitute for every HTTP request.

## 11. Source health is multidimensional

Do not use one boolean `isAlive`.

```text
reachable
parserHealthy
searchHealthy
detailHealthy
episodeHealthy
playerHealthy
```

A source can be reachable while its parser is completely broken. This distinction is essential for automatic maintenance.

## 12. Domain failure matrix

| Failure | Meaning | Action |
|---|---|---|
| DNS | hostname no longer resolves | mark domain degraded; look for configured replacement |
| TLS | certificate/handshake failure | retry only with correct HTTPS; never silently disable TLS verification |
| 404 | path/domain missing | classify moved/dead depending on repeated checks |
| 403 | access denied | do not brute-force; use WebView if normal browser access is expected |
| 429 | rate limited | exponential backoff + cache + request budget |
| 5xx | server failure | retry bounded times, then degrade |
| timeout | slow/unavailable | bounded retry + alternate configured domain |
| empty HTML | page loaded but no expected data | selector drift or JS rendering |
| player missing | detail works, playback does not | isolate player resolver failure |

## 13. Domain migration

A source should have:

```text
sourceId = stable identity
baseUrl = current default
aliases = historical URLs
override = user-selected URL
```

The stable `sourceId` must not change merely because the website moves from `v8.example` to `v9.example`.

## 14. Caching

Cache:

- source metadata
- detail metadata
- episode lists
- last-known source health

Do not treat cached HTML as permanently valid. Attach a timestamp and invalidate on source/parser failures.

## 15. Request budget

Recommended per-source budget:

```text
search:       1 request
page parse:   1 request
batch detail: bounded concurrency
player:       1 page + bounded resolver requests
```

Avoid N+1 requests by not opening every detail page for a search result unless the UI actually needs the detail.

## 16. Testing pyramid

### Unit
- URL builder
- episode number parser
- selector parser
- source mode policy
- failure classification

### Fixture
Store representative HTML fixtures for each source. Test parser behavior without touching the live site.

### Integration
Run a small smoke test against a source's homepage/search/detail/episode endpoint.

### Playback smoke
Confirm that the player layer receives at least one supported stream or a valid embed fallback.

## 17. Maintenance rule

When a source breaks, fix in this order:

```text
1. endpoint
2. HTTP/WebView request
3. selectors
4. URL normalization
5. episode parser
6. player resolver
7. source-specific edge cases
```

Do not rewrite the whole engine for a selector change.

## 18. Relationship to the Comic extension system

Comic extension support remains isolated under `sources/tachiyomi/`.

Anime/Drama direct sources must not import:

```text
ExtensionManager
TachiyomiExtensionAdapter
Mihon APK loader
extension repository parser
```

This prevents accidental cross-mode coupling.

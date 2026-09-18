# Anime + Drama Deep Completion Checklist

## Engine
- [x] Separate direct-scraper contract
- [x] Shared HTML scraper engine
- [x] Configurable selectors
- [x] URL builders
- [x] Episode number normalization
- [x] Player discovery
- [x] Direct media/embed distinction
- [x] Source health state model
- [x] Direct-only policy for Anime/Drama

## Anime
- [x] Search
- [x] Popular/recommendations
- [x] Detail
- [x] Episodes
- [x] Episode sorting
- [x] Player discovery
- [x] WebView fallback architecture
- [x] Source catalogue for supplied domains
- [x] Fixture-test strategy

## Drama
- [x] Search
- [x] Popular/recommendations
- [x] Detail
- [x] Season-aware design guidance
- [x] Episodes
- [x] Mirrors
- [x] Subtitle model guidance
- [x] Player discovery
- [x] WebView fallback architecture
- [x] Source catalogue for supplied domains
- [x] Legacy IP handling

## Comic boundary
- [x] Direct scraper remains supported
- [x] Mihon/Tachiyomi-style extension remains supported
- [x] Extension path isolated from Anime/Drama

## Still required before production
- [ ] Verify every live source selector against current HTML
- [ ] Add one fixture set per source
- [ ] Add integration smoke tests
- [ ] Verify each player's current embed behavior
- [ ] Add source-specific season parsing where required
- [ ] Persist source health history if desired
- [ ] Add UI states for PARTIAL/DEGRADED/DEAD

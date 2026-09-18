# Scraper and Core Fixes
1. **Extended `WebViewFetcher` Timeouts**: Increased WebView render timeouts up to 30s to allow Cloudflare Turnstile and JS challenges to fully resolve natively.
2. **Robust JS Image Extraction**: Implemented Javascript payload parsing for Manga themes (`ts_reader`, `chapter_preloaded_images`, Next.js state) in `ComicScraperEngine` when standard DOM scraping fails.
3. **Flexible CSS Selectors for Comic and Anime**: Broadened `Selectors` in both `ComicDirectSourceCatalog` and `AnimeDirectSourceCatalog` to handle multiple variations of MangaStream, Madara, Blogger, and generic Tube themes seamlessly. Added background-image fallback for card thumbnails.
4. **Action-Based Video Resolution**: Added logic to extract `data-url` / `data-video` from episode action buttons in `AnimeDramaScraperEngine` for anime sites that defer iframe loading.

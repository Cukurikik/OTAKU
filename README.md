# CineManga — All-in-One Hub

This patch adds a three-mode browsing surface:

- Anime
- Komik
- Drama

It also adds a centralized source endpoint registry, runtime health probe, reusable HTML selector engine, and documentation for volatile domains.

## Build note

The original project archive contains a corrupt `gradle/wrapper/gradle-wrapper.jar`. Regenerate/replace the Gradle wrapper before building.

See `docs/` for implementation and migration details.

## Anime & Drama scraper implementation

See `docs/ANIME_SCRAPER_GUIDE.md` and `docs/DRAMA_SCRAPER_GUIDE.md`. Reusable source templates are under `app/src/main/java/com/example/data/sources/templates/`.

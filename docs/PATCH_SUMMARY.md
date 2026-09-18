# Patch Summary — Anime / Komik / Drama + Source Engine

## Implemented

- Added `HubMode` for Anime / Komik / Drama.
- Added persistent mode selection via `SettingsManager.hubMode`.
- Added Home UI mode chips.
- Home source list is filtered by selected mode.
- Content results are filtered by `ContentType` as a second safety layer.
- Added `SourceEndpointRegistry` for the supplied comic/anime/drama endpoints.
- Added `SourceHealthProbe` with HEAD → GET fallback.
- Added `HtmlSelectorEngine` reusable parser helper for new adapters.
- Added health/status documentation and domain migration strategy.
- Added unit tests for registry and mode mapping.

## Existing architecture reused

The project already contained `ContentSource`, `ConfigurableSource`, `SourceBaseUrlStore`, `SourceStatusBus`, `ContentRepository`, Tachiyomi-style source models, and multiple base source implementations. The patch builds on those instead of replacing them.

## Important limitation of this build environment

The supplied project contains an invalid/corrupt `gradle/wrapper/gradle-wrapper.jar`. Therefore a Gradle compile could not be completed in this environment. The source changes were made against the existing Kotlin/Compose architecture, but you should replace the wrapper JAR or regenerate the wrapper before treating the APK build as verified.

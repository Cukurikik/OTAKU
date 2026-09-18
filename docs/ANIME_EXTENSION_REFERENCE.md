# Anime Extension Repositories — Reference Only

The following repositories supplied during the project analysis may be useful for studying extension source structure, selectors and maintenance patterns, but **they are not installed or loaded by CinemaNaga's Anime mode**:

- Yūzōnō Anime repository
- Secozzi Aniyomi extensions
- Aniyomi FR extensions
- CursedYomi extensions

CinemaNaga Anime mode uses its own `DirectScraperContract` and `AnimeDramaScraperEngine`.

This distinction is intentional. Aniyomi's ecosystem is extension/APK based, while CinemaNaga's requested Anime architecture is direct scraping. citeturn0search0turn0search1turn0search2

## What can be learned from those repositories

Use them as engineering references for:

- source class organization
- source metadata
- pagination patterns
- HTML/API parsing patterns
- video source models
- maintenance workflow

Do not copy an extension's package loading mechanism into CinemaNaga's Anime/Drama pipeline.

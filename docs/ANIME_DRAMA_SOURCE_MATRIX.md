# Anime & Drama Source Matrix

## Anime

| Source | URL | Adapter strategy |
|---|---|---|
| KuraAnime | https://v19.kuramanime.ing/ | HTML + episode + embed |
| KuraAnime Blog | https://v9.kuramanime.blog/ | HTML + episode + embed |
| KuraAnime Work | https://v9.kuramanime.work/ | HTML + episode + embed |
| Otakudesu | https://otakudesu.blog/ | HTML + episode + mirror/embed |
| Meownime | https://meownime.ltd/ | HTML + episode + player |
| Samehadaku | https://v2.samehadaku.how/ | HTML + episode + mirror/embed |
| Anoboy | https://anoboy.be/ | HTML + episode + player |
| Kusonime | https://kusonime.com/ | HTML + episode + player |
| AniChin Cafe | https://anichin.cafe/ | HTML + episode + embed |
| AniChin Watch | https://anichin.watch/ | HTML + episode + embed |
| AniChin | https://anichin.com.co/ | HTML + episode + embed |
| Kazefuri Cloud | https://sv4.kazefuri.cloud/ | HTML + episode + player |
| Kazefuri ID | https://kazefuri.id/ | HTML + episode + player |

## Drama

| Source | URL | Adapter strategy |
|---|---|---|
| Viki | https://www.viki.com/ | rendered app/data + official player flow |
| Licil | https://licil.org/ | HTML + episode + embed |
| Licil Barat | https://licil.org/barat/ | HTML + episode + embed |
| JuCelebrity | https://jucelebrity.com/ | HTML + episode + embed |
| LokalFilm | https://lokalfilm.id/ | HTML/app-specific; inspect current structure |
| Legacy IP | http://178.128.91.191/ | health-check + override only |
| Legacy IP | http://134.209.105.11/ | health-check + override only |

## Repository catalogues

The following are not direct Anime/Drama HTML sources. They are extension repositories/catalogues and should be processed by `ExtensionManager`:

- Yūzōnō anime repo: `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`
- Secozzi Aniyomi repo: `https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json`
- Keiyoushi: `https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json`

Pipeline:

`catalogue → extension metadata → installed source → scraper/API → normalized model`

            var episodes = doc.select(selectors.episode).mapNotNull { element ->
                val anchor = if (element.tagName() == "a") element else element.selectFirst("a") ?: return@mapNotNull null
                val url = anchor.absUrl("href").ifBlank { return@mapNotNull null }
                val title = if (selectors.episodeTitle.isBlank()) anchor.text() else
                    element.selectFirst(selectors.episodeTitle)?.text().orEmpty().ifBlank { anchor.text() }
                val date = if (selectors.episodeDate.isBlank()) "" else
                    element.selectFirst(selectors.episodeDate)?.text().orEmpty()
                Chapter(url, title.ifBlank { "Episode" }, url, date)
            }.distinctBy { it.url }

            if (episodes.isEmpty()) {
                val dataContentElements = doc.select("[data-content*='href=']")
                for (el in dataContentElements) {
                    val htmlContent = el.attr("data-content")
                    val dummyDoc = org.jsoup.Jsoup.parseBodyFragment(htmlContent, baseUrl)
                    val anchors = dummyDoc.select("a[href]")
                    if (anchors.isNotEmpty()) {
                        episodes = anchors.map { a ->
                            Chapter(a.absUrl("href"), a.text().ifBlank { "Episode" }, a.absUrl("href"), "")
                        }.distinctBy { it.url }.reversed() // usually data-content has Ep 1 first
                        break
                    }
                }
                
                if (episodes.isEmpty()) {
                    // Try to find generic chapter list if still empty
                    val genericAnchors = doc.select("a[href*='episode'], a[href*='ep-']").filter { 
                        it.absUrl("href").contains("episode") && !it.absUrl("href").contains("check-episode")
                    }
                    if (genericAnchors.isNotEmpty()) {
                        episodes = genericAnchors.map { a ->
                            Chapter(a.absUrl("href"), a.text().ifBlank { "Episode" }, a.absUrl("href"), "")
                        }.distinctBy { it.url }
                    }
                }
            }

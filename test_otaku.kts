import org.jsoup.Jsoup
val html = Jsoup.connect("https://otakudesu.blog/").userAgent("Mozilla/5.0").get()
val cards = html.select(".venz ul li, .epztipe ul li")
println("Cards: ${cards.size}")
if (cards.size > 0) {
    val card = cards.first()!!
    println("Link: " + card.selectFirst("a")?.attr("href"))
    println("Title: " + card.selectFirst(".jdlse, h2, .judul")?.text())
    println("Image: " + card.select("img").first()?.attr("src"))
}

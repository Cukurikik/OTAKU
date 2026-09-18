import org.jsoup.Jsoup
val doc = Jsoup.connect("https://otakudesu.blog/anime/one-piece-sub-indo/").userAgent("Mozilla/5.0").get()
val eps = doc.select(".episodelist ul li")
println("Episodes found: ${eps.size}")
if (eps.isNotEmpty()) {
    println(eps.first()?.text())
}

import com.example.data.sources.kuraanime.KuraAnimeSource
import org.jsoup.Jsoup
import java.net.URLEncoder

fun main() {
    val html = """
                    <div id="animeList" class="row">                                                    
                        <div class="col-lg-4 col-md-6 col-sm-6">            
                            <div class="product__item">                
                                <a href="https://v19.kuramanime.ing/anime/1533/road-of-naruto" target="_blank">                    
                                    <div class="product__item__pic set-bg" data-setbg="https://r2.nyomo.my.id/images/20260310-1773129051-033b2e4f-5562-4e95-b301-371f2ce2849c.jpg">                        
                                    </div>
                                </a>
                                <h5><a href="https://v19.kuramanime.ing/anime/1533/road-of-naruto" target="_blank">Road of Naruto</a></h5>                
                            </div>            
                        </div>
                    </div>
    """
    val doc = Jsoup.parse(html)
    doc.setBaseUri("https://v19.kuramanime.ing")
    
    val cards = doc.select(".product__item")
    println("Cards: " + cards.size)
    
    for (card in cards) {
        val anchor = card.selectFirst("a")
        val url = anchor?.absUrl("href").orEmpty()
        val title = card.selectFirst("h5, .sidebar-title-h5")?.text()?.trim().orEmpty()
        println("URL: " + url + " | Title: " + title)
    }
}

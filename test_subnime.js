const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let items = [];
    $('.listupd .bs, .bsx, article, .post-item, .item, .animepost').each((i, el) => {
        let title = $(el).find('.tt, .title, .entry-title, h2, h3, h4, h5').text().trim();
        let link = $(el).find('a').attr('href');
        if (title && link) {
            items.push({title, link});
        }
    });
    console.log("List:");
    console.log(items.slice(0, 3));
    
    if(items.length > 0) {
        await page.goto(items[0].link, {waitUntil: 'networkidle2'});
        const dHtml = await page.content();
        const d$ = cheerio.load(dHtml);
        let episodes = [];
        d$('.episodelist li, .eplister li, .episode-list li, .epslist li, .lsteps li').each((i, el) => {
            let chref = d$(el).find('a').attr('href');
            let ctitle = d$(el).find('.epl-title, .ep-title, .episodi, .num, .server-name, .eps, a').first().text().trim() || d$(el).text().trim();
            if (chref) episodes.push({chref, ctitle});
        });
        console.log("Episodes:");
        console.log(episodes.slice(0, 3));
        
        if (episodes.length > 0) {
            await page.goto(episodes[0].chref, {waitUntil: 'networkidle2'});
            const cHtml = await page.content();
            const c$ = cheerio.load(cHtml);
            let iframes = [];
            c$("iframe[src], iframe[data-src], .player-video iframe, #pembed iframe, .video-content iframe, .player-area iframe").each((i, el) => {
                let src = c$(el).attr('src') || c$(el).attr('data-src');
                iframes.push(src);
            });
            console.log("Iframes:");
            console.log(iframes);
        }
    }
    
    await browser.close();
})();

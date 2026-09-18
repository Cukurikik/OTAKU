const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asuracomic.net/series?page=1&order=update', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let items = [];
    $("a[href^='/series/']").each((i, el) => {
        let href = $(el).attr('href');
        let title = $(el).find('span.block, .text-sm, h3').first().text().trim() || $(el).text().trim();
        let img = $(el).find('img').attr('src');
        if (title && href && href.split('/').length === 3) {
            items.push({href, title, img});
        }
    });
    console.log("List:");
    console.log(items.slice(0, 3));
    
    // Test detail
    if(items.length > 0) {
        let detailUrl = 'https://asuracomic.net' + items[0].href;
        console.log("Navigating to: " + detailUrl);
        await page.goto(detailUrl, {waitUntil: 'networkidle2'});
        const detailHtml = await page.content();
        const d$ = cheerio.load(detailHtml);
        let title = d$('h1, h2, .text-xl, span.text-center').first().text().trim();
        console.log("Detail title: ", title);
        
        let chapters = [];
        d$("a[href^='/series/'][href*='/chapter']").each((i, el) => {
            let chref = d$(el).attr('href');
            let ctitle = d$(el).find('h3.text-sm').first().text().trim() || d$(el).text().trim();
            chapters.push({chref, ctitle});
        });
        console.log("Chapters:");
        console.log(chapters.slice(0, 3));
        
        if (chapters.length > 0) {
            let chapUrl = 'https://asuracomic.net' + chapters[0].chref;
            console.log("Navigating to chap: " + chapUrl);
            await page.goto(chapUrl, {waitUntil: 'networkidle2'});
            const cHtml = await page.content();
            const c$ = cheerio.load(cHtml);
            let imgs = [];
            c$("img[alt*='chapter'], img[src*='wp-content'], img.object-cover").each((i, el) => {
                let src = c$(el).attr('src');
                if (src && !src.startsWith('data:')) {
                    imgs.push(src);
                }
            });
            console.log("Images:");
            console.log(imgs.slice(0, 3));
        }
    }
    
    await browser.close();
})();

const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    
    let html = await page.content();
    let cheerio = require('cheerio');
    let $ = cheerio.load(html);
    
    $('script').each((i, el) => {
        let js = $(el).html();
        if (js && js.includes('FIXED & SIMPLE JAVASCRIPT FOR VIDEO PLAYER')) {
            console.log(js.substring(0, 1500));
        }
        if (js && js.includes('videoUrl')) {
             console.log("videoUrl script:", js.substring(0, 500));
        }
    });
    
    await browser.close();
})();

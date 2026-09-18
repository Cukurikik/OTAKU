const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asurascans.com/series?page=1&order=update', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    $("a").each((i, el) => {
        let href = $(el).attr('href');
        if (href && href.includes('shadow-slave')) console.log(href);
        if (href && href.includes('chapter')) console.log(href);
    });
    console.log("Done");
    await browser.close();
})();

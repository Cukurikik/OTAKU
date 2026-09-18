const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asurascans.com/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let anchors = [];
    $("a").each((i, el) => {
        anchors.push({href: $(el).attr('href'), text: $(el).text().trim()});
    });
    console.log("Anchors in home:");
    console.log(anchors.slice(0, 10));
    
    await browser.close();
})();

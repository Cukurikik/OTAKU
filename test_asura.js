const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asuracomic.net/', {waitUntil: 'networkidle2'}); // AsuraScans new domain
    const html = await page.content();
    console.log(html.substring(0, 500));
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let items = [];
    $('.bsx').each((i, el) => {
        items.push($(el).find('a').attr('href'));
    });
    console.log($('title').text());
    console.log(items.length > 0 ? items.slice(0, 3) : "No items found");
    
    await browser.close();
})();

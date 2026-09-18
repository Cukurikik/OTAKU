const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://g.shinigami.asia/', {waitUntil: 'networkidle2'}); 
    const html = await page.content();
    console.log(html.substring(0, 500));
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    console.log($('title').text());
    
    await browser.close();
})();

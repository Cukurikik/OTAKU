const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://v19.kuramanime.ing/anime', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    let hrefs = [];
    $('a[href*="/anime/"]').each((i, el) => {
        hrefs.push($(el).attr('href'));
    });
    console.log(Array.from(new Set(hrefs)).slice(0, 5));
    await browser.close();
})();

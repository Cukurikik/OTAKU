const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://v20.kuramanime.ing/anime/1873/oshi-no-ko', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    let hrefs = [];
    $('a').each((i, el) => {
        const href = $(el).attr('href');
        if(href && href.includes('1873')) hrefs.push(href);
    });
    console.log(Array.from(new Set(hrefs)));
    await browser.close();
})();

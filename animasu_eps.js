const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://animasu.love/anime/katainaka-no-ossan-kensei-ni-naru/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    let hrefs = [];
    $('.eplister a').each((i, el) => {
        hrefs.push($(el).attr('href'));
    });
    console.log(Array.from(new Set(hrefs)).slice(0, 3));
    await browser.close();
})();

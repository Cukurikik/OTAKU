const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://licil.org/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let items = [];
    $('.item-infinite, .gmr-box-content, .item, article').each((i, el) => {
        let title = $(el).find('.entry-title, h2, h3').text().trim();
        let link = $(el).find('a.gmr-watch-btn, a[itemprop="url"], a').attr('href');
        if (title && link) {
            items.push({title, link});
        }
    });
    console.log(items.slice(0, 5));
    
    await browser.close();
})();

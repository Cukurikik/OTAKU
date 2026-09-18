const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://subnime.id/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    console.log(html.substring(0, 500));
    const cheerio = require('cheerio');
    const $ = cheerio.load(html);
    
    let anchors = [];
    $("a").each((i, el) => {
        let title = $(el).attr('title') || $(el).text().trim();
        anchors.push({href: $(el).attr('href'), text: title});
    });
    console.log("Anchors:");
    console.log(anchors.slice(20, 30));
    
    await browser.close();
})();

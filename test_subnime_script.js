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
        if (js && (js.includes('m3u8') || js.includes('mp4') || js.includes('player') || js.includes('iframe') || js.includes('sources'))) {
            console.log("Script snippet:", js.substring(0, 300));
        }
    });
    
    // Check if there is an onclick action on the watch button
    let btns = await page.evaluate(() => {
        let res = [];
        document.querySelectorAll('a, button, div').forEach(el => {
            if (el.className && typeof el.className === 'string' && el.className.includes('watch')) {
                res.push({
                    tag: el.tagName,
                    classes: el.className,
                    onclick: el.getAttribute('onclick'),
                    href: el.getAttribute('href')
                });
            }
        });
        return res;
    });
    console.log("Watch buttons:", btns);
    
    await browser.close();
})();

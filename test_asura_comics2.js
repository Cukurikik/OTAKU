const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asurascans.com/comics', {waitUntil: 'networkidle2'});
    
    try { await page.waitForSelector('a', {timeout: 5000}); } catch(e){}
    
    const items = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a')).map(a => a.href);
    });
    console.log(items.filter(h => h.includes('series') || h.includes('comic')).slice(0, 15));
    await browser.close();
})();

const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/', {waitUntil: 'networkidle2'});
    
    let aTags = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a')).map(a => a.href);
    });
    console.log("All links:");
    console.log(aTags.slice(0, 30));
    
    await browser.close();
})();

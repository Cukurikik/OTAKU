const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/anime/one-piece', {waitUntil: 'networkidle2'});
    
    let aTags = await page.evaluate(() => {
        let els = Array.from(document.querySelectorAll('a[href*="-episode-10"]'));
        return els.map(el => ({
            href: el.href,
            classes: el.className,
            parentClass: el.parentElement.className,
            parentParentClass: el.parentElement.parentElement.className
        }));
    });
    console.log(aTags);
    
    await browser.close();
})();

const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/anime/one-piece', {waitUntil: 'networkidle2'});
    
    let aTags = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a')).map(a => ({
            href: a.href,
            text: a.innerText.trim().substring(0,20)
        }));
    });
    console.log("All links containing episode or ep:");
    console.log(aTags.filter(a => a.href.includes('episode') || a.href.includes('ep') || a.text.toLowerCase().includes('ep')));
    
    await browser.close();
})();

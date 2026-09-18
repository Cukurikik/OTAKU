const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/', {waitUntil: 'networkidle2'});
    
    // Find first latest episode on homepage
    let latestEps = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a[href*="episode"]')).map(a => a.href);
    });
    console.log("Latest episodes on homepage:", latestEps.slice(0,3));
    
    if (latestEps.length > 0) {
        await page.goto(latestEps[0], {waitUntil: 'networkidle2'});
        let container = await page.evaluate(() => {
            let el = document.getElementById('video-player-container');
            if (el) return Object.assign({}, el.dataset);
            return null;
        });
        console.log("Container Dataset for latest ep:", container);
    }
    
    await browser.close();
})();

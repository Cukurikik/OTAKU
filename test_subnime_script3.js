const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    
    let container = await page.evaluate(() => {
        let el = document.getElementById('video-player-container');
        if (el) {
            return Object.assign({}, el.dataset);
        }
        return null;
    });
    console.log("Container Dataset:", container);
    
    // Look for resolution buttons or similar
    let resButtons = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('.resolution-btn, [data-src]')).map(el => ({
            text: el.innerText,
            src: el.getAttribute('data-src') || el.dataset.src
        }));
    });
    console.log("Resolution Buttons / Data src:", resButtons);
    
    await browser.close();
})();

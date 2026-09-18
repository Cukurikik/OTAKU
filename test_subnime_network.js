const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    
    page.on('response', async (response) => {
        let url = response.url();
        if (url.includes('api') || url.includes('player') || url.includes('.m3u8') || url.includes('.mp4')) {
            console.log("Found Media/API URL:", url);
        }
    });
    
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    await browser.close();
})();

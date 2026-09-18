const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    
    page.on('response', async (response) => {
        if (response.url().includes('api') || response.url().includes('json')) {
            console.log("API URL: " + response.url());
        }
    });
    
    await page.goto('https://asurascans.com/series/shadow-slave', {waitUntil: 'networkidle2'});
    await browser.close();
})();

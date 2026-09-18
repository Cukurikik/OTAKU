const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    
    let videoData = await page.evaluate(() => {
        let iframes = Array.from(document.querySelectorAll('iframe')).map(i => i.src);
        let videos = Array.from(document.querySelectorAll('video, source')).map(v => v.src);
        return {iframes, videos};
    });
    console.log("Video data:", videoData);
    
    await browser.close();
})();

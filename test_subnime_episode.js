const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/', {waitUntil: 'networkidle2'});
    
    // Find an anime
    let link = await page.evaluate(() => {
        let a = document.querySelector('a[href*="/anime/"]');
        return a ? a.href : null;
    });
    console.log("Anime link:", link);
    
    if (link) {
        await page.goto(link, {waitUntil: 'networkidle2'});
        let episodes = await page.evaluate(() => {
            return Array.from(document.querySelectorAll('a[href*="/episode/"]')).map(a => a.href);
        });
        console.log("Episodes:", episodes.slice(0,3));
        
        if (episodes.length > 0) {
            await page.goto(episodes[0], {waitUntil: 'networkidle2'});
            let videoData = await page.evaluate(() => {
                let iframes = Array.from(document.querySelectorAll('iframe')).map(i => i.src);
                let videos = Array.from(document.querySelectorAll('video, source')).map(v => v.src);
                return {iframes, videos};
            });
            console.log("Video data:", videoData);
        }
    }
    
    await browser.close();
})();

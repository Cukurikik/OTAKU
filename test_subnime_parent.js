const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/anime/one-piece', {waitUntil: 'networkidle2'});
    
    let aTags = await page.evaluate(() => {
        let firstEpisodeLink = document.querySelector('a[href*="-episode-1"]');
        if (!firstEpisodeLink) return null;
        return {
            classes: firstEpisodeLink.className,
            parentClass: firstEpisodeLink.parentElement.className,
            parentParentClass: firstEpisodeLink.parentElement.parentElement.className
        };
    });
    console.log(aTags);
    
    await browser.close();
})();

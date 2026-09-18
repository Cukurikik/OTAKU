const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    
    let html = await page.content();
    console.log("HTML length:", html.length);
    console.log("iframes:", html.match(/<iframe[^>]+>/g));
    console.log("videos:", html.match(/<video[^>]+>/g));
    console.log("players:", html.match(/player/g)?.length);
    
    // Find all links that might be a video
    let links = await page.evaluate(() => {
        return Array.from(document.querySelectorAll('a')).map(a => a.href).filter(h => h.includes('play') || h.includes('video') || h.includes('stream'));
    });
    console.log("Stream links:", links);
    
    await browser.close();
})();

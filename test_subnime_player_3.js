const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://subnime.id/one-piece-episode-1', {waitUntil: 'networkidle2'});
    
    let html = await page.content();
    console.log(html.substring(0, 1000));
    
    // Look for video IDs, sources, embed
    let embedSrc = html.match(/src=["']([^"']*(?:embed|player)[^"']*)["']/i);
    console.log("Embed match:", embedSrc ? embedSrc[1] : "None");
    
    let dataMatches = html.match(/data-[a-z]+=["']([^"']*(?:embed|player)[^"']*)["']/ig);
    console.log("Data matches:", dataMatches ? dataMatches.slice(0,5) : "None");
    
    let base64Matches = html.match(/atob\(['"]([^'"]+)['"]\)/g);
    console.log("Base64 matches:", base64Matches ? base64Matches.slice(0,5) : "None");
    
    await browser.close();
})();

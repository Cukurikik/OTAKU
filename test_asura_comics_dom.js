const puppeteer = require('puppeteer');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.setUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
    await page.goto('https://asurascans.com/comics', {waitUntil: 'networkidle2'});
    
    const items = await page.evaluate(() => {
        let res = [];
        document.querySelectorAll("a[href*='/comics/']").forEach(a => {
            let href = a.href;
            if(href.split('/').length < 5) return; // skip /browse/comics etc
            let titleEl = a.querySelector('.text-sm, h3, .title, [class*=title]') || a;
            let title = titleEl.innerText;
            let imgEl = a.querySelector('img');
            let img = imgEl ? imgEl.src : '';
            if (title && img && href) {
                res.push({title: title.substring(0,20).replace(/\n/g, ''), href, img});
            }
        });
        return res;
    });
    console.log(items.slice(0, 5));
    
    // Now let's try opening one chapter to get the pages
    if(items.length > 0) {
        let detUrl = items[0].href;
        await page.goto(detUrl, {waitUntil: 'networkidle2'});
        const chaps = await page.evaluate(() => {
            let res = [];
            document.querySelectorAll("a[href*='/chapter/'], a[href*='-chapter-']").forEach(a => {
                res.push({title: a.innerText.substring(0, 15).replace(/\n/g, ''), href: a.href});
            });
            return res;
        });
        console.log("Chapters:", chaps.slice(0,3));
        
        if (chaps.length > 0) {
            await page.goto(chaps[0].href, {waitUntil: 'networkidle2'});
            const pages = await page.evaluate(() => {
                let res = [];
                document.querySelectorAll("img[src*='asura'], img[alt*='chapter']").forEach(img => {
                    if(!img.src.startsWith('data:')) res.push(img.src);
                });
                return res;
            });
            console.log("Pages:", pages.slice(0,3));
        }
    }
    
    await browser.close();
})();

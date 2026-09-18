const puppeteer = require('puppeteer');
const fs = require('fs');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://animasu.love/nonton-dragon-raja-season-2-episode-16/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    fs.writeFileSync('animasu_vid.html', html);
    await browser.close();
})();

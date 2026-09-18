const puppeteer = require('puppeteer');
const fs = require('fs');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://animasu.love/anime/dragon-raja-s2/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    fs.writeFileSync('animasu_dragon.html', html);
    await browser.close();
})();

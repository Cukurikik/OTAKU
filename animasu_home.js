const puppeteer = require('puppeteer');
const fs = require('fs');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://v2.animasu.work/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    fs.writeFileSync('animasu.html', html);
    await browser.close();
})();

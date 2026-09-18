const puppeteer = require('puppeteer');
const fs = require('fs');
(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://v20.kuramanime.ing/anime/1873/oshi-no-ko', {waitUntil: 'networkidle2'});
    const html = await page.content();
    fs.writeFileSync('oshi.html', html);
    await browser.close();
})();

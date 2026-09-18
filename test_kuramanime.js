const puppeteer = require('puppeteer');

(async () => {
    const browser = await puppeteer.launch({args: ['--no-sandbox']});
    const page = await browser.newPage();
    await page.goto('https://v19.kuramanime.ing/', {waitUntil: 'networkidle2'});
    const html = await page.content();
    console.log(html.substring(0, 500));
    await browser.close();
})();

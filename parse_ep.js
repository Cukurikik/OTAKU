const fs = require('fs');
const html = fs.readFileSync('animasu_ep.html', 'utf8');
const cheerio = require('cheerio');
const $ = cheerio.load(html);
console.log($('iframe').length, $('video').length);
console.log($('.player').html());
console.log($('#pembed').html());

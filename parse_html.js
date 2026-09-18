const fs = require('fs');
const html = fs.readFileSync('kurama.html', 'utf8');
const cheerio = require('cheerio');
const $ = cheerio.load(html);
console.log('Video tags:', $('video').length);
console.log('Iframes:', $('iframe').length);
console.log('Main content html:');
console.log($('.anime__video__player').html());
console.log($('#player').html());

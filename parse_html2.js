const fs = require('fs');
const html = fs.readFileSync('kurama.html', 'utf8');
const cheerio = require('cheerio');
const $ = cheerio.load(html);
let classes = [];
$('*').each((i, el) => {
    if (el.attribs && el.attribs.class) {
        classes.push(el.attribs.class);
    }
});
console.log(Array.from(new Set(classes)).join(', '));

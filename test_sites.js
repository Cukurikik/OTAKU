const https = require('https');

function fetch(url) {
    https.get(url, {headers: {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}}, (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => { 
            console.log(url, "-> Length:", data.length, "Status:", res.statusCode);
            if (data.includes('Just a moment')) console.log("CF detected for", url);
            else if (data.includes('bsx')) console.log("Has bsx for", url);
        });
    }).on("error", (err) => console.log(url, "Error:", err.message));
}

fetch("https://komik6.mangatoon.cc/");
fetch("https://bacami.net/");
fetch("https://v2.komikcast.fit/");
fetch("https://v3.komikcast.fit/");
fetch("https://g.shinigami.asia/");

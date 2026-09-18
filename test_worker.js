const https = require('https');
https.get("https://anime-aggregator.njirlah.workers.dev/?url=https%3A%2F%2Fweb1.mgkomik.cc%2F", {
  headers: {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Authorization": "Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4"
  }
}, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => { 
    if (data.includes('Just a moment') || data.includes('Cloudflare')) {
        console.log("Cloudflare detected through worker!");
    } else {
        console.log("Worker HTML length:", data.length);
        console.log("Has bsx:", data.includes('bsx'));
        console.log("Has bixbox:", data.includes('bixbox'));
    }
  });
}).on("error", (err) => {
  console.log("Error: " + err.message);
});

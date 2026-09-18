const https = require('https');
https.get("https://web1.mgkomik.cc/", {headers: {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}}, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => { 
    if (data.includes('Just a moment') || data.includes('Cloudflare')) {
        console.log("Cloudflare detected!");
    } else {
        console.log("HTML length:", data.length);
        console.log("Has bsx:", data.includes('bsx'));
        console.log("Has bixbox:", data.includes('bixbox'));
    }
  });
}).on("error", (err) => {
  console.log("Error: " + err.message);
});

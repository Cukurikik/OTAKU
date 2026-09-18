const https = require('https');
https.get("https://v19.kuramanime.ing/", {headers: {"User-Agent": "Mozilla/5.0"}}, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => { 
    console.log("Length:", data.length, "Status:", res.statusCode);
    if (data.includes('Just a moment')) console.log("CF");
  });
}).on("error", (err) => console.log(err.message));

const https = require('https');
https.get("https://komikcast.cz/", {headers: {"User-Agent": "Mozilla/5.0"}}, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => { 
    console.log("Length:", data.length, "Status:", res.statusCode);
  });
}).on("error", (err) => {
  console.log("Error: " + err.message);
});

const https = require('https');
https.get('https://api.asurascans.com/api/series/shadow-slave', (res) => {
    let data = '';
    res.on('data', (chunk) => data += chunk);
    res.on('end', () => {
        let json = JSON.parse(data);
        if (json.series) {
            console.log("Keys in series:", Object.keys(json.series));
            if (json.series.chapters) {
                console.log("First chapter:", json.series.chapters[0]);
            }
        }
    });
});

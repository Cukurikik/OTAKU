const cheerio = require('cheerio');
const https = require('https');

async function fetchWorker(url) {
    return new Promise((resolve, reject) => {
        const workerUrl = "https://anime-aggregator.njirlah.workers.dev/?url=" + encodeURIComponent(url);
        https.get(workerUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0',
                'Authorization': 'Bearer cfat_xn2jWccJ7wCDYnomga23f37Hi5olRHCiAdGM4CxE5ee56ab4'
            }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => resolve(data));
        }).on('error', reject);
    });
}

const configs = {
    "viki": { url: "https://www.viki.com", card: ".film-list .item, .listupd .bs, .bsx, article, .post-item, .item, .gmr-box-content, .movie-item, .album, .video-block, .post, .video-post, .flw-item", link: "a", title: ".tt, .title, .entry-title, h2, h3, h5, .album-title, .video-title, .titlelist", img: "img, .thumb img, .kover img, .video-thumb img" },
    "licil": { url: "https://licil.org", card: ".item-infinite, .gmr-box-content, .item, article", link: "a.gmr-watch-btn, a[itemprop='url'], a", title: ".entry-title, h2, h3", img: "img" },
    "ip-178": { url: "http://178.128.91.191", card: ".item-infinite, .gmr-box-content, .item, article", link: "a.gmr-watch-btn, a[itemprop='url'], a", title: ".entry-title, h2, h3", img: "img" },
    "jucelebrity": { url: "https://jucelebrity.com", card: ".item-infinite, .gmr-box-content, .item, article", link: "a.gmr-watch-btn, a[itemprop='url'], a", title: ".entry-title, h2, h3", img: "img" },
    "lokalfilm": { url: "https://lokalfilm.id", card: ".movie-item", link: "a.video-icon, a", title: "h3, .title, a", img: "img" },
    "erome": { url: "https://www.erome.com/explore", card: ".album", link: "a", title: ".album-title, h3, h2", img: "img" },
    "xgemoy": { url: "https://xgemoy.com", card: ".video-preview-item, .post, .video-block, .thumb-block", link: "a", title: ".entry-header span, .entry-title, .title, h3, h5, .video-title", img: "img" },
    "ngentub": { url: "https://ngentub.cc", card: ".video-preview-item, .post, .video-block, .thumb-block", link: "a", title: ".entry-header span, .entry-title, .title, h3, h5, .video-title", img: "img" },
    "avtub": { url: "https://www.avtub.net", card: ".video-preview-item, .post, .video-block, .thumb-block", link: "a", title: ".entry-header span, .entry-title, .title, h3, h5, .video-title", img: "img" },
    "dicrotin": { url: "https://dicrotin.com", card: ".video-preview-item, .post, .video-block, .thumb-block", link: "a", title: ".entry-header span, .entry-title, .title, h3, h5, .video-title", img: "img" }
};

async function run() {
    for (const [key, c] of Object.entries(configs)) {
        console.log(`\n--- Testing ${key} (${c.url}) ---`);
        try {
            const html = await fetchWorker(c.url);
            const $ = cheerio.load(html);
            const cards = $(c.card);
            console.log(`Found ${cards.length} cards.`);
            if (cards.length > 0) {
                const card = cards.first();
                const link = card.find(c.link).first().attr('href') || card.attr('href') || 'No link';
                const img = card.find(c.img).first();
                const imgSrc = img.attr('data-setbg') || img.attr('data-src') || img.attr('data-lazy-src') || img.attr('src') || 'No image';
                let title = card.find(c.title).first().text().trim();
                if (!title) title = card.find('a').first().attr('title');
                if (!title) title = card.find('a').first().text().trim();
                
                console.log(`Sample [${title}] -> Link: ${link} | Img: ${imgSrc.substring(0, 50)}`);
            } else {
                console.log("No cards found. Sample HTML:", html.substring(0, 300));
            }
        } catch (e) {
            console.log(`Error: ${e.message}`);
        }
    }
}
run();

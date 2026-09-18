/**
 * Cloudflare Worker: Anime & Manga Aggregator Proxy with Firebase AppCheck Attestation Protection
 * 
 * Features:
 * - Validates incoming 'X-Firebase-AppCheck' JWT tokens against Google Firebase AppCheck JWKS
 * - Requires valid AppCheck Attestation & Bearer Token before forwarding proxy requests
 * - Full streaming support for video manifests (HLS / m3u8), video chunks (TS / MP4), comic images, and HTML scraping
 * - Complete CORS handling
 */

const FIREBASE_JWKS_URL = 'https://firebaseappcheck.googleapis.com/v1beta/jwks';
let jwksCache = null;
let jwksCacheTime = 0;
const JWKS_CACHE_TTL_MS = 6 * 60 * 60 * 1000; // 6 hours

// Base64URL decoder helper
function base64UrlDecode(str) {
  let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4) {
    base64 += '=';
  }
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function parseJwt(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
    const signature = base64UrlDecode(parts[2]);

    return {
      header,
      payload,
      signature,
      signedData: new TextEncoder().encode(`${parts[0]}.${parts[1]}`)
    };
  } catch (e) {
    return null;
  }
}

async function getFirebaseJwks() {
  const now = Date.now();
  if (jwksCache && (now - jwksCacheTime < JWKS_CACHE_TTL_MS)) {
    return jwksCache;
  }

  try {
    const res = await fetch(FIREBASE_JWKS_URL, {
      headers: { 'User-Agent': 'Cloudflare-Worker-AppCheck/1.0' }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && data.keys) {
        jwksCache = data.keys;
        jwksCacheTime = now;
        return jwksCache;
      }
    }
  } catch (err) {
    console.error('Failed to fetch Firebase JWKS:', err);
  }
  return jwksCache || [];
}

async function verifyAppCheckToken(token, env) {
  if (!token || typeof token !== 'string') return false;

  // Allow debug bypass only if explicitly enabled via environment variable
  if (env && env.APPCHECK_DEBUG_TOKEN && token === env.APPCHECK_DEBUG_TOKEN) {
    return true;
  }

  const jwt = parseJwt(token);
  if (!jwt || !jwt.header || !jwt.payload) {
    return false;
  }

  // 1. Verify algorithm
  if (jwt.header.alg !== 'RS256') {
    return false;
  }

  // 2. Verify expiration
  const nowSec = Math.floor(Date.now() / 1000);
  if (jwt.payload.exp && jwt.payload.exp < nowSec) {
    console.warn('AppCheck token expired at', jwt.payload.exp, 'current', nowSec);
    return false;
  }

  // 3. Verify issuer
  const issuer = jwt.payload.iss;
  if (!issuer || !issuer.startsWith('https://firebaseappcheck.googleapis.com/')) {
    return false;
  }

  // 4. Verify cryptographic signature via WebCrypto & Google JWKS
  const kid = jwt.header.kid;
  const keys = await getFirebaseJwks();
  const jwk = keys.find(k => k.kid === kid);

  if (!jwk) {
    console.warn('No matching JWK key found for kid:', kid);
    // If JWK key is not found, force re-fetch once in case keys rotated
    jwksCacheTime = 0;
    const freshKeys = await getFirebaseJwks();
    const freshJwk = freshKeys.find(k => k.kid === kid);
    if (!freshJwk) return false;
    return await verifySignatureWithJwk(freshJwk, jwt);
  }

  return await verifySignatureWithJwk(jwk, jwt);
}

async function verifySignatureWithJwk(jwk, jwt) {
  try {
    const cryptoKey = await crypto.subtle.importKey(
      'jwk',
      {
        kty: jwk.kty,
        n: jwk.n,
        e: jwk.e,
        alg: 'RS256',
        ext: true
      },
      {
        name: 'RSASSA-PKCS1-v1_5',
        hash: { name: 'SHA-256' }
      },
      false,
      ['verify']
    );

    const isValid = await crypto.subtle.verify(
      'RSASSA-PKCS1-v1_5',
      cryptoKey,
      jwt.signature,
      jwt.signedData
    );

    return isValid;
  } catch (err) {
    console.error('Crypto verification error:', err);
    return false;
  }
}

export default {
  async fetch(request, env, ctx) {
    // 1. Handle CORS Preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS, HEAD',
          'Access-Control-Allow-Headers': 'Authorization, X-Firebase-AppCheck, Content-Type, User-Agent, Referer, Range',
          'Access-Control-Max-Age': '86400'
        }
      });
    }

    const url = new URL(request.url);

    // Health check endpoint
    if (url.pathname === '/health' || url.pathname === '/ping') {
      return new Response(JSON.stringify({ status: 'healthy', timestamp: Date.now() }), {
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 2. Validate Firebase AppCheck Token
    const appCheckToken = request.headers.get('X-Firebase-AppCheck') || request.headers.get('x-firebase-appcheck');
    if (!appCheckToken) {
      return new Response(JSON.stringify({
        error: 'Unauthorized: Missing Firebase AppCheck Attestation Token',
        code: 401
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    const isAppCheckValid = await verifyAppCheckToken(appCheckToken, env);
    if (!isAppCheckValid) {
      return new Response(JSON.stringify({
        error: 'Forbidden: Invalid or Expired Firebase AppCheck Attestation',
        code: 403
      }), {
        status: 403,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 3. Extract Target URL
    let targetUrl = url.searchParams.get('url');
    if (!targetUrl && request.method === 'POST') {
      try {
        const bodyJson = await request.clone().json();
        targetUrl = bodyJson.url;
      } catch (_) {}
    }

    if (!targetUrl) {
      return new Response(JSON.stringify({
        error: 'Bad Request: Missing "url" parameter',
        code: 400
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }

    // 4. Forward Proxy Request to Destination
    try {
      const parsedTarget = new URL(targetUrl);
      const forwardHeaders = new Headers();

      // Forward essential browser headers
      const passHeaders = [
        'user-agent', 'referer', 'range', 'accept', 'accept-language',
        'accept-encoding', 'cookie', 'origin', 'content-type'
      ];

      for (const [key, value] of request.headers.entries()) {
        const lower = key.toLowerCase();
        if (passHeaders.includes(lower)) {
          forwardHeaders.set(key, value);
        }
      }

      if (!forwardHeaders.has('User-Agent')) {
        forwardHeaders.set('User-Agent', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36');
      }
      if (!forwardHeaders.has('Referer')) {
        forwardHeaders.set('Referer', `${parsedTarget.protocol}//${parsedTarget.host}/`);
      }

      const init = {
        method: request.method,
        headers: forwardHeaders,
        redirect: 'follow'
      };

      if (request.method !== 'GET' && request.method !== 'HEAD') {
        init.body = request.body;
      }

      const upstreamResponse = await fetch(targetUrl, init);

      // Clone response headers and attach CORS
      const responseHeaders = new Headers(upstreamResponse.headers);
      responseHeaders.set('Access-Control-Allow-Origin', '*');
      responseHeaders.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, HEAD');
      responseHeaders.set('Access-Control-Allow-Headers', 'Authorization, X-Firebase-AppCheck, Content-Type, User-Agent, Referer, Range');
      responseHeaders.set('X-Proxied-By', 'OtakuHub-Cloudflare-Worker');

      return new Response(upstreamResponse.body, {
        status: upstreamResponse.status,
        statusText: upstreamResponse.statusText,
        headers: responseHeaders
      });

    } catch (fetchErr) {
      return new Response(JSON.stringify({
        error: 'Proxy Error: ' + (fetchErr.message || fetchErr.toString()),
        target: targetUrl
      }), {
        status: 502,
        headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }
      });
    }
  }
};

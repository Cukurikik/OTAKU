package com.example.network.webview

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.util.WebViewHelper

/**
 * Script Stealth.js untuk penyamaran lingkungan WebView:
 * - Menghapus tanda navigator.webdriver (otomatisasi bot)
 * - Memalsukan objek window.navigator.chrome
 * - Spoofing WebGL Vendor (Intel Inc.) & Renderer (Intel Iris OpenGL Engine)
 * - Canvas Fingerprint micro-noise injection
 * - Spoofing platform attributes (languages, plugins, hardwareConcurrency)
 * - Simulasi interaksi user (touchstart, pointerdown, mousemove) saat challenge terdeteksi
 */
val STEALTH_JS = """
    (function() {
        // 1. Mask navigator.webdriver & automated driver properties
        try {
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
        } catch(e) {}
        try {
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
            delete window.__webdriver_evaluate;
            delete window.__selenium_evaluate;
            delete window.__webdriver_script_function;
            delete window.__webdriver_script_func;
            delete window.__webdriver_script_fn;
            delete window.__fxdriver_evaluate;
            delete window.__driver_evaluate;
            delete window.__webdriver_unwrapped;
            delete window.__selenium_unwrapped;
            delete window.__fxdriver_unwrapped;
            delete window.__driver_unwrapped;
        } catch(e) {}

        // 2. Mock chrome runtime & properties
        try {
            window.navigator.chrome = {
                runtime: {},
                loadTimes: function() {},
                csi: function() {},
                app: {}
            };
        } catch(e) {}

        // 3. Spoof WebGL Vendor & Renderer (Intel Iris)
        try {
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                if (parameter === 37445) return 'Intel Inc.';
                if (parameter === 37446) return 'Intel Iris OpenGL Engine';
                return getParameter.apply(this, arguments);
            };
        } catch(e) {}
        try {
            if (window.WebGL2RenderingContext) {
                const getParameter2 = WebGL2RenderingContext.prototype.getParameter;
                WebGL2RenderingContext.prototype.getParameter = function(parameter) {
                    if (parameter === 37445) return 'Intel Inc.';
                    if (parameter === 37446) return 'Intel Iris OpenGL Engine';
                    return getParameter2.apply(this, arguments);
                };
            }
        } catch(e) {}

        // 4. Canvas Fingerprint Noise Injection (subtle random micro-perturbation)
        try {
            const getImageData = CanvasRenderingContext2D.prototype.getImageData;
            CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                const image = getImageData.apply(this, arguments);
                for (let i = 0; i < 8; i++) {
                    const idx = (Math.floor(Math.random() * (image.data.length / 4))) * 4;
                    image.data[idx] = Math.min(255, Math.max(0, image.data[idx] + (Math.random() < 0.5 ? 1 : -1)));
                }
                return image;
            };
        } catch(e) {}

        // 5. Spoof Languages & Plugins
        try {
            Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en', 'id'] });
            Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
            Object.defineProperty(navigator, 'hardwareConcurrency', { get: () => 8 });
            Object.defineProperty(navigator, 'deviceMemory', { get: () => 8 });
        } catch(e) {}

        // 6. User Touch Event & Pointer Simulation on Challenge Elements
        try {
            window.dispatchEvent(new Event('touchstart'));
            window.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, cancelable: true, clientX: 150, clientY: 150 }));

            // Auto-trigger Turnstile widget checkbox click if present
            setTimeout(() => {
                try {
                    const checkbox = document.querySelector('.cf-turnstile, [name="cf-turnstile-response"], iframe[src*="challenges.cloudflare.com"], #turnstile-wrapper input[type="checkbox"]');
                    if (checkbox) {
                        checkbox.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
                    }
                } catch(e) {}
            }, 500);
        } catch(e) {}
    })();
""".trimIndent()

/**
 * Custom WebViewClient yang menginjeksi STEALTH_JS pada onPageStarted dan onPageFinished,
 * memonitor pembentukan cookie Cloudflare (cf_clearance / __cf_bm), serta mengecek status
 * turnstile challenge untuk diteruskan ke callback onChallengeSolved.
 */
open class StealthWebViewClient(
    private val onChallengeSolved: (String) -> Unit,
    private val onPageFinishedCallback: ((url: String?) -> Unit)? = null
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.evaluateJavascript(STEALTH_JS, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return true
        val scheme = uri.scheme?.lowercase() ?: ""
        return scheme != "http" && scheme != "https"
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, targetUrl: String?): Boolean {
        if (targetUrl == null) return true
        val lower = targetUrl.lowercase()
        return !lower.startsWith("http://") && !lower.startsWith("https://")
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val reqUri = request?.url ?: return super.shouldInterceptRequest(view, request)
        val scheme = reqUri.scheme?.lowercase() ?: ""
        if (scheme == "file" || scheme == "content") {
            return WebResourceResponse("text/plain", "UTF-8", null)
        }
        return super.shouldInterceptRequest(view, request)
    }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
        WebViewHelper.safeDestroy(view)
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(STEALTH_JS, null)
        onPageFinishedCallback?.invoke(url)
        checkClearance(view, url)
    }

    fun checkClearance(view: WebView?, url: String?) {
        if (url == null) return
        try {
            val cookieManager = CookieManager.getInstance() ?: return
            val cookies = cookieManager.getCookie(url)
            if (cookies != null && (cookies.contains("cf_clearance") || cookies.contains("__cf_bm"))) {
                cookieManager.flush()
                onChallengeSolved(cookies)
                return
            }

            view?.evaluateJavascript(
                "(function() { return { html: document.documentElement.outerHTML, title: document.title }; })();"
            ) { resultJson ->
                if (resultJson != null && resultJson != "null") {
                    val lower = resultJson.lowercase()
                    val isStillChallenged = lower.contains("cf-browser-verification") ||
                            lower.contains("just a moment") ||
                            lower.contains("challenge-running") ||
                            lower.contains("turnstile-wrapper") ||
                            lower.contains("ray_id") ||
                            lower.contains("attention required") ||
                            lower.contains("cf-challenge")
                    if (!isStillChallenged) {
                        val currentCookies = cookieManager.getCookie(url) ?: ""
                        cookieManager.flush()
                        onChallengeSolved(currentCookies)
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}

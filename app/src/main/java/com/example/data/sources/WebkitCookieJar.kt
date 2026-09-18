package com.example.data.sources

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebkitCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookiesString = cookieManager.getCookie(urlString)
        val cookies = mutableListOf<Cookie>()
        if (cookiesString != null && cookiesString.isNotEmpty()) {
            val cookiePairs = cookiesString.split(";")
            for (cookiePair in cookiePairs) {
                val cookie = Cookie.parse(url, cookiePair.trim())
                if (cookie != null) {
                    cookies.add(cookie)
                }
            }
        }
        return cookies
    }
}

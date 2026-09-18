package com.example.data.sources

class CloudflareException(val url: String) : Exception("Cloudflare challenge requires user interaction: $url")

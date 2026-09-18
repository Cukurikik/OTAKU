package com.example.config

import android.content.Context
import coil.ImageLoader

/**
 * Forwarding alias and factory conforming to package structure
 */
typealias CoilConfig = com.example.CoilConfig

fun createReaderImageLoader(context: Context): ImageLoader {
    return com.example.createReaderImageLoader(context)
}

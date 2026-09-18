package com.example.ui.screens

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.text.SubtitleDecoderFactory

class MyRenderersFactory(context: Context, val mySubtitleFactory: SubtitleDecoderFactory) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: java.util.ArrayList<Renderer>
    ) {
        out.add(TextRenderer(output, outputLooper, mySubtitleFactory))
    }
}

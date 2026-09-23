package com.example.ui.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.text.SubtitleDecoder
import java.util.ArrayList

/**
 * Custom SubtitleDecoderFactory for Advanced SubStation Alpha (.ass / .ssa)
 * and WebVTT/SRT styling preservation.
 */
@OptIn(UnstableApi::class)
class CustomSubtitleDecoderFactory(
    private val delegate: SubtitleDecoderFactory = SubtitleDecoderFactory.DEFAULT
) : SubtitleDecoderFactory {

    override fun supportsFormat(format: Format): Boolean {
        val mime = format.sampleMimeType ?: return false
        return mime == MimeTypes.TEXT_SSA ||
                mime == MimeTypes.TEXT_VTT ||
                mime == MimeTypes.APPLICATION_SUBRIP ||
                mime == MimeTypes.APPLICATION_TTML ||
                delegate.supportsFormat(format)
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        return delegate.createDecoder(format)
    }
}

/**
 * Custom RenderersFactory that injects CustomSubtitleDecoderFactory
 * for high-fidelity subtitle decoding and rendering in Anime Player.
 */
@OptIn(UnstableApi::class)
class CustomRenderersFactory(
    context: Context,
    private val subtitleDecoderFactory: SubtitleDecoderFactory = CustomSubtitleDecoderFactory()
) : DefaultRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        out.add(TextRenderer(output, outputLooper, subtitleDecoderFactory))
    }
}

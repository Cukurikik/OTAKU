import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import android.content.Context

fun getRenderersFactory(context: Context, mySubtitleDecoderFactory: SubtitleDecoderFactory): DefaultRenderersFactory {
    // Media3 TextRenderer takes SubtitleDecoderFactory? No, DefaultRenderersFactory does?
    // Let's check how DefaultRenderersFactory uses it.
    // There isn't a direct setter in DefaultRenderersFactory.
}

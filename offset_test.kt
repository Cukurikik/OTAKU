import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.SubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.common.Format

class OffsetSubtitleDecoder(val original: SubtitleDecoder, val offsetUs: Long) : SubtitleDecoder {
    override fun getName() = original.name
    // ...
}

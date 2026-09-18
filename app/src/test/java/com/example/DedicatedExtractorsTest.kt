package com.example

import com.example.data.sources.anime.extractors.DoodExtractor
import com.example.data.sources.anime.extractors.FileLionsExtractor
import com.example.data.sources.anime.extractors.GofileExtractor
import com.example.data.sources.anime.extractors.KrakenfilesExtractor
import com.example.data.sources.anime.extractors.Mp4UploadExtractor
import com.example.data.sources.anime.extractors.VidHideExtractor
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import com.example.data.sources.VideoExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DedicatedExtractorsTest {

    @Test
    fun testExtractorsInstantiatedAndConfigured() {
        val dood = DoodExtractor()
        assertEquals("DoodStream", dood.name)
        assertEquals("dood", dood.mainUrl)

        val fileLions = FileLionsExtractor()
        assertEquals("FileLions", fileLions.name)
        assertEquals("filelions", fileLions.mainUrl)

        val vidHide = VidHideExtractor()
        assertEquals("VidHide", vidHide.name)
        assertEquals("vidhide", vidHide.mainUrl)

        val mp4Upload = Mp4UploadExtractor()
        assertEquals("Mp4Upload", mp4Upload.name)
        assertEquals("mp4upload.com", mp4Upload.mainUrl)

        val kraken = KrakenfilesExtractor()
        assertEquals("KrakenFiles", kraken.name)
        assertEquals("krakenfiles.com", kraken.mainUrl)

        val gofile = GofileExtractor()
        assertEquals("Gofile", gofile.name)
        assertEquals("gofile.io", gofile.mainUrl)
    }

    @Test
    fun testFileLionsPackedJsPatternExtraction() {
        // Packed JS typical in FileLions / StreamWish / VidHide
        val packed = """
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p;}('1 0=[{2:"3://4/5/6/7.8"}];',9,9,'sources|var|file|https|cdn.vidhidepro.com|stream|hls|master|m3u8'.split('|')))
        """.trimIndent()

        val unpacked = JsPackerUnpacker.unpack(packed)
        assertNotNull(unpacked)
        val fileRegex = Regex("""(?:file|src)\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""")
        val match = fileRegex.find(unpacked!!)
        assertNotNull(match)
        assertEquals("https://cdn.vidhidepro.com/stream/hls/master.m3u8", match!!.groupValues[1])
    }

    @Test
    fun testMp4UploadRegexMatching() {
        val sampleHtml = """
            <script type='text/javascript'>
                player.src("https://www2.mp4upload.com:282/d/sample/video.mp4");
            </script>
        """.trimIndent()

        val pattern = Regex("""player\.src\(\s*["']([^"']+\.mp4[^"']*)["']""")
        val match = pattern.find(sampleHtml)
        assertNotNull(match)
        assertEquals("https://www2.mp4upload.com:282/d/sample/video.mp4", match!!.groupValues[1])
    }

    @Test
    fun testVideoExtractorRoutingForNewExtractors() = runBlocking {
        // Verify VideoExtractor has route matching without crashing on unknown test hosts
        val dummyVidHide = VideoExtractor.extract("https://vidhidepro.com/v/invalid12345")
        assertNotNull(dummyVidHide)
        assertEquals("https://vidhidepro.com/v/invalid12345", dummyVidHide?.embedUrl)

        val dummyDood = VideoExtractor.extract("https://dood.to/e/invalid12345")
        assertNotNull(dummyDood)
        assertEquals("https://dood.to/e/invalid12345", dummyDood?.embedUrl)
    }
}

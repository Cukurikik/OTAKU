package com.example

import com.example.data.sources.drama.DramaServerSource
import com.example.data.sources.drama.MultiServerDramaResolver
import com.example.data.sources.drama.ServerTab
import com.example.data.sources.drama.extractors.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Proposal3MultiHostDecryptionTest {

    private val resolver = MultiServerDramaResolver()

    @Test
    fun testParseServerTabsFromHtmlDom() {
        val base64Embed = Base64.getEncoder().encodeToString("https://filelions.to/v/fl12345".toByteArray())
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="server-tabs">
                    <a href="#" data-embed="https://hxfile.co/embed-hx987.html" class="active">Server Hxfile</a>
                    <a href="#" data-embed="$base64Embed">Server FileLions</a>
                    <a href="#" data-embed="https://mixdrop.co/e/mx555">Server Mixdrop</a>
                    <button data-src="https://vidhidepro.com/v/vh333">VidHide VIP</button>
                    <a href="#" data-embed="https://drive.google.com/file/d/123/preview">Google Drive</a>
                </div>
                <div class="video-container">
                    <iframe src="https://streamsb.net/e/sb999.html"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val tabs = resolver.parseServerTabs(html, "https://drama.example.com/watch/ep1")
        assertTrue(tabs.size >= 5)

        val hxTab = tabs.find { it.name.contains("HxFile", ignoreCase = true) }
        assertNotNull(hxTab)
        assertEquals("https://hxfile.co/embed-hx987.html", hxTab?.embedUrl)
        assertTrue(hxTab!!.isPrimary)

        val flTab = tabs.find { it.name.contains("FileLions", ignoreCase = true) }
        assertNotNull(flTab)
        assertEquals("https://filelions.to/v/fl12345", flTab?.embedUrl)

        val mxTab = tabs.find { it.name.contains("Mixdrop", ignoreCase = true) }
        assertNotNull(mxTab)
        assertEquals("https://mixdrop.co/e/mx555", mxTab?.embedUrl)

        val vhTab = tabs.find { it.name.contains("VidHide", ignoreCase = true) }
        assertNotNull(vhTab)
        assertEquals("https://vidhidepro.com/v/vh333", vhTab?.embedUrl)

        val gdTab = tabs.find { it.name.contains("Google Drive", ignoreCase = true) }
        assertNotNull(gdTab)
    }

    @Test
    fun testMultiServerDramaResolverDirectStreamAndSubtitles() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script>
                    var player = {
                        file: "https://cdn.streamvip.net/hls/master.m3u8"
                    };
                </script>
            </head>
            <body>
                <div class="server-tabs">
                    <a href="#" data-embed="https://stream.server.com/backup.mp4" class="active">Server Backup</a>
                </div>
                <video id="player">
                    <track kind="subtitles" src="/subs/indonesia.vtt" srclang="id" label="Indonesian">
                </video>
            </body>
            </html>
        """.trimIndent()

        val sources = resolver.resolveServerSources(html, "https://drama.example.com/ep-1")
        assertTrue(sources.isNotEmpty())

        val vip = sources.find { it.serverName.contains("VIP", ignoreCase = true) }
        assertNotNull(vip)
        assertEquals("https://cdn.streamvip.net/hls/master.m3u8", vip?.streamUrl)
        assertTrue(vip!!.isPrimary)

        val streamInfo = resolver.resolveStreamInfoWithMultiServer(html, "https://drama.example.com/ep-1")
        assertNotNull(streamInfo)
        assertEquals("https://cdn.streamvip.net/hls/master.m3u8", streamInfo?.embedUrl)
        assertTrue(streamInfo!!.qualities.isNotEmpty())
        assertEquals(1, streamInfo.subtitles.size)
        assertEquals("Indonesian", streamInfo.subtitles[0].language)
    }

    @Test
    fun testUnpackFileLionsAndVidHidePayload() {
        // Simulating Dean Edwards packed payload
        val packedScript = """
            eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('4.3({2:[{0:"1://6.5.7/8/9.a"}]});',11,11,'file|https|sources|setup|player|streamcdn|vip|com|hls|video|m3u8'.split('|'),0,{}))
        """.trimIndent()

        val unpacked = com.example.data.sources.anime.extractors.JsPackerUnpacker.unpack(packedScript)
        assertNotNull(unpacked)
        assertTrue(unpacked!!.contains("https://vip.streamcdn.com/hls/video.m3u8"))
    }

    @Test
    fun testMixdropPayloadParsing() {
        // Simulating Mixdrop MDCore unpacked code
        val mdCoreJs = """
            var MDCore = {};
            MDCore.vurl = "//s-delivery45.mixdrop.to/v/mx_stream_1080p.mp4";
            MDCore.wurl = "//s-delivery45.mixdrop.to/w/mx_stream_720p.mp4";
        """.trimIndent()

        val regex = Regex("""(?:MDCore\.(?:vurl|wurl|url)|wurl|vurl)\s*=\s*["']([^"']+)["']""")
        val match = regex.find(mdCoreJs)
        assertNotNull(match)
        var streamUrl = match!!.groupValues[1]
        if (streamUrl.startsWith("//")) {
            streamUrl = "https:$streamUrl"
        }
        assertEquals("https://s-delivery45.mixdrop.to/v/mx_stream_1080p.mp4", streamUrl)
    }
}

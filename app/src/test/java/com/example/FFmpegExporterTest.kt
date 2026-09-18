package com.example

import android.content.Context
import com.example.download.FFmpegExporterEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FFmpegExporterTest {

    @Test
    fun testBuildExportCommandFormat() {
        val input = "/storage/emulated/0/Android/data/com.example/cache/test.m3u8"
        val output = "/storage/emulated/0/Movies/OtakuHub/test.mp4"

        val command = FFmpegExporterEngine.buildExportCommand(input, output)

        val expected = "-y -i \"/storage/emulated/0/Android/data/com.example/cache/test.m3u8\" -c copy \"/storage/emulated/0/Movies/OtakuHub/test.mp4\""
        assertEquals(expected, command)
    }

    @Test
    fun testRegisterWithMediaStore() {
        val context: Context = RuntimeEnvironment.getApplication()

        // Create a temporary dummy file to register
        val tempFile = File(context.cacheDir, "test_export.mp4").apply {
            writeBytes("DUMMY_MP4_CONTENT".toByteArray())
        }

        val uri = FFmpegExporterEngine.registerWithMediaStore(context, tempFile, "test_export.mp4")

        assertNotNull("MediaStore URI should not be null after insertion", uri)
    }
}

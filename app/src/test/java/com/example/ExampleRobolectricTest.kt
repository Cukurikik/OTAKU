package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Otaku Hub", appName)
  }

  @Test
  fun `verify audio focus attributes configuration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    assertEquals(androidx.media3.common.C.USAGE_MEDIA, audioAttributes.usage)
    assertEquals(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE, audioAttributes.contentType)
  }

  @Test
  fun `verify subtitle parser with offset calculation`() {
    val vttContent = """
      WEBVTT

      00:00:01.000 --> 00:00:04.000
      Halo Dunia!
    """.trimIndent()

    val parsedDefault = com.example.ui.player.SubtitleParser.parse(vttContent, 0L)
    assertEquals(1, parsedDefault.size)
    assertEquals(1000L, parsedDefault[0].startMs)
    assertEquals(4000L, parsedDefault[0].endMs)
    assertEquals("Halo Dunia!", parsedDefault[0].text)

    // With +500ms offset (delay compensation: ΔT = ∑ (V_frame - A_sync)^2)
    val parsedWithOffset = com.example.ui.player.SubtitleParser.parse(vttContent, 500L)
    assertEquals(1, parsedWithOffset.size)
    assertEquals(1500L, parsedWithOffset[0].startMs)
    assertEquals(4500L, parsedWithOffset[0].endMs)

    // With applyOffset -500ms
    val adjusted = com.example.ui.player.SubtitleParser.applyOffset(parsedWithOffset, -500L)
    assertEquals(1000L, adjusted[0].startMs)
    assertEquals(4000L, adjusted[0].endMs)
  }
}

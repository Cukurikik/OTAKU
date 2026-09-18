package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.OtakuHubApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class ContentTest3 {

    @Test
    fun testAppInitialization() {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        println("SOURCES COUNT: " + app.repository.sources.size)
    }
}

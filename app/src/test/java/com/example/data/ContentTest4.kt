package com.example.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.OtakuHubApp
import com.example.ui.viewmodels.HomeViewModel
import com.example.ui.viewmodels.AppViewModelFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class ContentTest4 {

    @Test
    fun testHomeViewModel() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
        val viewModel = HomeViewModel(app.repository, SettingsManager(app))
        
        kotlinx.coroutines.delay(1000)
        
        println("SOURCES STATE SIZE: " + viewModel.sourcesState.value.size)
        println("ACTIVE SOURCE ID: " + viewModel.selectedSourceId.value)
        println("ITEMS SIZE: " + viewModel.items.value.size)
    }
}

package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.ContentRepository
import com.example.data.SettingsManager
import com.example.data.catalog.HubMode
import com.example.ui.viewmodels.HomeViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class HomeViewModelTest {
    @Test
    fun testViewModel() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<OtakuHubApp>()
            val settings = SettingsManager(app)
            settings.hubMode = HubMode.COMIC.name
            
            println("Creating HomeViewModel...")
            val viewModel = HomeViewModel(app.repository, settings)
            
            println("Initial state: isLoading=${viewModel.isLoading.value}, items=${viewModel.items.value.size}, sourcesState=${viewModel.sourcesState.value.size}")
            
            // Let the flows run
            delay(2000)
            
            println("After delay state: isLoading=${viewModel.isLoading.value}, items=${viewModel.items.value.size}, sourcesState=${viewModel.sourcesState.value.size}")
            println("Error message: ${viewModel.errorMessage.value}")
            
            viewModel.items.value.forEach { item ->
                println("Item: title=${item.title}, type=${item.type}, sourceId=${item.sourceId}, sourceName=${item.source}")
            }
        }
    }
}

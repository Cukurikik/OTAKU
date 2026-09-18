package com.example.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.OtakuHubApp
import com.example.data.sync.CrossDeviceResumeManager
import com.example.ui.navigation.DetailRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PlayerRoute
import com.example.ui.navigation.ReaderRoute
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ReaderScreen

@Composable
fun TvAppNavigation(app: OtakuHubApp) {
    val navController = rememberNavController()

    // TV Receiver Listener: Start real-time delta state listener on startup
    LaunchedEffect(Unit) {
        CrossDeviceResumeManager.startListening(app)
    }

    val resumeState by CrossDeviceResumeManager.incomingResumeState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            composable<HomeRoute> {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.HomeViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                TvHomeScreen(
                    viewModel = viewModel,
                    onItemClick = { itemId, sourceId ->
                        navController.navigate(DetailRoute(itemId, sourceId))
                    }
                )
            }
            
            composable<DetailRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<DetailRoute>()
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.DetailViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.viewmodels.DetailViewModel(
                                context = app,
                                itemId = args.itemId, 
                                sourceId = args.sourceId, 
                                repository = app.repository, 
                                libraryDao = app.database.libraryDao(), 
                                chapterDao = app.database.chapterDao(),
                                downloadDao = app.database.downloadDao(),
                                categoryDao = app.database.categoryDao(),
                                trackDao = app.database.trackDao()
                            ) as T
                        }
                    }
                )
                TvDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onReadClick = { chapterId, title ->
                        navController.navigate(ReaderRoute(chapterId, title, args.itemId, args.sourceId))
                    },
                    onPlayClick = { episodeId, title ->
                        navController.navigate(PlayerRoute(episodeId, title, args.itemId, args.sourceId))
                    }
                )
            }
            
            composable<PlayerRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<PlayerRoute>()
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.PlayerViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.viewmodels.PlayerViewModel(
                                args.episodeId, args.itemId, args.title, args.sourceId, 
                                app.repository, app.database.historyDao(), app.database.chapterDao(),
                                app.database.libraryDao(), app.database.trackDao(),
                                com.example.data.SettingsManager(app), app.database.downloadDao(), app,
                                initialSeekPosition = args.startPositionMs
                            ) as T
                        }
                    }
                )
                // Reuse standard player, as Media3 handles TV controls well
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    isTvMode = true
                )
            }
            
            composable<ReaderRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<ReaderRoute>()
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.ReaderViewModel>(
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return com.example.ui.viewmodels.ReaderViewModel(
                                args.chapterId, args.itemId, args.title, args.sourceId,
                                app.repository, app.database.historyDao(), app.database.chapterDao(),
                                app.database.pageDownloadDao(), app.database.libraryDao(), app.database.trackDao(),
                                com.example.data.SettingsManager(app), app.database.downloadDao(), app,
                                initialPage = args.initialPage
                            ) as T
                        }
                    }
                )
                // Reuse standard reader for TV
                ReaderScreen(
                    title = args.title,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onChapterChange = { newChapterId, newChapterTitle ->
                        navController.navigate(ReaderRoute(newChapterId, newChapterTitle, args.itemId, args.sourceId)) {
                            popUpTo<ReaderRoute> { inclusive = true }
                        }
                    }
                )
            }
        }

        // TV UI Trigger: Interactive resume prompt banner
        TvResumePromptBanner(
            resumeState = resumeState,
            onResumeClick = { state ->
                CrossDeviceResumeManager.consume(app, state)
                if (state.contentType.equals("COMIC", ignoreCase = true)) {
                    navController.navigate(
                        ReaderRoute(
                            chapterId = state.episodeOrChapterId,
                            title = state.episodeOrChapterTitle,
                            itemId = state.itemId,
                            sourceId = state.sourceId,
                            initialPage = state.pageIndex
                        )
                    )
                } else {
                    navController.navigate(
                        PlayerRoute(
                            episodeId = state.episodeOrChapterId,
                            title = state.episodeOrChapterTitle,
                            itemId = state.itemId,
                            sourceId = state.sourceId,
                            startPositionMs = state.positionMs
                        )
                    )
                }
            },
            onDismissClick = { state ->
                CrossDeviceResumeManager.dismiss(app, state)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

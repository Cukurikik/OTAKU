package com.example
import com.google.accompanist.permissions.ExperimentalPermissionsApi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.List
import com.example.ui.navigation.*
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import android.os.Build
import android.Manifest

import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import com.example.ui.navigation.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme



@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    private var navController: androidx.navigation.NavHostController? = null
    private var pendingNavigationIntent by mutableStateOf<android.content.Intent?>(null)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val currentRoute = navController?.currentDestination?.route
            if (currentRoute?.contains("PlayerRoute") == true) {
                val params = android.app.PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // PiP mode transition handled; system notifies listeners registered in Compose/PlayerScreen
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavigationIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict UI Mode & Leanback Isolation: Redirect TV devices directly to dedicated TvActivity
        val isTvMode = com.example.util.AppNavigationResolver(this).isTvMode()
        if (isTvMode) {
            val tvIntent = android.content.Intent(this, TvActivity::class.java).apply {
                intent?.extras?.let { putExtras(it) }
            }
            startActivity(tvIntent)
            finish()
            return
        }

        pendingNavigationIntent = intent
        enableEdgeToEdge()
        val app = application as OtakuHubApp
        com.example.data.AutoBackupWorker.schedule(applicationContext)
        
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            val isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

            val settingsManager = remember { com.example.data.SettingsManager(app) }
            val dynamicColor = settingsManager.isDynamicColor
            val darkModeSetting = settingsManager.darkMode // 0 = System, 1 = Light, 2 = Dark
            
            val isDarkTheme = when (darkModeSetting) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            // Request permissions for Local Content Source 
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { 
                val permissionsState = com.google.accompanist.permissions.rememberMultiplePermissionsState( 
                    permissions = listOf( 
                        android.Manifest.permission.READ_MEDIA_VIDEO, 
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) 
                ) 
                LaunchedEffect(Unit) { 
                    if (!permissionsState.allPermissionsGranted) { 
                        permissionsState.launchMultiplePermissionRequest() 
                    } 
                } 
            } else { 
                val permissionState = rememberPermissionState(android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                LaunchedEffect(Unit) { 
                    if (!permissionState.status.isGranted) { 
                        permissionState.launchPermissionRequest() 
                    } 
                } 
            }

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                // Interactive Cloudflare Turnstile Challenge Solver
                com.example.ui.components.ChallengeBottomSheet()

                val navController = rememberNavController()
                this@MainActivity.navController = navController
                val snackbarHostState = remember { SnackbarHostState() }

                    // Cross-Device Resume Listener: Listen for state updates across devices
                    LaunchedEffect(Unit) {
                        com.example.data.sync.CrossDeviceResumeManager.startListening(app)
                    }

                    val resumeState by com.example.data.sync.CrossDeviceResumeManager.incomingResumeState.collectAsStateWithLifecycle()

                    LaunchedEffect(pendingNavigationIntent) {
                        val currentPendingIntent = pendingNavigationIntent
                        if (currentPendingIntent != null) {
                            val action = currentPendingIntent.getStringExtra("action")
                            if (action == "open_detail") {
                                val itemId = currentPendingIntent.getStringExtra("itemId")
                                val sourceId = currentPendingIntent.getStringExtra("sourceId")
                                if (!itemId.isNullOrEmpty() && !sourceId.isNullOrEmpty()) {
                                    navController.navigate(DetailRoute(itemId = itemId, sourceId = sourceId))
                                }
                            } else if (action == "play_video") {
                                val episodeId = currentPendingIntent.getStringExtra("episodeId")
                                val sourceId = currentPendingIntent.getStringExtra("sourceId")
                                if (!episodeId.isNullOrEmpty() && !sourceId.isNullOrEmpty()) {
                                    navController.navigate(PlayerRoute(episodeId = episodeId, sourceId = sourceId, title = "Magnet Link", itemId = episodeId))
                                }
                            } else if (action == "open_updates") {
                                navController.navigate(UpdatesRoute)
                            } else if (action == "open_extensions") {
                                navController.navigate(ExtensionsRoute)
                            }
                            pendingNavigationIntent = null
                        }
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val isTopLevelDestination = currentRoute == "com.example.ui.navigation.HomeRoute" ||
                            currentRoute == "com.example.ui.navigation.LibraryRoute" ||
                            currentRoute == "com.example.ui.navigation.HistoryRoute" ||
                            currentRoute == "com.example.ui.navigation.UpdatesRoute" ||
                            currentRoute == "com.example.ui.navigation.ProfileRoute" || 
                            currentRoute == null

                    val showNavigationRail = isExpandedScreen && isTopLevelDestination
                    val showBottomBar = !isExpandedScreen && isTopLevelDestination

                    // Trigger Snackbar for Cross-Device Resume State
                    LaunchedEffect(resumeState, isTopLevelDestination) {
                        val state = resumeState
                        if (state != null && isTopLevelDestination) {
                            val isComic = state.contentType.equals("COMIC", ignoreCase = true)
                            val actionLabel = if (isComic) "Baca" else "Lanjutkan"
                            val message = if (isComic) {
                                "Lanjutkan membaca ${state.contentTitle} dari ${state.formattedTimestamp()}?"
                            } else {
                                "Lanjutkan menonton ${state.contentTitle} dari ${state.formattedTimestamp()}?"
                            }

                            val result = snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = actionLabel,
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                com.example.data.sync.CrossDeviceResumeManager.consume(app, state)
                                if (isComic) {
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
                            } else {
                                com.example.data.sync.CrossDeviceResumeManager.dismiss(app, state)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showNavigationRail) {
                            NavigationRail(
                                modifier = Modifier.fillMaxHeight(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                NavigationRailItem(
                                    selected = currentRoute == "com.example.ui.navigation.HomeRoute" || currentRoute == null,
                                    onClick = { navController.navigate(HomeRoute) },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == "com.example.ui.navigation.UpdatesRoute",
                                    onClick = { navController.navigate(UpdatesRoute) },
                                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Updates") },
                                    label = { Text("Update") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == "com.example.ui.navigation.LibraryRoute",
                                    onClick = { navController.navigate(LibraryRoute) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Library") },
                                    label = { Text("Library") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == "com.example.ui.navigation.HistoryRoute",
                                    onClick = { navController.navigate(HistoryRoute) },
                                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                    label = { Text("Riwayat") }
                                )
                                NavigationRailItem(
                                    selected = currentRoute == "com.example.ui.navigation.ProfileRoute",
                                    onClick = { navController.navigate(ProfileRoute) },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                    label = { Text("Profile") }
                                )
                            }
                        }

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (showBottomBar) {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = currentRoute == "com.example.ui.navigation.HomeRoute" || currentRoute == null,
                                            onClick = { navController.navigate(HomeRoute) },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                            label = { Text("Home") }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == "com.example.ui.navigation.UpdatesRoute",
                                            onClick = { navController.navigate(UpdatesRoute) },
                                            icon = { Icon(Icons.Default.Notifications, contentDescription = "Updates") },
                                            label = { Text("Update") }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == "com.example.ui.navigation.LibraryRoute",
                                            onClick = { navController.navigate(LibraryRoute) },
                                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Library") },
                                            label = { Text("Library") }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == "com.example.ui.navigation.HistoryRoute",
                                            onClick = { navController.navigate(HistoryRoute) },
                                            icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                            label = { Text("Riwayat") }
                                        )
                                        NavigationBarItem(
                                            selected = currentRoute == "com.example.ui.navigation.ProfileRoute",
                                            onClick = { navController.navigate(ProfileRoute) },
                                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                            label = { Text("Profile") }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = HomeRoute,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                        composable<HomeRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.HomeViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            HomeScreen(
                                viewModel = viewModel,
                                onItemClick = { itemId, sourceId -> 
                                    if (itemId.startsWith("error_cloudflare|")) {
                                        navController.navigate(com.example.ui.navigation.CloudflareBypassRoute(itemId.substringAfter("|")))
                                    } else {
                                        navController.navigate(DetailRoute(itemId, sourceId))
                                    }
                                },
                                onResumeClick = { historyItem ->
                                    if (historyItem.lastChapterOrEpisode.isNotBlank()) {
                                        when (historyItem.type) {
                                            com.example.data.models.ContentType.ANIME,
                                            com.example.data.models.ContentType.DRAMA -> {
                                                navController.navigate(
                                                    com.example.ui.navigation.PlayerRoute(
                                                        episodeId = historyItem.lastChapterOrEpisode,
                                                        title = historyItem.title,
                                                        itemId = historyItem.id,
                                                        sourceId = historyItem.sourceId
                                                    )
                                                )
                                            }
                                            com.example.data.models.ContentType.COMIC -> {
                                                navController.navigate(
                                                    com.example.ui.navigation.ReaderRoute(
                                                        chapterId = historyItem.lastChapterOrEpisode,
                                                        title = historyItem.title,
                                                        itemId = historyItem.id,
                                                        sourceId = historyItem.sourceId
                                                    )
                                                )
                                            }
                                            else -> {
                                                navController.navigate(DetailRoute(historyItem.id, historyItem.sourceId))
                                            }
                                        }
                                    } else {
                                        navController.navigate(DetailRoute(historyItem.id, historyItem.sourceId))
                                    }
                                }
                            )
                        }
                        composable<LibraryRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.LibraryViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            LibraryScreen(viewModel = viewModel, onItemClick = { itemId, sourceId -> 
                                    if (itemId.startsWith("error_cloudflare|")) {
                                        navController.navigate(com.example.ui.navigation.CloudflareBypassRoute(itemId.substringAfter("|")))
                                    } else {
                                        navController.navigate(DetailRoute(itemId, sourceId))
                                    }
                                })
                        }
                        composable<UpdatesRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.UpdatesViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            UpdatesScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { sourceId, mangaId ->
                                    navController.navigate(DetailRoute(itemId = mangaId, sourceId = sourceId))
                                }
                            )
                        }
                        composable<HistoryRoute> {
                            val scope = rememberCoroutineScope()
                            HistoryScreen(
                                historyFlow = app.database.historyDao().getAllHistory(com.example.data.ProfileManager.currentProfileIdValue),
                                onDelete = { id -> 
                                    scope.launch { app.database.historyDao().deleteHistoryById(com.example.data.ProfileManager.currentProfileIdValue, id) } 
                                },
                                onDeleteAll = { 
                                    scope.launch { app.database.historyDao().deleteAllHistory(com.example.data.ProfileManager.currentProfileIdValue) } 
                                },
                                onResume = { itemId, sourceId -> 
                                    navController.navigate(DetailRoute(itemId, sourceId)) 
                                },
                                onBack = { navController.navigate(HomeRoute) }
                            )
                        }
                        composable<com.example.ui.navigation.CloudflareBypassRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<com.example.ui.navigation.CloudflareBypassRoute>()
                            com.example.ui.screens.CloudflareBypassScreen(
                                url = route.url,
                                onSuccess = { navController.popBackStack() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<DownloadQueueRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.DownloadViewModel>(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        return com.example.ui.viewmodels.DownloadViewModel(app.database.downloadDao(), app.database.pageDownloadDao(), app.database.libraryDao(), app.database.chapterDao(), app.repository) as T
                                    }
                                }
                            )
                            com.example.ui.download.DownloadDashboardScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenMedia = { entity, isVideo ->
                                    if (isVideo) {
                                        navController.navigate(
                                            PlayerRoute(
                                                episodeId = entity.chapterId,
                                                title = entity.mangaId,
                                                itemId = entity.mangaId,
                                                sourceId = entity.sourceId
                                            )
                                        )
                                    } else {
                                        navController.navigate(
                                            ReaderRoute(
                                                chapterId = entity.chapterId,
                                                title = entity.mangaId,
                                                itemId = entity.mangaId,
                                                sourceId = entity.sourceId
                                            )
                                        )
                                    }
                                }
                            )
                        }
                        composable<ProfileRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.ProfileViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            ProfileScreen(
                                viewModel = viewModel,
                                onManageExtensions = {
                                    navController.navigate(ExtensionsRoute)
                                },
                                onLanShare = {
                                    navController.navigate(LanShareRoute)
                                },
                                onDownloadQueue = {
                                    navController.navigate(DownloadQueueRoute)
                                },
                                onMigrateSource = {
                                    navController.navigate(MigrationRoute)
                                },
                                onOpenSettings = {
                                    navController.navigate(SettingsRoute)
                                },
                                onOpenStatistics = {
                                    navController.navigate(StatisticsRoute)
                                }
                            )
                        }
                        composable<StatisticsRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.StatisticsViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            StatisticsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<MigrationRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.MigrationViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            com.example.ui.screens.MigrationScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<SettingsRoute> {
                            com.example.ui.screens.SettingsScreen(
                                onNavigateToBackup = { navController.navigate(BackupSettingsRoute) },
                                onNavigateToStatistics = { navController.navigate(StatisticsRoute) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<BackupSettingsRoute> {
                            com.example.ui.screens.BackupSettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToCloudSync = { navController.navigate(CloudSyncRoute) }
                            )
                        }
                        composable<LanShareRoute> {
                            val viewModel = remember { com.example.ui.screens.LanShareViewModel(app) }
                            com.example.ui.screens.LanShareScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onStreamVideo = { url, title ->
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(url), "video/mp4")
                                        putExtra("title", title)
                                    }
                                    app.startActivity(intent.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
                                }
                            )
                        }
                        composable<CloudSyncRoute> {
                            com.example.ui.screens.CloudSyncScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<ExtensionsRoute> {
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.ExtensionsViewModel>(factory = com.example.ui.viewmodels.AppViewModelFactory(app))
                            ExtensionsScreen(
                                viewModel = viewModel,
                            )
                        }
                        composable<DetailRoute> { backStackEntry ->
                            val args = backStackEntry.toRoute<DetailRoute>()
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.DetailViewModel>(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        return com.example.ui.viewmodels.DetailViewModel(app, args.itemId, args.sourceId, app.repository, app.database.libraryDao(), app.database.chapterDao(), app.database.downloadDao(), app.database.categoryDao(), app.database.trackDao()) as T
                                    }
                                }
                            )
                            com.example.ui.screens.DetailScreen(
                                viewModel = viewModel,
                                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                                onBack = { navController.popBackStack() },
                                onReadChapter = { chapterId, chapterTitle ->
                                    navController.navigate(ReaderRoute(chapterId, chapterTitle, args.itemId, args.sourceId))
                                },
                                onWatchEpisode = { episodeId, episodeTitle ->
                                    navController.navigate(PlayerRoute(episodeId, episodeTitle, args.itemId, args.sourceId))
                                },
                                onAlternativeSourceClick = { altId, altSourceId ->
                                    navController.navigate(DetailRoute(altId, altSourceId))
                                }
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
                        composable<PlayerRoute> { backStackEntry ->
                            val args = backStackEntry.toRoute<PlayerRoute>()
                            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.example.ui.viewmodels.PlayerViewModel>(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        return com.example.ui.viewmodels.PlayerViewModel(
                                            args.episodeId, 
                                            args.itemId, 
                                            args.title, 
                                            args.sourceId, 
                                            app.repository, 
                                            app.database.historyDao(), 
                                            app.database.chapterDao(), 
                                            app.database.libraryDao(), 
                                            app.database.trackDao(), 
                                            com.example.data.SettingsManager(app), 
                                            app.database.downloadDao(), 
                                            app,
                                            initialSeekPosition = args.startPositionMs
                                        ) as T
                                    }
                                }
                            )
                            PlayerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                isTvMode = false
                            )
                        }
                    }
                } // End of Scaffold
            } // End of Row
        } // MyApplicationTheme
    } // setContent
} // onCreate
} // class MainActivity

package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.example.ui.viewmodels.ReaderState
import com.example.ui.viewmodels.ReaderViewModel
import com.example.data.SettingsManager
import androidx.compose.foundation.background


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onChapterChange: (String, String) -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    

    
    val state by viewModel.state.collectAsState()
    val isSearchingMigration by viewModel.isSearchingMigration.collectAsState()
    val migrationCandidates by viewModel.migrationCandidates.collectAsState()
    val showMigrationDialog by viewModel.showMigrationDialog.collectAsState()
    val isPerformingMigration by viewModel.isPerformingMigration.collectAsState()
    val listState = rememberLazyListState()
    var isPagedMode by remember { mutableStateOf(settingsManager.isPagedMode) }
    var isRtl by remember { mutableStateOf(settingsManager.isRtl) }
    var cropScale by remember { mutableStateOf(settingsManager.cropScale) }
    var isDoublePageMode by remember { mutableStateOf(settingsManager.isDoublePageMode) }
    var separateCoverPage by remember { mutableStateOf(settingsManager.separateCoverPage) }
    var cropBorders by remember { mutableStateOf(settingsManager.cropBorders) }
    var enableSmartPanel by remember { mutableStateOf(settingsManager.enableSmartPanel) }
    var orientationLock by remember { mutableStateOf(settingsManager.orientationLock) }

    DisposableEffect(orientationLock) {
        activity?.requestedOrientation = when(orientationLock) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChapterMenu by remember { mutableStateOf(false) }
    var backgroundColor by remember { mutableStateOf(settingsManager.backgroundColor) }
    var customBrightness by remember { mutableStateOf(settingsManager.customBrightness) }
    // Zoom variables for Webtoon Mode
    var webtoonScale by remember { mutableFloatStateOf(1f) }
    var webtoonOffsetX by remember { mutableFloatStateOf(0f) }
    var webtoonOffsetY by remember { mutableFloatStateOf(0f) }
    var webtoonSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val scope = rememberCoroutineScope()

    DisposableEffect(customBrightness) {
        if (activity != null) {
            val window = activity.window
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (customBrightness >= 0f) customBrightness else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val layoutParams = window.attributes
                layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }
    }

    // Only scroll once when success state is first emitted with an initial page
    var hasScrolledToInitial by remember { mutableStateOf(false) }
    
    // For Paged Mode
    val pageCount = (state as? ReaderState.Success)?.pages?.let { pages ->
        if (!isDoublePageMode) pages.size
        else if (!separateCoverPage) (pages.size + 1) / 2
        else 1 + (pages.size / 2)
    } ?: 0
    val pagerState = rememberPagerState(pageCount = { pageCount })

    LaunchedEffect(state) {
        if (state is ReaderState.Success && !hasScrolledToInitial) {
            val successState = state as ReaderState.Success
            if (successState.initialPage > 0 && successState.initialPage < successState.pages.size) {
                listState.scrollToItem(successState.initialPage)
                val targetPage = if (isDoublePageMode) {
                    if (separateCoverPage) {
                        if (successState.initialPage == 0) 0 else 1 + (successState.initialPage - 1) / 2
                    } else {
                        successState.initialPage / 2
                    }
                } else {
                    successState.initialPage
                }
                pagerState.scrollToPage(targetPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            }
            hasScrolledToInitial = true
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, pagerState.currentPage, isPagedMode, isDoublePageMode, separateCoverPage) {
        if (state is ReaderState.Success) {
            val pages = (state as ReaderState.Success).pages
            val totalPages = pages.size
            if (totalPages > 0) {
                val currentIndex = if (isPagedMode) {
                    if (isDoublePageMode) {
                        if (separateCoverPage) {
                            if (pagerState.currentPage == 0) 0 else 1 + (pagerState.currentPage - 1) * 2
                        } else {
                            pagerState.currentPage * 2
                        }
                    } else {
                        pagerState.currentPage
                    }
                } else {
                    listState.firstVisibleItemIndex
                }
                viewModel.updateLastPageRead(currentIndex, totalPages)
                viewModel.updateActiveViewport(currentIndex, totalPages)
            }
        }
    }

    // Memory & Decoding Optimization for Webtoon / Reader Mode (Hardware Bitmap & Screen Downsampling)
    val isLowRamDevice = remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }
    val targetBitmapConfig = remember(isLowRamDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isLowRamDevice) {
            Bitmap.Config.HARDWARE
        } else {
            Bitmap.Config.RGB_565
        }
    }
    val screenWidth = remember(context) {
        context.resources.displayMetrics.widthPixels.coerceAtLeast(720)
    }
    val webtoonImageSize = remember(screenWidth) {
        Size(Dimension.Pixels(screenWidth), Dimension.Undefined)
    }

    // Disk Cache Prefetching (Prefetch 2 pages ahead during scroll)
    LaunchedEffect(state, listState, isPagedMode, pagerState) {
        if (state is ReaderState.Success) {
            val pages = (state as ReaderState.Success).pages
            if (isPagedMode) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { pageIndex ->
                        val nextIndices = if (isDoublePageMode) {
                            listOf(pageIndex * 2 + 2, pageIndex * 2 + 3)
                        } else {
                            listOf(pageIndex + 1, pageIndex + 2)
                        }
                        for (idx in nextIndices) {
                            if (idx in pages.indices) {
                                val prefetchRequest = ImageRequest.Builder(context)
                                    .data(pages[idx].url)
                                    .bitmapConfig(targetBitmapConfig)
                                    .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                    .size(webtoonImageSize)
                                    .precision(Precision.INEXACT)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .build()
                                context.imageLoader.enqueue(prefetchRequest)
                            }
                        }
                    }
            } else {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { currentIndex ->
                        for (step in 1..2) {
                            val prefetchIndex = currentIndex + step
                            if (prefetchIndex in pages.indices) {
                                val prefetchRequest = ImageRequest.Builder(context)
                                    .data(pages[prefetchIndex].url)
                                    .bitmapConfig(targetBitmapConfig)
                                    .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                    .size(webtoonImageSize)
                                    .precision(Precision.INEXACT)
                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .build()
                                context.imageLoader.enqueue(prefetchRequest)
                            }
                        }
                    }
            }
        }
    }

    val bgColor = when (backgroundColor) {
        "BLACK" -> Color.Black
        "PAPER" -> Color(0xFFE8DCC8)
        "SEPIA" -> Color(0xFFF4ECD8)
        "WHITE" -> Color.White
        else -> Color.Black
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (state is ReaderState.Success && (state as ReaderState.Success).isOffline) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Text("OFFLINE") }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showChapterMenu = true }) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "Daftar Chapter")
                    }
                    IconButton(onClick = { 
                        isPagedMode = !isPagedMode 
                        settingsManager.isPagedMode = isPagedMode
                    }) {
                        Icon(
                            if (isPagedMode) Icons.Default.ViewDay else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Read Mode"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(bgColor)) {
            when (val currentState = state) {
                is ReaderState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReaderState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tidak ada halaman yang ditemukan pada sumber ini.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { 
                                hasScrolledToInitial = false
                                viewModel.loadContent() 
                            }) {
                                Text("Coba Lagi")
                            }
                            OutlinedButton(onClick = { 
                                viewModel.openMigrationDialog()
                            }) {
                                Text("Pindah Sumber")
                            }
                        }
                    }
                }
                is ReaderState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: ${currentState.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { 
                                hasScrolledToInitial = false
                                viewModel.loadContent() 
                            }) {
                                Text("Coba Lagi")
                            }
                            OutlinedButton(onClick = { 
                                viewModel.openMigrationDialog()
                            }) {
                                Text("Pindah Sumber")
                            }
                        }
                    }
                }
                is ReaderState.Success -> {
                    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    val scale = when (cropScale) {
                        "CROP" -> ContentScale.Crop
                        "FILL_WIDTH" -> ContentScale.FillWidth
                        else -> ContentScale.Fit
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        if (isPagedMode) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val width = size.width
                                        scope.launch {
                                            when {
                                                offset.x < width * 0.3f -> {
                                                    val target = pagerState.currentPage - 1
                                                    if (target >= 0) pagerState.scrollToPage(target)
                                                }
                                                offset.x > width * 0.7f -> {
                                                    val target = pagerState.currentPage + 1
                                                    if (target < pagerState.pageCount) pagerState.scrollToPage(target)
                                                }
                                                else -> showSettingsDialog = true // tap center toggles settings
                                            }
                                        }
                                    }
                                }
                            ) { pageIndex ->
                                if (isDoublePageMode) {
                                    val spread = calculateSpread(
                                        spreadIndex = pageIndex,
                                        totalPages = currentState.pages.size,
                                        isDoublePage = isDoublePageMode,
                                        separateCover = separateCoverPage,
                                        isRtl = isRtl
                                    )

                                    if (spread.isCoverSpread || (spread.leftPageIndex != null && spread.rightPageIndex == null) || (spread.leftPageIndex == null && spread.rightPageIndex != null)) {
                                        val singleIndex = spread.leftPageIndex ?: spread.rightPageIndex ?: 0
                                        val singlePage = currentState.pages.getOrNull(singleIndex)
                                        if (singlePage != null) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ZoomableImage(
                                                    model = ImageRequest.Builder(context).data(singlePage.url)
                                                        .bitmapConfig(targetBitmapConfig)
                                                        .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                                        .precision(Precision.INEXACT)
                                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .apply {
                                                            if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                        }.build(),
                                                    contentDescription = if (spread.isCoverSpread) "Manga Cover Page" else "Manga Page ${singleIndex + 1}",
                                                    contentScale = scale,
                                                    modifier = Modifier.fillMaxHeight().wrapContentWidth(),
                                        enableSmartPanel = enableSmartPanel
                                                )
                                            }
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            val leftPage = if (spread.leftPageIndex != null) currentState.pages.getOrNull(spread.leftPageIndex) else null
                                            val rightPage = if (spread.rightPageIndex != null) currentState.pages.getOrNull(spread.rightPageIndex) else null
                                            
                                            if (leftPage != null) {
                                                ZoomableImage(
                                                    model = ImageRequest.Builder(context).data(leftPage.url)
                                                        .bitmapConfig(targetBitmapConfig)
                                                        .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                                        .precision(Precision.INEXACT)
                                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .apply {
                                                            if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                        }.build(),
                                                    contentDescription = "Manga Page ${(spread.leftPageIndex ?: 0) + 1}",
                                                    contentScale = scale,
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                        enableSmartPanel = enableSmartPanel
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                            if (rightPage != null) {
                                                ZoomableImage(
                                                    model = ImageRequest.Builder(context).data(rightPage.url)
                                                        .bitmapConfig(targetBitmapConfig)
                                                        .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                                        .precision(Precision.INEXACT)
                                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .apply {
                                                            if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                        }.build(),
                                                    contentDescription = "Manga Page ${(spread.rightPageIndex ?: 0) + 1}",
                                                    contentScale = scale,
                                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                        enableSmartPanel = enableSmartPanel
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    val page = currentState.pages[pageIndex]
                                    ZoomableImage(
                                        model = ImageRequest.Builder(context).data(page.url)
                                            .bitmapConfig(targetBitmapConfig)
                                            .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                            .precision(Precision.INEXACT)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .apply {
                                                if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                            }.build(),
                                        contentDescription = "Manga Page ${pageIndex + 1}",
                                        contentScale = scale,
                                        modifier = Modifier.fillMaxSize(),
                                        enableSmartPanel = enableSmartPanel
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onSizeChanged { webtoonSize = it }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown()
                                            do {
                                                val event = awaitPointerEvent()
                                                val zoom = event.calculateZoom()
                                                val pan = event.calculatePan()
                                                
                                                if (event.changes.size > 1) {
                                                    // Pinch to zoom
                                                    webtoonScale = (webtoonScale * zoom).coerceIn(1f, 5f)
                                                    if (webtoonScale > 1f) {
                                                        val maxX = (webtoonSize.width * (webtoonScale - 1)) / 2
                                                        val maxY = (webtoonSize.height * (webtoonScale - 1)) / 2
                                                        webtoonOffsetX = (webtoonOffsetX + pan.x * webtoonScale).coerceIn(-maxX, maxX)
                                                        webtoonOffsetY = (webtoonOffsetY + pan.y * webtoonScale).coerceIn(-maxY, maxY)
                                                    } else {
                                                        webtoonOffsetX = 0f
                                                        webtoonOffsetY = 0f
                                                    }
                                                    event.changes.forEach { it.consume() }
                                                } else if (webtoonScale > 1f) {
                                                    // Single finger pan when zoomed in
                                                    val maxX = (webtoonSize.width * (webtoonScale - 1)) / 2
                                                    val maxY = (webtoonSize.height * (webtoonScale - 1)) / 2
                                                    
                                                    val newOffsetX = (webtoonOffsetX + pan.x * webtoonScale).coerceIn(-maxX, maxX)
                                                    val newOffsetY = (webtoonOffsetY + pan.y * webtoonScale).coerceIn(-maxY, maxY)
                                                    
                                                    // Check if hit edges to allow normal scroll to take over
                                                    val hitVerticalEdge = newOffsetY == maxY || newOffsetY == -maxY
                                                    
                                                    webtoonOffsetX = newOffsetX
                                                    webtoonOffsetY = newOffsetY
                                                    
                                                    if (!hitVerticalEdge) {
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                        }
                                    }
                            ) {
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.Top,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = webtoonScale
                                            scaleY = webtoonScale
                                            translationX = webtoonOffsetX
                                            translationY = webtoonOffsetY
                                        }
                                ) {
                                    if (currentState.prevChapter != null) {
                                        item {
                                            Button(
                                                onClick = { onChapterChange(currentState.prevChapter.chapterId, currentState.prevChapter.name) },
                                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                                            ) {
                                                Text("Previous Chapter")
                                            }
                                        }
                                    }

                                    itemsIndexed(currentState.pages, key = { index, _ -> index }) { index, page ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 250.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(page.url)
                                                    .bitmapConfig(targetBitmapConfig)
                                                    .allowHardware(targetBitmapConfig == Bitmap.Config.HARDWARE)
                                                    .size(webtoonImageSize)
                                                    .precision(Precision.INEXACT)
                                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .apply {
                                                        if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                    }
                                                    .build(),
                                                contentDescription = "Webtoon Page ${index + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .wrapContentHeight()
                                            )
                                        }
                                    }

                                    if (currentState.nextChapter != null) {

                                    item {
                                        Button(
                                            onClick = { onChapterChange(currentState.nextChapter.chapterId, currentState.nextChapter.name) },
                                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                                        ) {
                                            Text("Next Chapter: ${currentState.nextChapter.name}")
                                        }
                                    }
                                } else {
                                    item {
                                        Text(
                                            "No more chapters",
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
}
                            }
                        }
                    }
                }
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Pengaturan Reader") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Arah Baca RTL (Manga)")
                            Switch(
                                checked = isRtl,
                                onCheckedChange = { checked ->
                                    isRtl = checked
                                    settingsManager.isRtl = checked
                                }
                            )
                        }

                        if (isPagedMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mode Dua Halaman")
                                    Text(
                                        "Tampilkan dua halaman berdampingan untuk lanskap/tablet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isDoublePageMode,
                                    onCheckedChange = { checked ->
                                        isDoublePageMode = checked
                                        settingsManager.isDoublePageMode = checked
                                    }
                                )
                            }

                            if (isDoublePageMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Pisahkan Halaman Sampul")
                                        Text(
                                            "Sampul pertama ditampilkan sendiri, halaman berikutnya dipasangkan",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = separateCoverPage,
                                        onCheckedChange = { checked ->
                                            separateCoverPage = checked
                                            settingsManager.separateCoverPage = checked
                                        }
                                    )
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Potong Tepi Putih (Auto-Crop)")
                            Switch(
                                checked = cropBorders,
                                onCheckedChange = { checked ->
                                    cropBorders = checked
                                    settingsManager.cropBorders = checked
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Smart Panel (Guided View)")
                                Text("Double-tap navigates comic panels", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = enableSmartPanel,
                                onCheckedChange = { checked ->
                                    enableSmartPanel = checked
                                    settingsManager.enableSmartPanel = checked
                                }
                            )
                        }
                        
                        HorizontalDivider()
                        
                        Text("Orientasi Layar", style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(-1 to "Auto", 1 to "Portrait", 0 to "Landscape").forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            orientationLock = value
                                            settingsManager.orientationLock = value
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = orientationLock == value,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                        
                        HorizontalDivider()
                        
                        Text("Skala Gambar", style = MaterialTheme.typography.titleSmall)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("FIT" to "Fit Screen", "FILL_WIDTH" to "Fill Width", "CROP" to "Crop / Fill").forEach { (value, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            cropScale = value
                                            settingsManager.cropScale = value
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = cropScale == value,
                                        onClick = {
                                            cropScale = value
                                            settingsManager.cropScale = value
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("Warna Latar Belakang", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("BLACK", "PAPER", "SEPIA", "WHITE").forEach { color ->
                                val boxColor = when (color) {
                                    "BLACK" -> Color.Black
                                    "PAPER" -> Color(0xFFE8DCC8)
                                    "SEPIA" -> Color(0xFFF4ECD8)
                                    "WHITE" -> Color.White
                                    else -> Color.Black
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(boxColor, shape = androidx.compose.foundation.shape.CircleShape)
                                        .clickable {
                                            backgroundColor = color
                                            settingsManager.backgroundColor = color
                                        }
                                ) {
                                    if (backgroundColor == color) {
                                        Text(
                                            "✓",
                                            color = if (color == "WHITE" || color == "SEPIA") Color.Black else Color.White,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        
                        Text("Kecerahan Layar", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("-")
                            Slider(
                                value = if (customBrightness < 0f) 0.5f else customBrightness,
                                onValueChange = { 
                                    customBrightness = it 
                                    settingsManager.customBrightness = it
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text("+")
                        }
                        Button(
                            onClick = { 
                                customBrightness = -1f 
                                settingsManager.customBrightness = -1f
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gunakan Kecerahan Sistem")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Selesai")
                    }
                }
            )
        }

        if (showMigrationDialog) {
            com.example.ui.components.OneTapMigrationDialog(
                currentTitle = title,
                currentSourceId = viewModel.activeSourceId,
                candidates = migrationCandidates,
                isLoading = isSearchingMigration,
                isMigrating = isPerformingMigration,
                onDismissRequest = viewModel::dismissMigrationDialog,
                onConfirmMigration = viewModel::executeOneTapMigration,
                onRetrySearch = viewModel::triggerAutoMigrationSearch
            )
        }
    }
}

data class SpreadPages(
    val leftPageIndex: Int?,
    val rightPageIndex: Int?,
    val isCoverSpread: Boolean
)

/**
 * Calculates page spread indices for Paged Mode.
 * Supports Dual-Page Spread and Separate Cover Page (first page displayed solo).
 */
fun calculateSpread(
    spreadIndex: Int,
    totalPages: Int,
    isDoublePage: Boolean,
    separateCover: Boolean,
    isRtl: Boolean
): SpreadPages {
    if (!isDoublePage) {
        return SpreadPages(leftPageIndex = spreadIndex, rightPageIndex = null, isCoverSpread = false)
    }
    if (separateCover) {
        if (spreadIndex == 0) {
            return SpreadPages(leftPageIndex = 0, rightPageIndex = null, isCoverSpread = true)
        }
        val firstIndex = 1 + (spreadIndex - 1) * 2
        val secondIndex = firstIndex + 1
        val p1 = if (firstIndex < totalPages) firstIndex else null
        val p2 = if (secondIndex < totalPages) secondIndex else null
        return if (isRtl) {
            SpreadPages(leftPageIndex = p2, rightPageIndex = p1, isCoverSpread = false)
        } else {
            SpreadPages(leftPageIndex = p1, rightPageIndex = p2, isCoverSpread = false)
        }
    } else {
        val firstIndex = spreadIndex * 2
        val secondIndex = firstIndex + 1
        val p1 = if (firstIndex < totalPages) firstIndex else null
        val p2 = if (secondIndex < totalPages) secondIndex else null
        return if (isRtl) {
            SpreadPages(leftPageIndex = p2, rightPageIndex = p1, isCoverSpread = false)
        } else {
            SpreadPages(leftPageIndex = p1, rightPageIndex = p2, isCoverSpread = false)
        }
    }
}


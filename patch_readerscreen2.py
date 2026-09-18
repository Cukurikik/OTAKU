import re

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

# 1. Add cropBorders and orientationLock state
state_old = '''    var cropScale by remember { mutableStateOf(settingsManager.cropScale) }
    var isDoublePageMode by remember { mutableStateOf(settingsManager.isDoublePageMode) }'''
state_new = '''    var cropScale by remember { mutableStateOf(settingsManager.cropScale) }
    var isDoublePageMode by remember { mutableStateOf(settingsManager.isDoublePageMode) }
    var cropBorders by remember { mutableStateOf(settingsManager.cropBorders) }
    var orientationLock by remember { mutableStateOf(settingsManager.orientationLock) }'''
content = content.replace(state_old, state_new)

# 2. Add orientation lock effect
effect_old = '''    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }'''
effect_new = '''    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    DisposableEffect(orientationLock) {
        activity?.requestedOrientation = when(orientationLock) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }'''
content = content.replace(effect_old, effect_new)

# 3. Add Transformation to preloader
preloader_old = '''                        val request = ImageRequest.Builder(context)
                            .data(pages[i].url)
                            .memoryCachePolicy(if (isPagedMode) coil.request.CachePolicy.ENABLED else coil.request.CachePolicy.DISABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()'''
preloader_new = '''                        val request = ImageRequest.Builder(context)
                            .data(pages[i].url)
                            .memoryCachePolicy(if (isPagedMode) coil.request.CachePolicy.ENABLED else coil.request.CachePolicy.DISABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .apply {
                                if (cropBorders) {
                                    transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                }
                            }
                            .build()'''
content = content.replace(preloader_old, preloader_new)

# 4. Apply Transformation to ZoomableImage
left_page_old = '''                                        if (leftPage != null) {
                                            ZoomableImage(
                                                model = leftPage.url,'''
left_page_new = '''                                        if (leftPage != null) {
                                            ZoomableImage(
                                                model = ImageRequest.Builder(context).data(leftPage.url).apply {
                                                    if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                }.build(),'''
content = content.replace(left_page_old, left_page_new)

right_page_old = '''                                        if (rightPage != null) {
                                            ZoomableImage(
                                                model = rightPage.url,'''
right_page_new = '''                                        if (rightPage != null) {
                                            ZoomableImage(
                                                model = ImageRequest.Builder(context).data(rightPage.url).apply {
                                                    if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                }.build(),'''
content = content.replace(right_page_old, right_page_new)

single_page_old = '''                                    ZoomableImage(
                                        model = page.url,'''
single_page_new = '''                                    ZoomableImage(
                                        model = ImageRequest.Builder(context).data(page.url).apply {
                                            if (cropBorders) transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                        }.build(),'''
content = content.replace(single_page_old, single_page_new)

# 5. Apply Transformation to AsyncImage
async_image_old = '''                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(page.url)
                                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED) // Disable RAM cache for long-scroll to prevent OOM
                                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .crossfade(true)
                                                    .listener(
                                                        onSuccess = { _, _ ->
                                                            isLoading = false
                                                            isError = false
                                                        },
                                                        onError = { _, _ ->
                                                            isLoading = false
                                                            isError = true
                                                        }
                                                    )
                                                    .build(),'''
async_image_new = '''                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(page.url)
                                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED) // Disable RAM cache for long-scroll to prevent OOM
                                                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                    .crossfade(true)
                                                    .apply {
                                                        if (cropBorders) {
                                                            transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                                        }
                                                    }
                                                    .listener(
                                                        onSuccess = { _, _ ->
                                                            isLoading = false
                                                            isError = false
                                                        },
                                                        onError = { _, _ ->
                                                            isLoading = false
                                                            isError = true
                                                        }
                                                    )
                                                    .build(),'''
content = content.replace(async_image_old, async_image_new)

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

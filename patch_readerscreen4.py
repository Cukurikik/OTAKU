import re

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

preload_old = '''                // Preload next 5 pages
                val endIndex = minOf(currentIndex + 5, totalPages - 1)
                for (i in currentIndex..endIndex) {
                    if (i >= 0 && i < totalPages) {
                        val request = ImageRequest.Builder(context)
                            .data(pages[i].url)
                            .memoryCachePolicy(if (isPagedMode) coil.request.CachePolicy.ENABLED else coil.request.CachePolicy.DISABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .apply {
                                if (cropBorders) {
                                    transformations(com.example.ui.utils.WhiteBorderCropTransformation())
                                }
                            }
                            .build()
                        context.imageLoader.enqueue(request)
                    }
                }'''

content = content.replace(preload_old, '')

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

import re

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

bad_block = '''    DisposableEffect(orientationLock) {
        activity?.requestedOrientation = when(orientationLock) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            0 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }'''

content = content.replace(bad_block, '')

# Now insert it AFTER the state definition
target = '''    var cropScale by remember { mutableStateOf(settingsManager.cropScale) }
    var isDoublePageMode by remember { mutableStateOf(settingsManager.isDoublePageMode) }
    var cropBorders by remember { mutableStateOf(settingsManager.cropBorders) }
    var orientationLock by remember { mutableStateOf(settingsManager.orientationLock) }'''

content = content.replace(target, target + '\n\n' + bad_block)

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

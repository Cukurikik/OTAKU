import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# 1. Remove from ExoPlayerView
wrong_code = '''    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }'''
content = content.replace(wrong_code, '    val lifecycleOwner = LocalLifecycleOwner.current')

# 2. Add to PlayerScreen
target = '''    val isTv = isTvMode || (uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)'''
replacement = '''    val isTv = isTvMode || (uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }'''
content = content.replace(target, replacement)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)

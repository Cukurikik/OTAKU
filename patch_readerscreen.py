import re

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'r') as f:
    content = f.read()

launch_old = '''    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }'''

launch_new = '''    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }'''

content = content.replace(launch_old, launch_new)

with open('app/src/main/java/com/example/ui/screens/ReaderScreen.kt', 'w') as f:
    f.write(content)

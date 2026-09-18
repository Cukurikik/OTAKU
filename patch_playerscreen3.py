import re

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r') as f:
    content = f.read()

# Add LaunchedEffect to update currentUrl when initialUrl changes
launched_effect_initialUrl = '''
    LaunchedEffect(initialUrl) {
        if (currentUrl != initialUrl) {
            currentUrl = initialUrl
            currentQualities = qualities
            currentSubtitles = subtitles
            currentQualityLabel = qualities.find { it.url == initialUrl }?.qualityLabel ?: "Auto"
        }
    }
'''

target = 'var currentServerName by remember {'
content = content.replace(target, launched_effect_initialUrl + '\n    ' + target)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w') as f:
    f.write(content)

import re
import os

def fix_dao_calls(path):
    with open(path, 'r') as f:
        content = f.read()
    
    orig = content
    
    # Clean any weird "currentProfileIdValuecom"
    content = content.replace('currentProfileIdValuecom', 'currentProfileIdValue')
    content = content.replace('currentProfileIdValue.example', 'currentProfileIdValue, com.example')

    # Methods that take profileId as first arg
    methods = [
        'getAllHistory', 'getAllHistorySync', 'getHistoryItemSync', 'deleteHistoryById', 'deleteAllHistory',
        'observeLibraryEntry', 'getLibraryEntry', 'deleteLibraryEntry', 'updateUnreadCount', 'updateAllUnreadCounts', 'getAllLibrary', 'getAllEntriesSync',
        'getAllDownloads', 'getPendingDownloads', 'getAllDownloadsSync', 'getDownload',
        'getChapters', 'getChaptersList', 'getChapter', 'updateProgress', 'getUnreadCount', 'markAllChaptersAsRead', 'updateChapterReadStatus', 'getReadChaptersSourceIds', 'getAllChaptersSync', 'getAllChaptersFlow', 'getReadChaptersFlow', 'getReadChaptersSync'
    ]

    for m in methods:
        # Regex to find: daoName.methodName( args )
        # We need to insert ProfileManager.currentProfileIdValue, as the first arg.
        # But only if it's not already there.
        # Example: app.database.historyDao().getAllHistory() -> app.database.historyDao().getAllHistory(ProfileManager...)
        
        # We find occurrences of `.methodName(`
        # If the next characters are NOT `com.example.data.ProfileManager`, we insert it.
        pattern = r'\.' + m + r'\(\s*(?!com\.example\.data\.ProfileManager\.currentProfileIdValue)'
        replacement = '.' + m + '(com.example.data.ProfileManager.currentProfileIdValue'
        
        # If it originally had no args, it was `method()`. Now it's `method(ProfileManager...)`. We need to handle the comma.
        # It's easier to just use a function to do the replacement.
        
        def replacer(match):
            # Check what follows. If it's `)`, we just insert the value. If it's something else, we insert value + `, `
            # But regex lookahead already says it doesn't start with ProfileManager.
            rest_of_string = content[match.end():]
            if rest_of_string.startswith(')'):
                return '.' + m + '(com.example.data.ProfileManager.currentProfileIdValue'
            else:
                return '.' + m + '(com.example.data.ProfileManager.currentProfileIdValue, '
        
        # This is a bit tricky with re.sub because we need to know if there are arguments.
        # Let's use a simpler approach:
        # 1. Add comma space to ALL:
        content = re.sub(r'\.' + m + r'\(\s*(?!\s*\)|com\.example\.data\.ProfileManager\.currentProfileIdValue)', '.' + m + '(com.example.data.ProfileManager.currentProfileIdValue, ', content)
        # 2. Add without comma space to empty parens:
        content = re.sub(r'\.' + m + r'\(\s*\)', '.' + m + '(com.example.data.ProfileManager.currentProfileIdValue)', content)

    # Clean syntax errors in DetailViewModel and LibraryViewModel
    content = content.replace('System.currentTimeMillis())))', 'System.currentTimeMillis()))')
    content = content.replace('System.currentTimeMillis()))', 'System.currentTimeMillis())')

    if orig != content:
        with open(path, 'w') as f:
            f.write(content)
        print(f"Updated {path}")

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            fix_dao_calls(os.path.join(root, file))

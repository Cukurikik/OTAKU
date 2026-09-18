import os

def clean(path):
    with open(path, 'r') as f:
        c = f.read()
    orig = c

    # Fix the double ProfileManager insert
    c = c.replace('(com.example.data.ProfileManager.currentProfileIdValue, com.example.data.ProfileManager.currentProfileIdValue, ', '(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('(com.example.data.ProfileManager.currentProfileIdValue, com.example.data.ProfileManager.currentProfileIdValue)', '(com.example.data.ProfileManager.currentProfileIdValue)')

    # Fix repository.getChapters, source.getChapters, api.getLibraryEntry
    bad_prefixes = [
        'repository', 'source', 'api', 'scraper', 'ext'
    ]
    methods = [
        'getChapters', 'getLibraryEntry', 'getDownload'
    ]
    
    # Strip the ProfileManager argument from these bad calls.
    # It looks like: repository.getChapters(com.example.data.ProfileManager.currentProfileIdValue, itemId)
    # So we replace `.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ` with `.getChapters(`
    # But ONLY if it's NOT a DAO. Wait, we can just replace ALL `.getChapters(com.example.data.ProfileManager.currentProfileIdValue, `
    # with `.getChapters(` UNLESS it's `chapterDao.getChapters(`.
    # Same for others.
    
    # Since we can just write specific string replacements:
    c = c.replace('repository.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'repository.getChapters(')
    c = c.replace('source.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'source.getChapters(')
    c = c.replace('api.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, ', 'api.getLibraryEntry(')
    c = c.replace('getDownload(com.example.data.ProfileManager.currentProfileIdValue, ', 'getDownload(')
    
    # Actually wait, `downloadDao.getDownload` DOES need it!
    c = c.replace('downloadDao.getDownload(', 'downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, com.example.data.ProfileManager.currentProfileIdValue, ', 'downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, ')
    
    # Just fix the specific files:
    
    if orig != c:
        with open(path, 'w') as f:
            f.write(c)

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            clean(os.path.join(root, file))

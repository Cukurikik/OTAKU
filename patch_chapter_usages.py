import os, re

def process_file(path):
    with open(path, 'r') as f:
        content = f.read()
    
    orig = content
    
    content = re.sub(r'chapterDao\.getChapters\((.*?),\s*(.*?)\)', r'chapterDao.getChapters(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'chapterDao\.getChaptersList\((.*?),\s*(.*?)\)', r'chapterDao.getChaptersList(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'chapterDao\.getChapter\((.*?),\s*(.*?),\s*(.*?)\)', r'chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, \1, \2, \3)', content)
    content = re.sub(r'chapterDao\.updateProgress\((.*?),\s*(.*?),\s*(.*?),\s*(.*?),\s*(.*?),\s*(.*?)\)', r'chapterDao.updateProgress(com.example.data.ProfileManager.currentProfileIdValue, \1, \2, \3, \4, \5, \6)', content)
    content = re.sub(r'chapterDao\.getUnreadCount\((.*?),\s*(.*?)\)', r'chapterDao.getUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'chapterDao\.markAllChaptersAsRead\((.*?),\s*(.*?)\)', r'chapterDao.markAllChaptersAsRead(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'chapterDao\.updateChapterReadStatus\((.*?),\s*(.*?),\s*(.*?),\s*(.*?)\)', r'chapterDao.updateChapterReadStatus(com.example.data.ProfileManager.currentProfileIdValue, \1, \2, \3, \4)', content)
    content = re.sub(r'chapterDao\.getReadChaptersSourceIds\(\)', r'chapterDao.getReadChaptersSourceIds(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'chapterDao\.getAllChaptersSync\(\)', r'chapterDao.getAllChaptersSync(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'chapterDao\.getAllChaptersFlow\(\)', r'chapterDao.getAllChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'chapterDao\.getReadChaptersFlow\(\)', r'chapterDao.getReadChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'chapterDao\.getReadChaptersSync\(\)', r'chapterDao.getReadChaptersSync(com.example.data.ProfileManager.currentProfileIdValue)', content)
    
    # insertChapter ensures profileId
    content = re.sub(r'chapterDao\.insertChapter\((.*?)\)', r'chapterDao.insertChapter(\1.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue))', content)
    content = re.sub(r'chapterDao\.updateChapter\((.*?)\)', r'chapterDao.updateChapter(\1.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue))', content)
    
    if orig != content:
        with open(path, 'w') as f:
            f.write(content)
        print(f"Updated {path}")

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            process_file(os.path.join(root, file))

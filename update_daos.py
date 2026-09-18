import os

daos = {
    'app/src/main/java/com/example/data/db/ChapterDao.kt': '''
    @Query("SELECT * FROM chapter_entries")
    suspend fun getAllChaptersSync(): List<ChapterEntry>
''',
    'app/src/main/java/com/example/data/db/UpdateDao.kt': '''
    @Query("SELECT * FROM updates")
    suspend fun getAllUpdatesSync(): List<UpdateEntity>
''',
    'app/src/main/java/com/example/data/db/DownloadDao.kt': '''
    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsSync(): List<DownloadEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloads(items: List<DownloadEntity>)
''',
    'app/src/main/java/com/example/data/db/PageDownloadDao.kt': '''
    @Query("SELECT * FROM page_downloads")
    suspend fun getAllPageDownloadsSync(): List<PageDownloadEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageDownloads(items: List<PageDownloadEntity>)
''',
    'app/src/main/java/com/example/data/db/CategoryDao.kt': '''
    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSync(): List<CategoryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntry>)
''',
    'app/src/main/java/com/example/data/db/TrackDao.kt': '''
    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksSync(): List<TrackEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(items: List<TrackEntity>)
''',
    'app/src/main/java/com/example/data/db/HistoryDao.kt': '''
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(items: List<HistoryItem>)
''',
    'app/src/main/java/com/example/data/db/LibraryDao.kt': '''
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(items: List<LibraryEntry>)
'''
}

for file_path, injection in daos.items():
    with open(file_path, 'r') as f:
        content = f.read()
    
    if "getAll" not in injection or "Sync()" not in content:
        # Just insert it before the last brace
        last_brace = content.rfind('}')
        if last_brace != -1:
            content = content[:last_brace] + injection + content[last_brace:]
            with open(file_path, 'w') as f:
                f.write(content)

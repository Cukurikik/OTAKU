package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.db.ChapterDao
import com.example.data.db.HistoryDao
import com.example.data.db.LibraryDao
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.LibraryEntry
import com.example.data.sources.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class MigrationItemState(
    val entry: LibraryEntry,
    val isMigrating: Boolean = false,
    val isSuccess: Boolean = false,
    val isError: Boolean = false,
    val errorMsg: String? = null
)

class MigrationViewModel(
    private val repository: ContentRepository,
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao,
    private val historyDao: HistoryDao
) : ViewModel() {

    private val _sources = MutableStateFlow<List<ContentSource>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _libraryEntries = MutableStateFlow<List<LibraryEntry>>(emptyList())
    
    val sourceFromOptions = _libraryEntries.map { entries ->
        entries.map { it.sourceId }.distinct().mapNotNull { srcId -> 
            _sources.value.find { it.sourceId == srcId } ?: object : ContentSource {
                override val sourceId = srcId
                override val sourceName = srcId
                override fun getRecommendations(page: Int) = emptyFlow<List<ContentItem>>()
                override fun getChapters(itemId: String) = emptyFlow<List<com.example.data.models.Chapter>>()
                override fun getPages(chapterId: String) = emptyFlow<List<com.example.data.models.Page>>()
                override suspend fun getDetail(itemId: String) = null
                override fun search(query: String, page: Int) = emptyFlow<List<ContentItem>>()
                override fun browse(filter: com.example.data.sources.BrowseFilter, page: Int) = emptyFlow<List<ContentItem>>()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedSourceFrom = MutableStateFlow<ContentSource?>(null)
    val selectedSourceFrom = _selectedSourceFrom.asStateFlow()

    private val _selectedSourceTo = MutableStateFlow<ContentSource?>(null)
    val selectedSourceTo = _selectedSourceTo.asStateFlow()

    val sourceToOptions = combine(_selectedSourceFrom, _sources) { fromSrc, allSources ->
        if (fromSrc == null) emptyList()
        else {
            val fromType = getContentTypeForMigration(fromSrc.sourceId)
            val filtered = allSources.filter { getContentTypeForMigration(it.sourceId) == fromType && it.sourceId != fromSrc.sourceId }
            val autoSource = object : ContentSource {
                override val sourceId = "auto_migration_all"
                override val sourceName = "Pencarian Otomatis (Semua Sumber)"
                override fun getRecommendations(page: Int) = emptyFlow<List<ContentItem>>()
                override fun getChapters(itemId: String) = emptyFlow<List<com.example.data.models.Chapter>>()
                override fun getPages(chapterId: String) = emptyFlow<List<com.example.data.models.Page>>()
                override suspend fun getDetail(itemId: String) = null
                override fun search(query: String, page: Int) = emptyFlow<List<ContentItem>>()
                override fun browse(filter: com.example.data.sources.BrowseFilter, page: Int) = emptyFlow<List<ContentItem>>()
            }
            listOf(autoSource) + filtered
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val itemsToMigrate = combine(_libraryEntries, _selectedSourceFrom) { entries, fromSrc ->
        if (fromSrc == null) emptyList()
        else entries.filter { it.sourceId == fromSrc.sourceId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _migrationStates = MutableStateFlow<Map<String, MigrationItemState>>(emptyMap())
    val migrationStates = _migrationStates.asStateFlow()
    
    private val _isMigratingAll = MutableStateFlow(false)
    val isMigratingAll = _isMigratingAll.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sourcesFlow.collect {
                _sources.value = it
            }
        }
        viewModelScope.launch {
            libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue).collect {
                _libraryEntries.value = it
            }
        }
    }

    private fun getContentTypeForMigration(sourceId: String): com.example.data.models.ContentType {
        val id = sourceId.lowercase()
        return when {
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") -> com.example.data.models.ContentType.ANIME
            id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> com.example.data.models.ContentType.DRAMA
            else -> com.example.data.models.ContentType.COMIC
        }
    }

    fun selectSourceFrom(source: ContentSource?) {
        _selectedSourceFrom.value = source
        _selectedSourceTo.value = null
        _migrationStates.value = emptyMap()
    }

    fun selectSourceTo(source: ContentSource?) {
        _selectedSourceTo.value = source
    }

    fun migrateAll() {
        val fromSrc = _selectedSourceFrom.value ?: return
        val toSrc = _selectedSourceTo.value ?: return
        val items = itemsToMigrate.value
        if (items.isEmpty()) return

        _isMigratingAll.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val states = _migrationStates.value.toMutableMap()
            
            for (entry in items) {
                states[entry.mangaId] = MigrationItemState(entry, isMigrating = true)
                _migrationStates.value = states.toMap()
                
                try {
                    val success = performMigration(entry, fromSrc, toSrc)
                    states[entry.mangaId] = MigrationItemState(
                        entry = entry, 
                        isMigrating = false, 
                        isSuccess = success, 
                        isError = !success, 
                        errorMsg = if (success) null else "Tidak ditemukan kecocokan"
                    )
                } catch (e: Exception) {
                    states[entry.mangaId] = MigrationItemState(
                        entry = entry, 
                        isMigrating = false, 
                        isSuccess = false, 
                        isError = true, 
                        errorMsg = e.localizedMessage ?: "Error jaringan"
                    )
                }
                _migrationStates.value = states.toMap()
            }
            
            _isMigratingAll.value = false
            // Keep selected sources so user can see results, they can press back to leave.
        }
    }

    private fun calculateLevenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    private fun calculateFuzzyMatchScore(title1: String, title2: String): Float {
        val t1 = title1.lowercase()
        val t2 = title2.lowercase()
        val maxLen = maxOf(t1.length, t2.length)
        if (maxLen == 0) return 1.0f
        val dist = calculateLevenshteinDistance(t1, t2)
        return 1.0f - dist.toFloat() / maxLen
    }

    private suspend fun performMigration(entry: LibraryEntry, fromSrc: ContentSource, toSrc: ContentSource): Boolean = coroutineScope {
        val searchResults = if (toSrc.sourceId == "auto_migration_all") {
            val type = getContentTypeForMigration(fromSrc.sourceId)
            val targets = _sources.value.filter { getContentTypeForMigration(it.sourceId) == type && it.sourceId != fromSrc.sourceId }
            val deferreds = targets.map { targetSrc ->
                async {
                    try {
                        val res = targetSrc.search(entry.title, 1).firstOrNull() ?: emptyList()
                        res.map { it to targetSrc }
                    } catch (e: Exception) { emptyList() }
                }
            }
            deferreds.awaitAll().flatten()
        } else {
            try {
                val res = toSrc.search(entry.title, 1).firstOrNull() ?: emptyList()
                res.map { it to toSrc }
            } catch (e: Exception) { emptyList() }
        }

        if (searchResults.isEmpty()) return@coroutineScope false

        var bestMatch: Pair<ContentItem, ContentSource>? = null
        var bestScore = 0f
        
        for ((item, src) in searchResults) {
            val score = calculateFuzzyMatchScore(entry.title, item.title)
            if (score > bestScore) {
                bestScore = score
                bestMatch = item to src
            }
        }
        
        if (bestMatch == null || bestScore < 0.5f) return@coroutineScope false
        
        val match = bestMatch.first
        val targetSrc = bestMatch.second
        
        val newChapters = try {
            targetSrc.getChapters(match.id).firstOrNull() ?: emptyList()
        } catch(e: Exception) { emptyList() }
        
        val oldChapters = chapterDao.getChaptersList(com.example.data.ProfileManager.currentProfileIdValue, entry.sourceId, entry.mangaId)
        
        val chapterEntities = newChapters.mapIndexed { index, newChap ->
            val numStr = newChap.title.replace(Regex("[^0-9.]"), "")
            val newNum = numStr.toFloatOrNull() ?: (newChapters.size - index).toFloat()
            
            val oldChap = oldChapters.find { 
                it.number == newNum || it.name.equals(newChap.title, ignoreCase = true)
            }
            
            ChapterEntry(
                sourceId = targetSrc.sourceId,
                mangaId = match.id,
                chapterId = newChap.id,
                name = newChap.title,
                url = newChap.url,
                dateUpload = null,
                number = newNum,
                read = oldChap?.read ?: false,
                bookmark = oldChap?.bookmark ?: false,
                lastPageRead = oldChap?.lastPageRead ?: 0,
                lastReadAt = oldChap?.lastReadAt,
                downloaded = false
            )
        }
        
        val newEntry = LibraryEntry(
            sourceId = targetSrc.sourceId,
            mangaId = match.id,
            title = match.title,
            thumbnailUrl = match.imageUrl,
            addedAt = entry.addedAt,
            lastUpdatedAt = System.currentTimeMillis(),
            favorite = true,
            categoryId = entry.categoryId,
            status = entry.status,
            unreadCount = chapterEntities.count { !it.read }
        )
        libraryDao.insertLibraryEntry(newEntry)
        chapterDao.insertChapters(chapterEntities)
        
        val histories = historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue).firstOrNull() ?: emptyList()
        val oldHistory = histories.find { it.id == entry.mangaId && it.sourceId == entry.sourceId }
        if (oldHistory != null) {
            val newHistory = oldHistory.copy(
                id = match.id,
                sourceId = targetSrc.sourceId,
                source = targetSrc.sourceName,
                title = match.title,
                imageUrl = match.imageUrl,
                lastUpdated = System.currentTimeMillis()
            )
            historyDao.deleteHistoryById(com.example.data.ProfileManager.currentProfileIdValue, oldHistory.id)
            historyDao.insertHistory(newHistory)
        }
        
        libraryDao.deleteLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, entry.sourceId, entry.mangaId)
        chapterDao.deleteChapters(oldChapters)
        
        try {
            com.example.data.linkrot.LinkRotTracker.getInstance(com.example.OtakuHubApp.instance).clearEntry(entry.sourceId, entry.mangaId)
            com.example.data.linkrot.LinkRotTracker.getInstance(com.example.OtakuHubApp.instance).clearEntry(targetSrc.sourceId, match.id)
        } catch (_: Exception) {}
        
        @Suppress("RedundantLabelMigration")
        return@coroutineScope true
    }
}

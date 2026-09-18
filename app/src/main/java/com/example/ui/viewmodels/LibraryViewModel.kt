package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.CategoryDao
import com.example.data.db.LibraryDao
import com.example.data.db.HistoryDao
import com.example.data.db.ChapterDao
import com.example.data.models.CategoryEntry
import com.example.data.models.LibraryEntry
import com.example.data.models.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val libraryDao: LibraryDao,
    private val categoryDao: CategoryDao,
    private val historyDao: HistoryDao,
    private val chapterDao: ChapterDao
) : ViewModel() {

    enum class SortOrder {
        ALPHABETICAL,
        DATE_ADDED,
        UNREAD,
        LAST_UPDATED,
        LAST_READ
    }

    enum class FilterMode {
        ALL,
        COMPLETED,
        ONGOING,
        UNREAD_ONLY
    }

    enum class MediaTypeFilter {
        ALL,
        COMIC,
        ANIME,
        DRAMA
    }

    enum class StatusFilter {
        ALL,
        ONGOING,
        COMPLETED
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId = _selectedCategoryId.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.ALPHABETICAL)
    val sortOrder = _sortOrder.asStateFlow()

    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode = _filterMode.asStateFlow()

    private val _mediaTypeFilter = MutableStateFlow(MediaTypeFilter.ALL)
    val mediaTypeFilter = _mediaTypeFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter = _statusFilter.asStateFlow()

    val categories = categoryDao.getAllCategories()
    
    val libraryItems = combine(
        libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue),
        _searchQuery,
        _selectedCategoryId,
        _sortOrder,
        _filterMode,
        _mediaTypeFilter,
        _statusFilter,
        historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue),
        chapterDao.getAllChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue)
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val items = array[0] as List<LibraryEntry>
        val query = array[1] as String
        val categoryId = array[2] as Long?
        val order = array[3] as SortOrder
        val filter = array[4] as FilterMode
        val media = array[5] as MediaTypeFilter
        val status = array[6] as StatusFilter
        @Suppress("UNCHECKED_CAST")
        val history = array[7] as List<HistoryItem>
        @Suppress("UNCHECKED_CAST")
        val allChapters = array[8] as List<com.example.data.models.ChapterEntry>

        val historyMap = history.associate { it.id to it.timestamp }
        val offlineMangaIds = allChapters.filter { it.downloaded }.map { it.mangaId }.toSet()
        
        val filtered = items.filter { item ->
            val matchesQuery = if (query.isNotBlank()) item.title.contains(query, ignoreCase = true) else true
            val matchesCategory = when (categoryId) {
                null -> true // Semua
                -1L -> offlineMangaIds.contains(item.mangaId) // Offline
                else -> item.categoryId == categoryId // Specific category
            }
            
            val matchesFilter = when (filter) {
                FilterMode.ALL -> true
                FilterMode.COMPLETED -> item.status.equals("Completed", ignoreCase = true)
                FilterMode.ONGOING -> item.status.equals("Ongoing", ignoreCase = true)
                FilterMode.UNREAD_ONLY -> item.unreadCount > 0
            }

            val matchesStatus = when (status) {
                StatusFilter.ALL -> true
                StatusFilter.ONGOING -> item.status.equals("Ongoing", ignoreCase = true)
                StatusFilter.COMPLETED -> item.status.equals("Completed", ignoreCase = true)
            }

            val itemMediaType = getMediaType(item.sourceId)
            val matchesMediaType = when (media) {
                MediaTypeFilter.ALL -> true
                MediaTypeFilter.COMIC -> itemMediaType == "COMIC"
                MediaTypeFilter.ANIME -> itemMediaType == "ANIME"
                MediaTypeFilter.DRAMA -> itemMediaType == "DRAMA"
            }

            matchesQuery && matchesCategory && matchesFilter && matchesStatus && matchesMediaType
        }

        when (order) {
            SortOrder.ALPHABETICAL -> filtered.sortedBy { it.title }
            SortOrder.DATE_ADDED -> filtered.sortedByDescending { it.addedAt }
            SortOrder.UNREAD -> filtered.sortedByDescending { it.unreadCount }
            SortOrder.LAST_UPDATED -> filtered.sortedByDescending { item -> item.lastUpdatedAt ?: 0L }
            SortOrder.LAST_READ -> filtered.sortedByDescending { item -> historyMap[item.mangaId] ?: 0L }
        }
    }

    private fun getMediaType(sourceId: String): String {
        val id = sourceId.lowercase()
        return when {
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") -> "ANIME"
            id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> "DRAMA"
            else -> "COMIC"
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun updateFilterMode(mode: FilterMode) {
        _filterMode.value = mode
    }

    fun updateMediaTypeFilter(media: MediaTypeFilter) {
        _mediaTypeFilter.value = media
    }

    fun updateStatusFilter(status: StatusFilter) {
        _statusFilter.value = status
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val maxSort = 0 
            categoryDao.insert(CategoryEntry(name = name, sortOrder = maxSort + 1))
        }
    }

    fun deleteLibraryItems(items: List<LibraryEntry>) {
        viewModelScope.launch {
            items.forEach { libraryDao.deleteLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, it.sourceId, it.mangaId) }
        }
    }

    fun updateLibraryCategories(items: List<LibraryEntry>, categoryId: Long?) {
        viewModelScope.launch {
            items.forEach { 
                libraryDao.insertLibraryEntry(it.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis()))
            }
        }
    }

    fun markItemsAsRead(items: List<LibraryEntry>) {
        viewModelScope.launch {
            items.forEach { item ->
                chapterDao.markAllChaptersAsRead(com.example.data.ProfileManager.currentProfileIdValue, item.sourceId, item.mangaId)
                libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, item.sourceId, item.mangaId)
            }
        }
    }
}

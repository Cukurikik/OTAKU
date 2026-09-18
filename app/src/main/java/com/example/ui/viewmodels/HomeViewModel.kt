package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.SettingsManager
import com.example.data.catalog.HubMode
import com.example.data.sources.ContentSource
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class)
sealed class MediaSearchState {
    object Loading : MediaSearchState()
    data class Success(val items: List<ContentItem>) : MediaSearchState()
    data class Error(val message: String) : MediaSearchState()
}

class HomeViewModel(
    private val repository: ContentRepository,
    private val settings: SettingsManager,
    private val historyDao: com.example.data.db.HistoryDao? = null,
    private val libraryDao: com.example.data.db.LibraryDao? = null
) : ViewModel() {
    private val _mode = MutableStateFlow(runCatching { HubMode.valueOf(settings.hubMode) }.getOrDefault(HubMode.COMIC))
    val mode: StateFlow<HubMode> = _mode.asStateFlow()

    val sourcesState: StateFlow<List<ContentSource>> = kotlinx.coroutines.flow.combine(repository.sourcesFlow, _mode) { sources, mode ->
        sources.filter { source ->
            val id = source.sourceId.lowercase()
            val mapped = when {
                id == "local_storage" -> mode
                id == "torrent_source" -> HubMode.ANIME
                id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") || id.contains("anilist") -> HubMode.ANIME
                id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> HubMode.DRAMA
                else -> HubMode.COMIC
            }
            mapped.name == mode.name
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _selectedSourceId = MutableStateFlow<String?>(null)
    val selectedSourceId: StateFlow<String?> = _selectedSourceId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _groupedByMediaState = MutableStateFlow<Map<HubMode, MediaSearchState>>(emptyMap())
    val groupedByMediaState: StateFlow<Map<HubMode, MediaSearchState>> = _groupedByMediaState.asStateFlow()

    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items: StateFlow<List<ContentItem>> = _items.asStateFlow()

    private val _resumeItems = MutableStateFlow<List<com.example.data.models.HistoryItem>>(emptyList())
    val resumeItems: StateFlow<List<com.example.data.models.HistoryItem>> = _resumeItems.asStateFlow()

    private val _recommendedItems = MutableStateFlow<List<com.example.engine.recommendation.RecommendationResult>>(emptyList())
    val recommendedItems: StateFlow<List<com.example.engine.recommendation.RecommendationResult>> = _recommendedItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<com.example.data.sources.SourceError?>(null)
    val errorMessage: StateFlow<com.example.data.sources.SourceError?> = _errorMessage.asStateFlow()

    private val _browseFilter = MutableStateFlow(com.example.data.sources.BrowseFilter())
    val browseFilter: StateFlow<com.example.data.sources.BrowseFilter> = _browseFilter.asStateFlow()

    private var loadJob: Job? = null

    private data class CombineState(
        val mode: HubMode,
        val selectedSourceId: String?,
        val query: String,
        val filter: com.example.data.sources.BrowseFilter,
        val activeSourceIds: List<String>
    )

    init {
        // Observe History for Resume Carousel (unfinished progress 0 < progress < 1)
        historyDao?.let { dao ->
            viewModelScope.launch {
                dao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue)
                    .collect { historyList ->
                        _resumeItems.value = com.example.engine.recommendation.LocalRecommendationEngine.getUnfinishedHistory(historyList, maxCount = 5)
                        updateRecommendations()
                    }
            }
        }

        // Observe Library to recalculate user preferences on favorites change
        libraryDao?.let { dao ->
            viewModelScope.launch {
                dao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue)
                    .collect {
                        updateRecommendations()
                    }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                _mode,
                _selectedSourceId,
                _searchQuery.debounce { if (it.isEmpty()) 0L else 300L }.distinctUntilChanged(),
                _browseFilter,
                repository.sourcesFlow
            ) { mode, selectedSource, query, filter, sources ->
                val activeSourceIds = if (selectedSource != null) listOf(selectedSource) else {
                    sources.filter { source ->
                        if (query.isNotBlank()) return@filter true
                        val id = source.sourceId.lowercase()
                        val mapped = when {
                            id == "local_storage" -> mode
                            id == "torrent_source" -> HubMode.ANIME
                            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") || id.contains("anilist") -> HubMode.ANIME
                            id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> HubMode.DRAMA
                            else -> HubMode.COMIC
                        }
                        mapped == mode
                    }.map { it.sourceId }
                }
                CombineState(mode, selectedSource, query, filter, activeSourceIds)
            }.distinctUntilChanged()
            .collect { state ->
                currentPage = 1
                isLastPage = false
                _items.value = emptyList()
                loadContent(state.query, state.selectedSourceId, state.activeSourceIds)
            }
        }
    }
    
    private var currentPage = 1
    private var isLastPage = false
    
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    private fun getActiveSourceIds(sourceId: String?, query: String): List<String> {
        if (sourceId != null) return listOf(sourceId)
        val allSources = repository.sources
        return allSources.filter { source ->
            if (query.isNotBlank()) return@filter true
            val id = source.sourceId.lowercase()
            val mapped = when {
                id == "local_storage" -> _mode.value
                id == "torrent_source" -> HubMode.ANIME
                id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") || id.contains("anilist") -> HubMode.ANIME
                id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> HubMode.DRAMA
                else -> HubMode.COMIC
            }
            mapped == _mode.value
        }.map { it.sourceId }
    }

    fun reload() {
        val current = _selectedSourceId.value
        viewModelScope.launch {
            currentPage = 1
            isLastPage = false
            _items.value = emptyList()
            val query = _searchQuery.value
            val activeSourceIds = getActiveSourceIds(current, query)
            loadContent(query, current, activeSourceIds)
        }
    }

    fun loadNextPage() {
        if (isLastPage || _isLoadingMore.value || _isLoading.value) return
        currentPage++
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val query = _searchQuery.value
                val sourceId = _selectedSourceId.value
                val activeSourceIds = getActiveSourceIds(sourceId, query)
                if (activeSourceIds.isEmpty()) {
                    _isLoadingMore.value = false
                    return@launch
                }
                
                if (query.isBlank()) {
                    repository.browse(_browseFilter.value, activeSourceIds, currentPage).collect { newItems ->
                        if (newItems.isEmpty() && activeSourceIds.size <= 1) isLastPage = true
                        val filtered = if (query.isBlank()) newItems.filter { it.type == _mode.value.contentType } else newItems
                        _items.value = _items.value + filtered
                    }
                } else {
                    repository.search(query, activeSourceIds, currentPage).collect { newItems ->
                        if (newItems.isEmpty() && activeSourceIds.size <= 1) isLastPage = true
                        val filtered = if (query.isBlank()) newItems.filter { it.type == _mode.value.contentType } else newItems
                        _items.value = _items.value + filtered
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore for now
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun setMode(mode: HubMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        settings.hubMode = mode.name
        _selectedSourceId.value = null
    }

    fun selectSource(sourceId: String?) {
        _selectedSourceId.value = sourceId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun updateBrowseFilter(filter: com.example.data.sources.BrowseFilter) {
        _browseFilter.value = filter
    }

    private fun loadContent(query: String, sourceId: String?, activeSourceIds: List<String>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            _isLoading.value = true
            _errorMessage.value = null
            _items.value = emptyList()
            try {
                if (activeSourceIds.isEmpty()) {
                    if (loadJob == currentJob) {
                        _isLoading.value = false
                    }
                    return@launch
                }
                
                if (query.isBlank()) {
                    repository.browse(_browseFilter.value, activeSourceIds, 1)
                        .onCompletion { 
                            if (loadJob == currentJob) {
                                _isLoading.value = false
                            }
                        }
                        .collect { newItems ->
                            val filtered = if (query.isBlank()) newItems.filter { it.type == _mode.value.contentType } else newItems
                            if (filtered.isNotEmpty()) {
                                _items.value = _items.value + filtered
                            }
                            if (newItems.isEmpty() && activeSourceIds.size <= 1) isLastPage = true
                        }
                } else {
                    if (activeSourceIds.size > 1) {
                        _isLoading.value = false
                        _groupedByMediaState.value = mapOf(
                            HubMode.COMIC to MediaSearchState.Loading,
                            HubMode.ANIME to MediaSearchState.Loading,
                            HubMode.DRAMA to MediaSearchState.Loading
                        )
                        
                        try {
                            val deferredResults = activeSourceIds.map { id ->
                                async(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val itemsList = mutableListOf<ContentItem>()
                                        repository.search(query, listOf(id), 1).collect {
                                            itemsList.addAll(it)
                                        }
                                        itemsList
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                            }
                            
                            val allResults = deferredResults.awaitAll().flatten()
                            
                            // Fuzzy Matching & Deduplication
                            fun normalizeTitle(title: String): String {
                                return title.lowercase().replace(Regex("[^a-z0-9]"), "")
                            }
                            
                            val deduplicatedResults = allResults.groupBy { 
                                normalizeTitle(it.title)
                            }.map { (_, group) ->
                                val primary = group.first()
                                val alternatives = group.map { 
                                    com.example.data.models.AlternativeSource(it.id, it.sourceId, it.source)
                                }.filter { it.sourceId != primary.sourceId }.distinctBy { it.sourceId }
                                
                                primary.copy(alternativeSources = alternatives)
                            }
                            
                            val comics = deduplicatedResults.filter { it.type == ContentType.COMIC }
                            val animes = deduplicatedResults.filter { it.type == ContentType.ANIME }
                            val dramas = deduplicatedResults.filter { it.type == ContentType.DRAMA }
                            
                            _groupedByMediaState.value = mapOf(
                                HubMode.COMIC to MediaSearchState.Success(comics),
                                HubMode.ANIME to MediaSearchState.Success(animes),
                                HubMode.DRAMA to MediaSearchState.Success(dramas)
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _groupedByMediaState.value = mapOf(
                                HubMode.COMIC to MediaSearchState.Error(e.message ?: "Error"),
                                HubMode.ANIME to MediaSearchState.Error(e.message ?: "Error"),
                                HubMode.DRAMA to MediaSearchState.Error(e.message ?: "Error")
                            )
                        }
                    } else {
                        repository.search(query, activeSourceIds, 1)
                            .onCompletion { 
                                if (loadJob == currentJob) {
                                    _isLoading.value = false
                                }
                            }
                            .collect { newItems ->
                                val filtered = if (query.isBlank()) newItems.filter { it.type == _mode.value.contentType } else newItems
                                if (filtered.isNotEmpty()) {
                                    _items.value = _items.value + filtered
                                    updateRecommendations()
                                }
                                if (newItems.isEmpty() && activeSourceIds.size <= 1) isLastPage = true
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = com.example.data.sources.SourceError.Network(e.message ?: "An error occurred")
            } finally {
                if (loadJob == currentJob) {
                    _isLoading.value = false
                    updateRecommendations()
                }
            }
        }
    }

    fun updateRecommendations() {
        val currentCatalog = _items.value
        if (currentCatalog.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val profileId = com.example.data.ProfileManager.currentProfileIdValue
                val library = libraryDao?.getAllEntriesSync(profileId) ?: emptyList()
                val history = historyDao?.getAllHistorySync(profileId) ?: emptyList()
                val userProfile = com.example.engine.recommendation.LocalRecommendationEngine.buildUserProfile(
                    libraryEntries = library,
                    historyItems = history,
                    knownCatalogItems = currentCatalog
                )
                val recommendations = com.example.engine.recommendation.LocalRecommendationEngine.generateRecommendations(
                    candidates = currentCatalog,
                    profile = userProfile,
                    limit = 10
                )
                _recommendedItems.value = recommendations
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error calculating recommendations: ${e.message}")
            }
        }
    }
}

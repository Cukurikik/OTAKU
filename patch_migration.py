import re

with open('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 'r') as f:
    content = f.read()

# 1. Update sourceToOptions
options_old = '''    val sourceToOptions = combine(_selectedSourceFrom, _sources) { fromSrc, allSources ->
        if (fromSrc == null) emptyList()
        else {
            val fromType = getContentTypeForMigration(fromSrc.sourceId)
            allSources.filter { getContentTypeForMigration(it.sourceId) == fromType && it.sourceId != fromSrc.sourceId }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())'''
options_new = '''    val sourceToOptions = combine(_selectedSourceFrom, _sources) { fromSrc, allSources ->
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())'''
content = content.replace(options_old, options_new)

# 2. Add coroutines imports
imports_old = '''import kotlinx.coroutines.flow.emptyFlow'''
imports_new = '''import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope'''
content = content.replace(imports_old, imports_new)

# 3. Update performMigration
perf_old = '''    private suspend fun performMigration(entry: LibraryEntry, fromSrc: ContentSource, toSrc: ContentSource): Boolean {
        val searchResults = try {
            toSrc.search(entry.title, 1).firstOrNull() ?: emptyList()
        } catch (e: Exception) { emptyList() }
        if (searchResults.isEmpty()) return false
        
        val match = searchResults.find { it.title.equals(entry.title, ignoreCase = true) } ?: searchResults.first()
        
        val newChapters = try {
            toSrc.getChapters(match.id).firstOrNull() ?: emptyList()
        } catch(e: Exception) { emptyList() }'''
perf_new = '''    private fun calculateLevenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
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
        } catch(e: Exception) { emptyList() }'''
content = content.replace(perf_old, perf_new)

with open('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 'w') as f:
    f.write(content)

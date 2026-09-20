package com.example.ui.player

import com.example.data.models.Chapter

data class PlaylistQueueState(
    val episodes: List<Chapter> = emptyList(),
    val currentEpisodeId: String = "",
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val nextEpisode: Chapter? = null,
    val previousEpisode: Chapter? = null
)

/**
 * Continuous Playback Playlist Queue Manager.
 * Handles chronological and reverse-chronological episode ordering for Anime and Drama series.
 */
class PlaylistQueue(
    initialEpisodes: List<Chapter> = emptyList(),
    initialEpisodeId: String = ""
) {
    private var _episodes: List<Chapter> = initialEpisodes
    private var _currentEpisodeId: String = initialEpisodeId

    fun setEpisodes(episodes: List<Chapter>, currentEpisodeId: String = _currentEpisodeId) {
        _episodes = episodes
        _currentEpisodeId = currentEpisodeId
    }

    fun setCurrentEpisodeId(episodeId: String) {
        _currentEpisodeId = episodeId
    }

    val currentEpisodeId: String
        get() = _currentEpisodeId

    val episodes: List<Chapter>
        get() = _episodes

    fun hasNext(): Boolean = getNextEpisode() != null

    fun hasPrevious(): Boolean = getPreviousEpisode() != null

    fun getNextEpisode(): Chapter? {
        if (_episodes.isEmpty()) return null
        val currentIndex = _episodes.indexOfFirst { it.id == _currentEpisodeId }
        if (currentIndex == -1) return null

        var nextIndex = -1
        if (_episodes.size > 1) {
            val firstNum = _episodes.first().title.filter { it.isDigit() }.toIntOrNull() ?: 0
            val lastNum = _episodes.last().title.filter { it.isDigit() }.toIntOrNull() ?: 0
            
            if (firstNum < lastNum) {
                // Ascending list (Episode 1, Episode 2, ... Episode N)
                if (currentIndex < _episodes.size - 1) {
                    nextIndex = currentIndex + 1
                }
            } else {
                // Descending list (Episode N, Episode N-1, ... Episode 1)
                if (currentIndex > 0) {
                    nextIndex = currentIndex - 1
                }
            }
        }
        return if (nextIndex in _episodes.indices) _episodes[nextIndex] else null
    }

    fun getPreviousEpisode(): Chapter? {
        if (_episodes.isEmpty()) return null
        val currentIndex = _episodes.indexOfFirst { it.id == _currentEpisodeId }
        if (currentIndex == -1) return null

        var prevIndex = -1
        if (_episodes.size > 1) {
            val firstNum = _episodes.first().title.filter { it.isDigit() }.toIntOrNull() ?: 0
            val lastNum = _episodes.last().title.filter { it.isDigit() }.toIntOrNull() ?: 0
            
            if (firstNum < lastNum) {
                // Ascending list (Episode 1, Episode 2, ... Episode N)
                if (currentIndex > 0) {
                    prevIndex = currentIndex - 1
                }
            } else {
                // Descending list (Episode N, Episode N-1, ... Episode 1)
                if (currentIndex < _episodes.size - 1) {
                    prevIndex = currentIndex + 1
                }
            }
        }
        return if (prevIndex in _episodes.indices) _episodes[prevIndex] else null
    }

    fun getNextEpisodeUrl(): String? {
        val next = getNextEpisode()
        return next?.url?.ifBlank { null } ?: next?.id
    }

    fun getState(): PlaylistQueueState {
        val currentIndex = _episodes.indexOfFirst { it.id == _currentEpisodeId }
        val next = getNextEpisode()
        val prev = getPreviousEpisode()
        return PlaylistQueueState(
            episodes = _episodes,
            currentEpisodeId = _currentEpisodeId,
            currentIndex = currentIndex,
            hasNext = next != null,
            hasPrevious = prev != null,
            nextEpisode = next,
            previousEpisode = prev
        )
    }
}

package com.example.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workers.LibraryUpdateWorker
import com.example.data.db.AppDatabase
import com.example.data.models.UpdateEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdatesViewModel(
    private val database: AppDatabase,
    private val context: Context
) : ViewModel() {
    
    val updates: StateFlow<List<UpdateEntity>> = database.updateDao().getRecentUpdates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isUpdating: StateFlow<Boolean> = LibraryUpdateWorker.isUpdatingFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    fun refreshNow() {
        LibraryUpdateWorker.runOnce(context)
    }

    fun markAsRead(update: UpdateEntity) {
        viewModelScope.launch {
            database.updateDao().markAsRead(update.mangaId, update.chapterId, true)
            database.chapterDao().updateChapterReadStatus(com.example.data.ProfileManager.currentProfileIdValue, update.sourceId, update.mangaId, update.chapterId, true)
            database.libraryDao().updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, update.sourceId, update.mangaId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            database.updateDao().markAllAsRead()
            // Mark all chapters as read in database
            val allUpdates = database.updateDao().getAllUpdatesSync()
            allUpdates.forEach { update ->
                database.chapterDao().updateChapterReadStatus(com.example.data.ProfileManager.currentProfileIdValue, update.sourceId, update.mangaId, update.chapterId, true)
                database.libraryDao().updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, update.sourceId, update.mangaId)
            }
        }
    }
    
    fun delete(update: UpdateEntity) {
        viewModelScope.launch {
            database.updateDao().delete(update.mangaId, update.chapterId)
        }
    }
    
    fun clearAll() {
        viewModelScope.launch {
            database.updateDao().deleteAll()
        }
    }
}

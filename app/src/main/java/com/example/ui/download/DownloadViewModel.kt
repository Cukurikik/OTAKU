package com.example.ui.download

import com.example.data.ContentRepository
import com.example.data.db.ChapterDao
import com.example.data.db.DownloadDao
import com.example.data.db.LibraryDao
import com.example.data.db.PageDownloadDao
import com.example.ui.viewmodels.DownloadItemUiState
import com.example.ui.viewmodels.DownloadViewModel as CoreDownloadViewModel

typealias DownloadViewModel = CoreDownloadViewModel
typealias DownloadItemUiState = com.example.ui.viewmodels.DownloadItemUiState

fun createDownloadViewModel(
    downloadDao: DownloadDao,
    pageDownloadDao: PageDownloadDao,
    libraryDao: LibraryDao,
    chapterDao: ChapterDao,
    repository: ContentRepository? = null
): DownloadViewModel {
    return CoreDownloadViewModel(
        downloadDao = downloadDao,
        pageDownloadDao = pageDownloadDao,
        libraryDao = libraryDao,
        chapterDao = chapterDao,
        repository = repository
    )
}

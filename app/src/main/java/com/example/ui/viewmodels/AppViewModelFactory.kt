package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.OtakuHubApp

class AppViewModelFactory(private val app: OtakuHubApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    app.repository,
                    com.example.data.SettingsManager(app),
                    app.database.historyDao(),
                    app.database.libraryDao()
                ) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(
                    app.database.libraryDao(),
                    app.database.categoryDao(),
                    app.database.historyDao(),
                    app.database.chapterDao()
                ) as T
            }
            modelClass.isAssignableFrom(ExtensionsViewModel::class.java) -> {
                ExtensionsViewModel(app) as T
            }
            modelClass.isAssignableFrom(MigrationViewModel::class.java) -> {
                MigrationViewModel(
                    app.repository,
                    app.database.libraryDao(),
                    app.database.chapterDao(),
                    app.database.historyDao()
                ) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(
                    app.database.historyDao(),
                    app.database.libraryDao(),
                    app.database.chapterDao(),
                    com.example.data.SettingsManager(app)
                ) as T
            }
            modelClass.isAssignableFrom(UpdatesViewModel::class.java) -> {
                UpdatesViewModel(app.database, app.applicationContext) as T
            }
            modelClass.isAssignableFrom(StatisticsViewModel::class.java) -> {
                StatisticsViewModel(
                    app.database.historyDao(),
                    app.database.libraryDao(),
                    app.database.chapterDao()
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}. Cannot instantiate without arguments.")
        }
    }
}

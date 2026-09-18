package com.example.ui.reader

import androidx.compose.runtime.Composable
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SpreadPages
import com.example.ui.screens.calculateSpread
import com.example.ui.viewmodels.ReaderViewModel

typealias SpreadPages = com.example.ui.screens.SpreadPages

@Composable
fun ReaderScreen(
    title: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onChapterChange: (String, String) -> Unit
) {
    com.example.ui.screens.ReaderScreen(
        title = title,
        viewModel = viewModel,
        onBack = onBack,
        onChapterChange = onChapterChange
    )
}


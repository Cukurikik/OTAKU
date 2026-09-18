package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.reader.SmartPanelDetector

class SmartPanelState {
    var panels by mutableStateOf<List<SmartPanelDetector.PanelRect>>(emptyList())
    var currentPanelIndex by mutableIntStateOf(-1)
    
    suspend fun analyze(bitmap: Bitmap) {
        val detector = SmartPanelDetector()
        val detected = detector.detectPanels(bitmap)
        panels = detected
        currentPanelIndex = -1
    }
    
    fun nextPanel(): SmartPanelDetector.PanelRect? {
        if (panels.isEmpty()) return null
        currentPanelIndex++
        if (currentPanelIndex >= panels.size) {
            currentPanelIndex = -1 // reset or move to next page
            return null
        }
        return panels[currentPanelIndex]
    }
}

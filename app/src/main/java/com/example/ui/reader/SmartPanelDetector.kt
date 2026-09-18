package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue
import kotlin.math.abs

/**
 * Detektor Panel Sederhana Berbasis Bounding Box Matriks Bitmap
 */
class SmartPanelDetector {

    data class PanelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = left + width / 2f
        val centerY get() = top + height / 2f
    }

    suspend fun detectPanels(bitmap: Bitmap): List<PanelRect> = withContext(Dispatchers.Default) {
        // Skala turun (downscale) gambar untuk mempercepat pemrosesan
        val maxDim = 800f
        val scale = if (bitmap.width > bitmap.height) {
            maxDim / bitmap.width
        } else {
            maxDim / bitmap.height
        }.coerceAtMost(1f)
        
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        val pixels = IntArray(scaledWidth * scaledHeight)
        scaledBitmap.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight)
        
        val gray = IntArray(scaledWidth * scaledHeight)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            gray[i] = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
        }

        // Matriks Gradien (Sobel Filter sederhana)
        val edgeMap = BooleanArray(scaledWidth * scaledHeight)
        val threshold = 40
        
        for (y in 1 until scaledHeight - 1) {
            for (x in 1 until scaledWidth - 1) {
                val idx = y * scaledWidth + x
                val p00 = gray[idx - scaledWidth - 1]
                val p01 = gray[idx - scaledWidth]
                val p02 = gray[idx - scaledWidth + 1]
                val p10 = gray[idx - 1]
                val p12 = gray[idx + 1]
                val p20 = gray[idx + scaledWidth - 1]
                val p21 = gray[idx + scaledWidth]
                val p22 = gray[idx + scaledWidth + 1]
                
                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * p21 + p22) - (p00 + 2 * p01 + p02)
                
                val g = abs(gx) + abs(gy)
                edgeMap[idx] = g > threshold
            }
        }
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // Connected Component Labeling untuk mencari Bounding Box
        val visited = BooleanArray(scaledWidth * scaledHeight)
        val detectedPanels = mutableListOf<PanelRect>()
        
        // Minimum panel size (e.g. 5% of width and height)
        val minWidth = scaledWidth * 0.05f
        val minHeight = scaledHeight * 0.05f
        
        for (y in 0 until scaledHeight step 2) {
            for (x in 0 until scaledWidth step 2) {
                val idx = y * scaledWidth + x
                if (edgeMap[idx] && !visited[idx]) {
                    // BFS to find blob bounding box
                    var minX = x
                    var maxX = x
                    var minY = y
                    var maxY = y
                    
                    val q: Queue<Pair<Int, Int>> = LinkedList()
                    q.add(Pair(x, y))
                    visited[idx] = true
                    
                    var pixelCount = 0
                    
                    while (q.isNotEmpty()) {
                        val (cx, cy) = q.poll()!!
                        pixelCount++
                        
                        if (cx < minX) minX = cx
                        if (cx > maxX) maxX = cx
                        if (cy < minY) minY = cy
                        if (cy > maxY) maxY = cy
                        
                        // Cek tetangga dengan lompatan untuk blur/menyambungkan
                        // Lompat 2-3 pixel agar gap kecil tetap tersambung
                        val step = 3
                        for (ny in maxOf(0, cy - step)..minOf(scaledHeight - 1, cy + step)) {
                            for (nx in maxOf(0, cx - step)..minOf(scaledWidth - 1, cx + step)) {
                                val nIdx = ny * scaledWidth + nx
                                if (!visited[nIdx] && edgeMap[nIdx]) {
                                    visited[nIdx] = true
                                    q.add(Pair(nx, ny))
                                }
                            }
                        }
                    }
                    
                    // Validasi ukuran
                    if ((maxX - minX) > minWidth && (maxY - minY) > minHeight) {
                        // Rescale back to original bitmap size
                        detectedPanels.add(
                            PanelRect(
                                left = (minX / scale).toInt(),
                                top = (minY / scale).toInt(),
                                right = (maxX / scale).toInt(),
                                bottom = (maxY / scale).toInt()
                            )
                        )
                    }
                }
            }
        }
        
        // Urutkan dari kanan ke kiri (R-to-L) sesuai standar baca manga
        // Kemudian dari atas ke bawah
        detectedPanels.sortedWith(
            compareByDescending<PanelRect> { it.right }.thenBy { it.top }
        ).let { panels ->
            // Filter overlapping panels (keep the larger or merge them)
            // Sederhana: kita return semua yang lolos.
            if (panels.isEmpty()) {
                // Fallback: seluruh gambar adalah 1 panel
                listOf(PanelRect(0, 0, bitmap.width, bitmap.height))
            } else {
                panels
            }
        }
    }
}

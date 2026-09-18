package com.example.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import coil.size.Size
import coil.transform.Transformation

/**
 * Memory-efficient and ultra-fast transformation that crops white/transparent margins around comic pages.
 * Handles HARDWARE bitmaps safely by downsampling checks and ensures no memory leaks or GC trashing
 * during continuous vertical scroll in Webtoon mode.
 */
class WhiteBorderCropTransformation : Transformation {
    override val cacheKey: String = "white_border_crop_v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.isRecycled || input.width <= 4 || input.height <= 4) {
            return input
        }

        // Hardware Bitmaps cannot have getPixel/getPixels called on them directly.
        val workingBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input.config == Bitmap.Config.HARDWARE) {
            input.copy(Bitmap.Config.ARGB_8888, false) ?: return input
        } else {
            input
        }

        val width = workingBitmap.width
        val height = workingBitmap.height
        val threshold = 245 // RGB threshold to be considered "white"

        // Sampling strides to scan ultra-fast without freezing UI / GC trashing
        val sampleStepX = maxOf(1, width / 64)
        val sampleStepY = maxOf(1, height / 128)

        fun isContentPixel(pixel: Int): Boolean {
            val alpha = Color.alpha(pixel)
            if (alpha < 20) return false // Transparent margin
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            return r < threshold || g < threshold || b < threshold
        }

        fun hasContentInRow(y: Int, startX: Int, endX: Int, step: Int): Boolean {
            var x = startX
            while (x <= endX) {
                if (isContentPixel(workingBitmap.getPixel(x, y))) return true
                x += step
            }
            return false
        }

        fun hasContentInCol(x: Int, startY: Int, endY: Int, step: Int): Boolean {
            var y = startY
            while (y <= endY) {
                if (isContentPixel(workingBitmap.getPixel(x, y))) return true
                y += step
            }
            return false
        }

        var top = 0
        var bottom = height - 1
        var left = 0
        var right = width - 1

        // 1. Find top margin with coarse-to-fine scan
        var coarseTop = -1
        var y = 0
        while (y < height) {
            if (hasContentInRow(y, 0, width - 1, sampleStepX)) {
                coarseTop = y
                break
            }
            y += sampleStepY
        }
        if (coarseTop != -1) {
            val fineStartY = maxOf(0, coarseTop - sampleStepY)
            for (fy in fineStartY..coarseTop) {
                if (hasContentInRow(fy, 0, width - 1, maxOf(1, sampleStepX / 2))) {
                    top = fy
                    break
                }
            }
        }

        // 2. Find bottom margin with coarse-to-fine scan
        var coarseBottom = -1
        y = height - 1
        while (y >= top) {
            if (hasContentInRow(y, 0, width - 1, sampleStepX)) {
                coarseBottom = y
                break
            }
            y -= sampleStepY
        }
        if (coarseBottom != -1) {
            val fineEndY = minOf(height - 1, coarseBottom + sampleStepY)
            for (fy in fineEndY downTo coarseBottom) {
                if (hasContentInRow(fy, 0, width - 1, maxOf(1, sampleStepX / 2))) {
                    bottom = fy
                    break
                }
            }
        }

        // 3. Find left margin
        var coarseLeft = -1
        var x = 0
        while (x < width) {
            if (hasContentInCol(x, top, bottom, sampleStepY)) {
                coarseLeft = x
                break
            }
            x += sampleStepX
        }
        if (coarseLeft != -1) {
            val fineStartX = maxOf(0, coarseLeft - sampleStepX)
            for (fx in fineStartX..coarseLeft) {
                if (hasContentInCol(fx, top, bottom, maxOf(1, sampleStepY / 2))) {
                    left = fx
                    break
                }
            }
        }

        // 4. Find right margin
        var coarseRight = -1
        x = width - 1
        while (x >= left) {
            if (hasContentInCol(x, top, bottom, sampleStepY)) {
                coarseRight = x
                break
            }
            x -= sampleStepX
        }
        if (coarseRight != -1) {
            val fineEndX = minOf(width - 1, coarseRight + sampleStepX)
            for (fx in fineEndX downTo coarseRight) {
                if (hasContentInCol(fx, top, bottom, maxOf(1, sampleStepY / 2))) {
                    right = fx
                    break
                }
            }
        }

        // Add small safety padding (2px)
        top = maxOf(0, top - 2)
        bottom = minOf(height - 1, bottom + 2)
        left = maxOf(0, left - 2)
        right = minOf(width - 1, right + 2)

        val newWidth = right - left + 1
        val newHeight = bottom - top + 1

        val didCrop = (left > 0 || top > 0 || right < width - 1 || bottom < height - 1) &&
                newWidth > 0 && newHeight > 0 &&
                (newWidth < width || newHeight < height)

        if (!didCrop) {
            if (workingBitmap !== input) {
                workingBitmap.recycle()
            }
            return input
        }

        val croppedBitmap = Bitmap.createBitmap(workingBitmap, left, top, newWidth, newHeight)
        if (workingBitmap !== input) {
            workingBitmap.recycle()
        }

        // Convert to HARDWARE bitmap if supported to save Java heap memory footprint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hwBitmap = croppedBitmap.copy(Bitmap.Config.HARDWARE, false)
            if (hwBitmap != null) {
                croppedBitmap.recycle()
                return hwBitmap
            }
        }

        return croppedBitmap
    }
}

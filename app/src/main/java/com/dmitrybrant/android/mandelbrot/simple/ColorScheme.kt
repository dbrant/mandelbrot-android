package com.dmitrybrant.android.mandelbrot.simple

import android.graphics.Color

object ColorScheme {
    private var colorSchemes = mutableListOf<IntArray>()

    fun initColorSchemes() {
        colorSchemes = ArrayList()
        colorSchemes.add(createColorScheme(intArrayOf(Color.BLUE, Color.GREEN, Color.RED, Color.BLUE), 256))
        colorSchemes.add(createColorScheme(intArrayOf(Color.YELLOW, Color.MAGENTA, Color.BLUE, Color.GREEN, Color.YELLOW), 256))
        colorSchemes.add(createColorScheme(intArrayOf(Color.WHITE, Color.BLACK, Color.WHITE), 256))
        colorSchemes.add(createColorScheme(intArrayOf(Color.BLACK, Color.WHITE, Color.BLACK), 256))
        colorSchemes.add(intArrayOf(Color.BLACK, Color.WHITE))
    }

    fun getColorSchemes(): List<IntArray> {
        return colorSchemes
    }

    fun getShiftedScheme(colors: IntArray, shiftAmount: Int): IntArray {
        val shifted = IntArray(colors.size)
        for (i in colors.indices) {
            shifted[i] = colors[(i + shiftAmount) % colors.size]
        }
        return shifted
    }

    private fun createColorScheme(colorArray: IntArray, numElements: Int): IntArray {
        val elementsPerStep = numElements / (colorArray.size - 1)
        val colors = IntArray(numElements)
        var r = 0f
        var g = 0f
        var b = 0f
        var rInc = 0f
        var gInc = 0f
        var bInc = 0f
        var cIndex = 0
        var cCounter = 0
        for (i in 0 until numElements) {
            if (cCounter == 0) {
                b = (colorArray[cIndex] and 0xff.toFloat().toInt()).toFloat()
                g = (colorArray[cIndex] and 0xff00 shr 8.toFloat().toInt()).toFloat()
                r = (colorArray[cIndex] and 0xff0000 shr 16.toFloat().toInt()).toFloat()
                if (cIndex < colorArray.size - 1) {
                    bInc = ((colorArray[cIndex + 1] and 0xff).toFloat() - b) / elementsPerStep.toFloat()
                    gInc = ((colorArray[cIndex + 1] and 0xff00 shr 8).toFloat() - g) / elementsPerStep.toFloat()
                    rInc = ((colorArray[cIndex + 1] and 0xff0000 shr 16).toFloat() - r) / elementsPerStep.toFloat()
                }
                cIndex++
                cCounter = elementsPerStep
            }
            colors[i] = -0x1000000 or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            b += bInc
            g += gInc
            r += rInc
            if (b < 0f) {
                b = 0f
            }
            if (g < 0f) {
                g = 0f
            }
            if (r < 0f) {
                r = 0f
            }
            if (b > 255f) {
                b = 255f
            }
            if (g > 255f) {
                g = 255f
            }
            if (r > 255f) {
                r = 255f
            }
            cCounter--
        }
        return colors
    }
}
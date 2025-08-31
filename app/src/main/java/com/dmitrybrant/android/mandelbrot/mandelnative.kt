package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap

object MandelNative {
    fun setParameters(paramIndex: Int, power: Int, numIterations: Int, xMin: Double, xMax: Double, yMin: Double, yMax: Double, isJulia: Int, juliaX: Double, juliaY: Double, viewWidth: Int, viewHeight: Int) {
        MandelbrotCalculator.setParameters(paramIndex, power, numIterations, xMin, xMax, yMin, yMax, isJulia, juliaX, juliaY, viewWidth, viewHeight)
    }
    
    fun releaseParameters(paramIndex: Int) {
        MandelbrotCalculator.releaseParameters(paramIndex)
    }
    
    fun drawFractal(paramIndex: Int, startX: Int, startY: Int, startWidth: Int, startHeight: Int, level: Int, doAll: Int) {
        MandelbrotCalculator.drawFractal(paramIndex, startX, startY, startWidth, startHeight, level, doAll)
    }
    
    fun setBitmap(paramIndex: Int, bmp: Bitmap?) {
        MandelbrotCalculator.setBitmap(paramIndex, bmp)
    }
    
    fun updateBitmap(paramIndex: Int, bmp: Bitmap?) {
        MandelbrotCalculator.updateBitmap(paramIndex, bmp)
    }
    
    fun releaseBitmap(paramIndex: Int) {
        MandelbrotCalculator.releaseBitmap(paramIndex)
    }
    
    fun setColorPalette(paramIndex: Int, colors: IntArray?, numColors: Int) {
        MandelbrotCalculator.setColorPalette(paramIndex, colors, numColors)
    }
    
    fun signalTerminate(paramIndex: Int) {
        MandelbrotCalculator.signalTerminate(paramIndex)
    }
}

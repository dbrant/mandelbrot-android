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
    
    // Deep zoom methods
    fun setParametersDeepZoom(
        paramIndex: Int,
        power: Int,
        numIterations: Int,
        centerX: String,
        centerY: String,
        zoomFactor: Double,
        isJulia: Int,
        juliaX: Double,
        juliaY: Double,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val centerXBig = java.math.BigDecimal(centerX)
        val centerYBig = java.math.BigDecimal(centerY)
        MandelbrotCalculator.setParametersDeepZoom(
            paramIndex, power, numIterations, centerXBig, centerYBig, zoomFactor,
            isJulia, juliaX, juliaY, viewWidth, viewHeight
        )
    }
    
    fun getZoomInfo(paramIndex: Int): String {
        return MandelbrotCalculator.getZoomInfo(paramIndex)
    }
    
    fun isUsingDeepZoom(paramIndex: Int): Boolean {
        return MandelbrotCalculator.isUsingDeepZoom(paramIndex)
    }
    
    fun getCenterCoordinates(paramIndex: Int): Pair<String, String> {
        return MandelbrotCalculator.getCenterCoordinates(paramIndex)
    }
    
    fun zoomToPoint(paramIndex: Int, screenX: Int, screenY: Int, zoomMultiplier: Double) {
        MandelbrotCalculator.zoomToPoint(paramIndex, screenX, screenY, zoomMultiplier)
    }
}

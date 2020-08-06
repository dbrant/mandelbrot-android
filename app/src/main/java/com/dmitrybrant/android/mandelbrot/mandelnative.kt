package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap

object MandelNative {
    external fun setParameters(paramIndex: Int, power: Int, numIterations: Int, xMin: Double, xMax: Double, yMin: Double, yMax: Double, isJulia: Int, juliaX: Double, juliaY: Double, viewWidth: Int, viewHeight: Int)
    external fun releaseParameters(paramIndex: Int)
    external fun drawFractal(paramIndex: Int, startX: Int, startY: Int, startWidth: Int, startHeight: Int, level: Int, doAll: Int)
    external fun setBitmap(paramIndex: Int, bmp: Bitmap?)
    external fun updateBitmap(paramIndex: Int, bmp: Bitmap?)
    external fun releaseBitmap(paramIndex: Int)
    external fun setColorPalette(paramIndex: Int, colors: IntArray?, numColors: Int)
    external fun signalTerminate(paramIndex: Int)
}

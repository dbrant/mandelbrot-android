package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap

object MandelNative {
    external fun setParameters(paramIndex: Int, jarg1: Int, jarg2: Double, jarg3: Double, jarg4: Double, jarg5: Double, isJulia: Int, jarg6: Double, jarg7: Double, jarg8: Int, jarg9: Int)
    external fun releaseParameters(paramIndex: Int)
    external fun drawFractal(paramIndex: Int, jarg1: Int, jarg2: Int, jarg3: Int, jarg4: Int, jarg5: Int, jarg6: Int)
    external fun setBitmap(paramIndex: Int, bmp: Bitmap?)
    external fun updateBitmap(paramIndex: Int, bmp: Bitmap?)
    external fun releaseBitmap(paramIndex: Int)
    external fun setColorPalette(paramIndex: Int, colors: IntArray?, numColors: Int)
    external fun signalTerminate(paramIndex: Int)
}

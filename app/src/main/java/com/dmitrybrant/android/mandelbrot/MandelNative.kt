package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap

class OrbitResult(
    val orbit: FloatArray, // length = 1024*1024, layout = R channel; triplets per step
    val polyScaled: FloatArray, // 6 floats -> poly1[0..3], poly2[0..1]
    val polyLim: Int,
    val polyScaleExp: Int,
    val qStart: Int // like JS’s uState[2]-1 base exponent
)

object MandelNative {
    init {
        System.loadLibrary("mandel_native")
    }

    external fun makeReferenceOrbit(
        reStr: String, imStr: String, // center (decimal strings)
        radiusStr: String,
        iterations: Int
    ): OrbitResult


    // TODO: remove legacy stuff
    fun setParameters(paramIndex: Int, power: Int, numIterations: Int, xMin: Double, xMax: Double, yMin: Double, yMax: Double, isJulia: Int, juliaX: Double, juliaY: Double, viewWidth: Int, viewHeight: Int) { }
    fun releaseParameters(paramIndex: Int) { }
    fun drawFractal(paramIndex: Int, startX: Int, startY: Int, startWidth: Int, startHeight: Int, level: Int, doAll: Int) { }
    fun setBitmap(paramIndex: Int, bmp: Bitmap?) { }
    fun updateBitmap(paramIndex: Int, bmp: Bitmap?) { }
    fun releaseBitmap(paramIndex: Int) { }
    fun setColorPalette(paramIndex: Int, colors: IntArray?, numColors: Int) { }
    fun signalTerminate(paramIndex: Int) { }

}

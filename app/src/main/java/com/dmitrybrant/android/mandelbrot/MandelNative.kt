package com.dmitrybrant.android.mandelbrot

object MandelNative {
    init {
        System.loadLibrary("mandel_native")
    }

    external fun makeReferenceOrbit(
        reStr: String, imStr: String, // center (decimal strings)
        radiusStr: String,
        iterations: Int
    ): OrbitResult

    data class OrbitResult(
        val orbit: FloatArray, // length = 1024*1024, layout = R channel; triplets per step
        val polyScaled: FloatArray, // 6 floats -> poly1[0..3], poly2[0..1]
        val polyLim: Int,
        val polyScaleExp: Int,
        val qStart: Int // like JS’s uState[2]-1 base exponent
    )
}

package com.dmitrybrant.android.mandelbrot

import java.nio.ByteBuffer

class OrbitResult(
    val orbit: ByteBuffer,
    val polyScaled: FloatArray,
    val polyLim: Int,
    val polyScaleExp: Int,
    val radiusExp: Double
)

object MandelbrotNative {
    init {
        System.loadLibrary("mandel_native")
    }

    // Native methods
    external fun createState(x: Double, y: Double, r: Double, iterations: Int): Long
    external fun destroyState(statePtr: Long)
    external fun setState(statePtr: Long, x: Double, y: Double, r: Double, iterations: Int)
    external fun setStateStr(statePtr: Long, x: String, y: String, r: String, iterations: Int)
    external fun zoomIn(statePtr: Long, dx: Double, dy: Double, factor: Double)
    external fun zoomOut(statePtr: Long, factor: Double)
    external fun generateOrbit(statePtr: Long): OrbitResult?
    external fun setIterations(statePtr: Long, iterations: Int)
    external fun getCenterX(statePtr: Long): String?
    external fun getCenterY(statePtr: Long): String?
    external fun getRadius(statePtr: Long): String?

    const val ITERATIONS = 2000
    const val INIT_X = -0.5
    const val INIT_Y = 0.0
    const val INIT_R = 2.0

    class MandelbrotState {
        private var nativePtr: Long

        var iterations = ITERATIONS
            set(value) {
                field = value
                setIterations(nativePtr, iterations)
            }

        var cmapscale = 20.0

        init {
            nativePtr = createState(INIT_X, INIT_Y, INIT_R, iterations)
        }

        fun destroy() {
            if (nativePtr != 0L) {
                destroyState(nativePtr)
                nativePtr = 0
            }
        }

        @Throws(Throwable::class)
        protected fun finalize() {
            destroy()
        }

        fun set(x: Double, y: Double, r: Double, iterations: Int) {
            setState(nativePtr, x, y, r, iterations)
        }

        fun set(x: String, y: String, r: String, iterations: Int) {
            setStateStr(nativePtr, x, y, r, iterations)
        }

        fun zoomIn(dx: Double, dy: Double, factor: Double) {
            zoomIn(nativePtr, dx, dy, factor)
        }

        fun generateOrbit(): OrbitResult {
            return generateOrbit(nativePtr)!!
        }

        val centerX: String
            get() = getCenterX(nativePtr)!!

        val centerY: String
            get() = getCenterY(nativePtr)!!

        val radius: String
            get() = getRadius(nativePtr)!!

        fun reset() {
            iterations = ITERATIONS
            cmapscale = 20.0
            set(INIT_X, INIT_Y, INIT_R, iterations)
        }

        fun zoomOut(factor: Double) {
            zoomOut(nativePtr, factor)
        }
    }
}

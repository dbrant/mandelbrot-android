package com.dmitrybrant.android.mandelbrot

object MandelbrotNative {
    init {
        System.loadLibrary("mandel_native") // Load the native library
    }

    // Native methods
    external fun createState(): Long
    external fun destroyState(statePtr: Long)
    external fun setState(statePtr: Long, x: Double, y: Double, r: Double)
    external fun updateState(statePtr: Long, dx: Double, dy: Double)
    external fun generateOrbit(statePtr: Long): FloatArray?
    external fun getPolynomialCoefficients(statePtr: Long): FloatArray?
    external fun getPolynomialLimit(statePtr: Long): Int
    external fun getPolynomialScaleExp(statePtr: Long): Int
    external fun getRadiusExponent(statePtr: Long): Double
    external fun getCenterX(statePtr: Long): String?
    external fun getCenterY(statePtr: Long): String?
    external fun getRadius(statePtr: Long): String?

    external fun testBasicFunctionality(): String?

    class MandelbrotState {
        private var nativePtr: Long
        var iterations: Int = 1000
        var cmapscale: Double = 20.0

        init {
            nativePtr = createState()
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

        fun set(x: Double, y: Double, r: Double) {
            setState(nativePtr, x, y, r)
        }

        fun update(dx: Double, dy: Double) {
            updateState(nativePtr, dx, dy)
        }

        fun generateOrbit(): FloatArray {
            return generateOrbit(nativePtr)!!
        }

        val polynomialCoefficients: FloatArray
            get() = getPolynomialCoefficients(nativePtr)!!

        val polynomialLimit: Int
            get() = getPolynomialLimit(nativePtr)

        val polynomialScaleExp: Int
            get() = getPolynomialScaleExp(nativePtr)

        val radiusExponent: Double
            get() = getRadiusExponent(nativePtr)

        val centerX: String
            get() = getCenterX(nativePtr)!!

        val centerY: String
            get() = getCenterY(nativePtr)!!

        val radius: String
            get() = getRadius(nativePtr)!!

        fun reset() {
            iterations = 1000
            cmapscale = 20.1
            set(-0.5, 0.0, 2.0)
        }

        fun zoomOut() {
            // This would need to be implemented in native code
            // For now, we can approximate by doubling the radius
            val currentRadius = this.radius
            try {
                val r = currentRadius.toDouble()
                setState(nativePtr, 0.0, 0.0, r * 2)
            } catch (e: NumberFormatException) {
                // Handle high precision case - would need native implementation
            }
        }

        val stateString: String
            get() = "re=" + this.centerX + "; im=" + this.centerY +
                    "; r=" + this.radius + "; iterations=" + iterations
    }
}

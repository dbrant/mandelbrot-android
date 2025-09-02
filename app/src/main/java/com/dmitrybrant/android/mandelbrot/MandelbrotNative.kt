package com.dmitrybrant.android.mandelbrot

object MandelbrotNative {
    init {
        System.loadLibrary("mandel_native") // Load the native library
    }

    // Native methods
    external fun createState(): Long
    external fun destroyState(statePtr: Long)
    external fun setState(statePtr: Long, x: Double, y: Double, r: Double)
    external fun setStateFromStrings(statePtr: Long, x: String, y: String, r: String)
    external fun updateState(statePtr: Long, dx: Double, dy: Double)
    external fun zoomOut(statePtr: Long)
    external fun generateOrbit(statePtr: Long): FloatArray?
    external fun getPolynomialCoefficients(statePtr: Long): DoubleArray?
    external fun getPolynomialLimit(statePtr: Long): Int
    external fun getPolynomialScaleExp(statePtr: Long): Int
    external fun getRadiusExponent(statePtr: Long): Double
    external fun getCenterX(statePtr: Long): String?
    external fun getCenterY(statePtr: Long): String?
    external fun getCenterXAsDouble(statePtr: Long): Double
    external fun getCenterYAsDouble(statePtr: Long): Double
    external fun getRadius(statePtr: Long): String?


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

        fun setFromStrings(x: String, y: String, r: String) {
            setStateFromStrings(nativePtr, x, y, r)
        }

        fun generateOrbit(): FloatArray {
            return generateOrbit(nativePtr)!!
        }

        val polynomialCoefficients: DoubleArray
            get() = getPolynomialCoefficients(nativePtr)!!

        val polynomialLimit: Int
            get() = getPolynomialLimit(nativePtr)

        val polynomialScaleExp: Int
            get() = getPolynomialScaleExp(nativePtr)

        val radiusExponent: Double
            get() = getRadiusExponent(nativePtr)

        val centerX: String
            get() = getCenterX(nativePtr)!!

        val centerXasDouble: Double
            get() = getCenterXAsDouble(nativePtr)

        val centerY: String
            get() = getCenterY(nativePtr)!!

        val radius: String
            get() = getRadius(nativePtr)!!

        fun reset() {
            iterations = 1000
            cmapscale = 20.1
            set(0.0, 0.0, 2.0)
        }

        fun zoomOut() {
            // Use the native zoomOut implementation
            MandelbrotNative.zoomOut(nativePtr)
        }

        val stateString: String
            get() = "re=" + this.centerX + "; im=" + this.centerY +
                    "; r=" + this.radius + "; iterations=" + iterations
    }
}

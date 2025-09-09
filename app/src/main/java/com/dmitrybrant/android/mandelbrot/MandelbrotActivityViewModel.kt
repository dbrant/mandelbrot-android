package com.dmitrybrant.android.mandelbrot

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel

class MandelbrotActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val sharedPreferences = app.getSharedPreferences("MandelbrotActivityPrefs", 0)

    var colorScale = MandelbrotNative.INIT_COLOR_SCALE
    var xCenter = MandelbrotNative.INIT_X.toString()
    var yCenter = MandelbrotNative.INIT_Y.toString()
    var xExtent = MandelbrotNative.INIT_R.toString()
    var numIterations = MandelbrotNative.ITERATIONS

    init {
        xCenter = sharedPreferences.getString("gmp_xcenter", MandelbrotNative.INIT_X.toString())!!
        yCenter = sharedPreferences.getString("gmp_ycenter", MandelbrotNative.INIT_X.toString())!!
        xExtent = sharedPreferences.getString("gmp_xextent", MandelbrotNative.INIT_R.toString())!!
        numIterations = sharedPreferences.getInt("gmp_iterations", MandelbrotNative.ITERATIONS)
        colorScale = sharedPreferences.getFloat("gmp_colorscale", MandelbrotNative.INIT_COLOR_SCALE)
    }

    fun save() {
        sharedPreferences.edit {
            putString("gmp_xcenter", xCenter)
            putString("gmp_ycenter", yCenter)
            putString("gmp_xextent", xExtent)
            putInt("gmp_iterations", numIterations)
            putFloat("gmp_colorscale", colorScale)
        }
    }
}

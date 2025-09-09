package com.dmitrybrant.android.mandelbrot

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel

class MandelbrotActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val sharedPreferences = app.getSharedPreferences("MandelbrotActivityPrefs", 0)

    var colorScale = 0
    var xCenter = MandelbrotNative.INIT_X.toString()
    var yCenter = MandelbrotNative.INIT_Y.toString()
    var xExtent = MandelbrotNative.INIT_R.toString()
    var numIterations = MandelbrotNative.ITERATIONS

    init {
        xCenter = sharedPreferences.getString("gmp_xcenter", MandelbrotNative.INIT_X.toString())!!
        yCenter = sharedPreferences.getString("gmp_ycenter", MandelbrotNative.INIT_X.toString())!!
        xExtent = sharedPreferences.getString("gmp_xextent", MandelbrotNative.INIT_R.toString())!!
        numIterations = sharedPreferences.getInt("gmp_iterations", MandelbrotNative.ITERATIONS)
        colorScale = sharedPreferences.getInt("gmp_colorscale", 0)
    }

    fun save() {
        sharedPreferences.edit {
            putString("gmp_xcenter", xCenter)
            putString("gmp_ycenter", yCenter)
            putString("gmp_xextent", xExtent)
            putInt("gmp_iterations", numIterations)
            putInt("gmp_colorscale", colorScale)
        }
    }
}

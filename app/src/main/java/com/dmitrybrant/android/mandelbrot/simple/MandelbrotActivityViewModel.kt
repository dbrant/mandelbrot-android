package com.dmitrybrant.android.mandelbrot.simple

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit

class MandelbrotActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val sharedPreferences = app.getSharedPreferences("MandelbrotActivityPrefs", 0)

    var juliaEnabled = false
    var currentColorScheme = 0
    var xCenter = 0.0
    var yCenter = 0.0
    var xExtent = 0.0
    var numIterations = 0
    var power = 0

    init {
        xCenter = sharedPreferences.getString("xcenter", MandelbrotCalculator.DEFAULT_X_CENTER.toString())!!.toDouble()
        yCenter = sharedPreferences.getString("ycenter", MandelbrotCalculator.DEFAULT_Y_CENTER.toString())!!.toDouble()
        xExtent = sharedPreferences.getString("xextent", MandelbrotCalculator.DEFAULT_X_EXTENT.toString())!!.toDouble()
        numIterations = sharedPreferences.getInt("iterations", MandelbrotCalculator.DEFAULT_ITERATIONS)
        power = sharedPreferences.getInt("power", MandelbrotCalculator.DEFAULT_POWER)
        currentColorScheme = sharedPreferences.getInt("colorscheme", 0)
        juliaEnabled = sharedPreferences.getBoolean("juliaEnabled", false)
    }

    fun save() {
        sharedPreferences.edit {
            putString("xcenter", xCenter.toString())
            putString("ycenter", yCenter.toString())
            putString("xextent", xExtent.toString())
            putInt("iterations", numIterations)
            putInt("power", power)
            putInt("colorscheme", currentColorScheme)
            putBoolean("juliaEnabled", juliaEnabled)
        }
    }
}

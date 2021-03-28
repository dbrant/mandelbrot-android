package com.dmitrybrant.android.mandelbrot

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MandelbrotActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val sharedPreferences = app.getSharedPreferences(PREFS_NAME, 0)

    var juliaEnabled = false
    var currentColorScheme = 0
    var xCenter = 0.0
    var yCenter = 0.0
    var xExtent = 0.0
    var numIterations = 0
    var power = 0

    init {
        xCenter = sharedPreferences.getString("xcenter", MandelbrotViewBase.DEFAULT_X_CENTER.toString())!!.toDouble()
        yCenter = sharedPreferences.getString("ycenter", MandelbrotViewBase.DEFAULT_Y_CENTER.toString())!!.toDouble()
        xExtent = sharedPreferences.getString("xextent", MandelbrotViewBase.DEFAULT_X_EXTENT.toString())!!.toDouble()
        numIterations = sharedPreferences.getInt("iterations", MandelbrotViewBase.DEFAULT_ITERATIONS)
        power = sharedPreferences.getInt("power", MandelbrotViewBase.DEFAULT_POWER)
        currentColorScheme = sharedPreferences.getInt("colorscheme", 0)
        juliaEnabled = sharedPreferences.getBoolean("juliaEnabled", false)
    }

    fun save() {
        val editor = sharedPreferences.edit()
        editor.putString("xcenter", xCenter.toString())
        editor.putString("ycenter", yCenter.toString())
        editor.putString("xextent", xExtent.toString())
        editor.putInt("iterations", numIterations)
        editor.putInt("power", power)
        editor.putInt("colorscheme", currentColorScheme)
        editor.putBoolean("juliaEnabled", juliaEnabled)
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "MandelbrotActivityPrefs"
    }
}

package com.dmitrybrant.android.mandelbrot

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MandelbrotActivityViewModel(app: Application) : AndroidViewModel(app) {
    private val sharedPreferences = app.getSharedPreferences("MandelbrotActivityPrefs", 0)

    init {

    }

    fun save() {

    }
}

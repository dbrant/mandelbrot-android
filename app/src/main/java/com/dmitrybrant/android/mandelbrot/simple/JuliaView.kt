package com.dmitrybrant.android.mandelbrot.simple

import android.content.Context
import android.util.AttributeSet

class JuliaView(context: Context, attrs: AttributeSet? = null) :
        MandelbrotViewBase(context, attrs) {
    init {
        super.setup(true)
    }
}

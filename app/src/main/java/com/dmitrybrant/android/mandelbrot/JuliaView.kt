package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.util.AttributeSet

class JuliaView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        MandelbrotViewBase(context, attrs, defStyleAttr) {
    init {
        if (!isInEditMode) {
            super.setup(true)
        }
    }
}

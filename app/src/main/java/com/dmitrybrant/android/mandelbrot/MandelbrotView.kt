package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.util.AttributeSet

class MandelbrotView : MandelbrotViewBase {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        if (isInEditMode) {
            return
        }
        super.setup(false)
    }
}
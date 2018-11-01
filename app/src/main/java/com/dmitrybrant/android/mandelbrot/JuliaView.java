package com.dmitrybrant.android.mandelbrot;

import android.util.AttributeSet;
import android.content.Context;

public class JuliaView extends MandelbrotViewBase {

    public JuliaView(Context context) {
        super(context);
        init();
    }

    public JuliaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JuliaView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init() {
        if (isInEditMode()) {
            return;
        }
        super.init(true);
    }
}

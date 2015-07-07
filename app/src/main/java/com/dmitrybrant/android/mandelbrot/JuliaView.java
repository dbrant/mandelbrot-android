package com.dmitrybrant.android.mandelbrot;

import android.content.Context;
import android.util.AttributeSet;

public class JuliaView extends MandelbrotViewBase {

    public JuliaView(Context context) {
        super(context);
        init(context);
    }

    public JuliaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public JuliaView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    protected void init(Context context) {
        if (isInEditMode()) {
            return;
        }
        super.init(context, true);
    }
}

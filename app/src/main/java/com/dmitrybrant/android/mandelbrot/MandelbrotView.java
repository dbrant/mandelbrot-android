package com.dmitrybrant.android.mandelbrot;

import android.util.AttributeSet;
import android.content.Context;

public class MandelbrotView extends MandelbrotViewBase {

    public MandelbrotView(Context context) {
        super(context);
        init();
    }

    public MandelbrotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MandelbrotView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init() {
        if (isInEditMode()) {
            return;
        }
        super.init(false);
    }
}

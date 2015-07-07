package com.dmitrybrant.android.mandelbrot;

import android.content.Context;
import android.util.AttributeSet;

public class MandelbrotView extends MandelbrotViewBase {

    public MandelbrotView(Context context) {
        super(context);
        init(context);
    }

    public MandelbrotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MandelbrotView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    protected void init(Context context) {
        if (isInEditMode()) {
            return;
        }
        super.init(context, false);
    }
}

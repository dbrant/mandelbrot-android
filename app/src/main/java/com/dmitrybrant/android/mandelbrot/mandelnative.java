package com.dmitrybrant.android.mandelbrot;

import android.graphics.Bitmap;

public class mandelnative {
	  public final static native void SetParameters(int jarg1, double jarg2, double jarg3, double jarg4, double jarg5, double jarg6, double jarg7, int jarg8, int jarg9, int jarg10);
	  public final static native void ReleaseParameters();
	  public final static native void MandelbrotPixels(int jarg1, int jarg2, int jarg3, int jarg4, int jarg5, int jarg6);
	  public final static native void JuliaPixels(int jarg1, int jarg2, int jarg3, int jarg4, int jarg5, int jarg6);
	  public final static native void SetBitmap(Bitmap bmp);
	  public final static native void UpdateBitmap(Bitmap bmp);
	  public final static native void ReleaseBitmap();
	  public final static native void SetColorPalette(int[] colors, int numColors);
	  public final static native void SignalTerminate();
}

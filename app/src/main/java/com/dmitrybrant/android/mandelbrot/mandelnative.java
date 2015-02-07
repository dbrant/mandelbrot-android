package com.dmitrybrant.android.mandelbrot;

import android.graphics.Bitmap;

public class mandelnative {
	  public static native void SetParameters(int jarg1, double jarg2, double jarg3, double jarg4, double jarg5, double jarg6, double jarg7, int jarg8, int jarg9, int jarg10);
	  public static native void ReleaseParameters();
	  public static native void MandelbrotPixels(int jarg1, int jarg2, int jarg3, int jarg4, int jarg5, int jarg6);
	  public static native void JuliaPixels(int jarg1, int jarg2, int jarg3, int jarg4, int jarg5, int jarg6);
	  public static native void SetBitmap(Bitmap bmp);
	  public static native void UpdateBitmap(Bitmap bmp);
	  public static native void ReleaseBitmap();
	  public static native void SetColorPalette(int[] colors, int numColors);
	  public static native void SignalTerminate();
}

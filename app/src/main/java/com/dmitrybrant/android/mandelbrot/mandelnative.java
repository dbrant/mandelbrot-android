package com.dmitrybrant.android.mandelbrot;

import android.graphics.Bitmap;

public class mandelnative {
	  public static native void SetParameters(int paramIndex, int jarg1, double jarg2, double jarg3, double jarg4, double jarg5, int isJulia, double jarg6, double jarg7, int jarg8, int jarg9);
	  public static native void ReleaseParameters(int paramIndex);
	  public static native void DrawFractal(int paramIndex, int jarg1, int jarg2, int jarg3, int jarg4, int jarg5, int jarg6);
	  public static native void SetBitmap(int paramIndex, Bitmap bmp);
	  public static native void UpdateBitmap(int paramIndex, Bitmap bmp);
	  public static native void ReleaseBitmap(int paramIndex);
	  public static native void SetColorPalette(int paramIndex, int[] colors, int numColors);
	  public static native void SignalTerminate(int paramIndex);
}

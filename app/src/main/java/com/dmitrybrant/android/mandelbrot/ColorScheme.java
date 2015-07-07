package com.dmitrybrant.android.mandelbrot;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

public final class ColorScheme {

    private static List<int[]> colorSchemes;

    public static void initColorSchemes() {
        colorSchemes = new ArrayList<>();
        colorSchemes.add(createColorScheme(new int[]{Color.BLUE, Color.GREEN, Color.RED, Color.BLUE}, 256));
        colorSchemes.add(createColorScheme(new int[]{Color.YELLOW, Color.MAGENTA, Color.BLUE, Color.GREEN, Color.YELLOW}, 256));
        colorSchemes.add(createColorScheme(new int[]{Color.WHITE, Color.BLACK, Color.WHITE}, 256));
        colorSchemes.add(createColorScheme(new int[]{Color.BLACK, Color.WHITE, Color.BLACK}, 256));
        colorSchemes.add(new int[]{Color.BLACK, Color.WHITE});
    }

    public static List<int[]> getColorSchemes() {
        return colorSchemes;
    }

    public static int[] getShiftedScheme(int[] colors, int shiftAmount) {
        int[] shifted = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            shifted[i] = colors[(i + shiftAmount) % colors.length];
        }
        return shifted;
    }

    private static int[] createColorScheme(int[] colorArray, int numElements) {
        int elementsPerStep = numElements / (colorArray.length - 1);
        int[] colors = new int[numElements];

        float r = 0f, g = 0f, b = 0f;
        float rInc = 0f, gInc = 0f, bInc = 0f;
        int cIndex = 0;
        int cCounter = 0;

        for (int i = 0; i < numElements; i++) {
            if (cCounter == 0) {
                b = colorArray[cIndex] & 0xff;
                g = (colorArray[cIndex] & 0xff00) >> 8;
                r = (colorArray[cIndex] & 0xff0000) >> 16;
                if (cIndex < colorArray.length - 1) {
                    bInc = ((float) (colorArray[cIndex + 1] & 0xff) - b) / (float) elementsPerStep;
                    gInc = ((float) ((colorArray[cIndex + 1] & 0xff00) >> 8) - g) / (float) elementsPerStep;
                    rInc = ((float) ((colorArray[cIndex + 1] & 0xff0000) >> 16) - r) / (float) elementsPerStep;
                }
                cIndex++;
                cCounter = elementsPerStep;
            }
            colors[i] = 0xff000000 | ((int) b << 16) | ((int) g << 8) | ((int) r);
            b = b + bInc;
            g = g + gInc;
            r = r + rInc;
            if (b < 0f) {
                b = 0f;
            }
            if (g < 0f) {
                g = 0f;
            }
            if (r < 0f) {
                r = 0f;
            }
            if (b > 255f) {
                b = 255f;
            }
            if (g > 255f) {
                g = 255f;
            }
            if (r > 255f) {
                r = 255f;
            }
            cCounter--;
        }
        return colors;
    }
}

package com.dmitrybrant.android.mandelbrot

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.Gravity
import kotlin.math.pow

object GradientUtil {
    private const val GRADIENT_NUM_STOPS = 8
    private const val GRADIENT_POWER = 3
    fun getCubicGradient(baseColor: Int, gravity: Int): Drawable {
        val drawable = PaintDrawable()
        drawable.shape = RectShape()
        setCubicGradient(drawable, baseColor, gravity)
        return drawable
    }

    /**
     * Create a cubic gradient by using a compound gradient composed of a series of linear
     * gradients with intermediate color values.
     * adapted from: https://github.com/romannurik/muzei/blob/master/main/src/main/java/com/google/android/apps/muzei/util/ScrimUtil.java
     * @param baseColor The color from which the gradient starts (the ending color is transparent).
     * @param gravity Where the gradient should start from. Note: when making horizontal gradients,
     * remember to use START/END, instead of LEFT/RIGHT.
     */
    private fun setCubicGradient(drawable: PaintDrawable, baseColor: Int, gravity: Int) {
        val stopColors = IntArray(GRADIENT_NUM_STOPS)
        val red = Color.red(baseColor)
        val green = Color.green(baseColor)
        val blue = Color.blue(baseColor)
        val alpha = Color.alpha(baseColor)
        for (i in 0 until GRADIENT_NUM_STOPS) {
            val x = i * 1f / (GRADIENT_NUM_STOPS - 1)
            var opacity = x.toDouble().pow(GRADIENT_POWER.toDouble()).toFloat()
            if (opacity < 0f) {
                opacity = 0f
            } else if (opacity > 1f) {
                opacity = 1f
            }
            stopColors[i] = Color.argb((alpha * opacity).toInt(), red, green, blue)
        }
        val x0: Float
        val x1: Float
        val y0: Float
        val y1: Float
        when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
            Gravity.START -> {
                x0 = 1f
                x1 = 0f
            }
            Gravity.END -> {
                x0 = 0f
                x1 = 1f
            }
            else -> {
                x0 = 0f
                x1 = 0f
            }
        }
        when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.TOP -> {
                y0 = 1f
                y1 = 0f
            }
            Gravity.BOTTOM -> {
                y0 = 0f
                y1 = 1f
            }
            else -> {
                y0 = 0f
                y1 = 0f
            }
        }
        drawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
            override fun resize(width: Int, height: Int): Shader {
                return LinearGradient(width * x0, height * y0, width * x1, height * y1,
                        stopColors, null, Shader.TileMode.CLAMP)
            }
        }
    }
}
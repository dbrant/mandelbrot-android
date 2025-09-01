package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import java.math.BigDecimal

class MandelGLView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    private val renderer: MandelbrotRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun reset() {
        renderer.iterations = 1000
        renderer.cmapScale = 20f
        renderer.centerRe = "0"; renderer.centerIm = "0"; renderer.radius = "2"
        renderer.uploadOrbit()
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Single-tap “zoom in” to match your click handler (dcx,dcy mapping). :contentReference[oaicite:9]{index=9}
        if (e.action == MotionEvent.ACTION_UP) {
            val w = width.toFloat()
            val h = height.toFloat()
            val nx = (e.x / (w/2f)) - 1f
            val ny = (e.y / (h/2f)) - 1f

            // Update state: center += radius * (nx, -ny); radius /= 2  (like JS update()) :contentReference[oaicite:10]{index=10}
            // Here we keep strings; in production keep BigDecimal and stringify for JNI.
            val r = renderer.radius.toBigDecimal()
            fun s(d: BigDecimal) = d.stripTrailingZeros().toPlainString()
            renderer.centerRe = s(renderer.centerRe.toBigDecimal() + r * nx.toBigDecimal())
            renderer.centerIm = s(renderer.centerIm.toBigDecimal() + r * (-ny).toBigDecimal())
            renderer.radius = s(r.divide(BigDecimal(2)))

            renderer.uploadOrbit()
            requestRender()
        }
        return true
    }

    fun setIterations(it: Int) {
        renderer.iterations = it;
        renderer.uploadOrbit();
        requestRender()
    }

    fun setCmapScale(s: Float) {
        renderer.cmapScale = s;
        requestRender()
    }
}

package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class MandelGLView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    private val renderer: MandelbrotRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun reset() {
        // TODO
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Single-tap “zoom in” to match your click handler (dcx,dcy mapping). :contentReference[oaicite:9]{index=9}
        if (e.action == MotionEvent.ACTION_UP) {
            // TODO
            requestRender()
        }
        return true
    }

    fun setIterations(it: Int) {

        requestRender()
    }

    fun setCmapScale(s: Float) {

        requestRender()
    }
}

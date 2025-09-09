package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class MandelGLView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    interface Callback {
        fun onUpdateState(centerX: String, centerY: String, radius: String, iterations: Int, colorScale: Int)
    }

    private val renderer: MandelbrotRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onDetachedFromWindow() {
        renderer.cleanup()
        super.onDetachedFromWindow()
    }

    fun zoomOut(factor: Double) {
        renderer.zoomOut(factor)
        requestRender()
    }

    fun reset() {
        renderer.reset()
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            renderer.handleTouch(e.x, e.y, width, height)
            requestRender()
        }
        return true
    }

    fun setIterations(iterations: Int) {
        renderer.setIterations(iterations)
        requestRender()
    }

    fun setCmapScale(scale: Double) {
        renderer.setCmapScale(scale)
        requestRender()
    }
}

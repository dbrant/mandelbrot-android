package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs

class MandelGLView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    interface Callback {
        fun onUpdateState(centerX: String, centerY: String, radius: String, iterations: Int, colorScale: Float)
    }

    private val renderer: MandelbrotRenderer
    var callback: Callback? = null

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchSlop = 16f * resources.displayMetrics.density

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun initState(centerX: String, centerY: String, radius: String, iterations: Int, colorScale: Float) {
        renderer.mandelbrotState?.set(centerX, centerY, radius, iterations)
        renderer.colorMapScale = colorScale
    }

    override fun onDetachedFromWindow() {
        renderer.cleanup()
        super.onDetachedFromWindow()
    }

    private fun doCallback() {
        renderer.mandelbrotState?.let { state ->
            callback?.onUpdateState(state.centerX, state.centerY, state.radius, state.numIterations, renderer.colorMapScale)
        }
    }

    fun zoomOut(factor: Double) {
        renderer.zoomOut(factor)
        requestRender()
        doCallback()
    }

    fun reset() {
        renderer.reset()
        requestRender()
        doCallback()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            touchDownX = e.x
            touchDownY = e.y
        } else if (e.action == MotionEvent.ACTION_UP) {
            if (abs(e.x - touchDownX) < touchSlop && abs(e.y - touchDownY) < touchSlop) {
                renderer.handleTouch(e.x, e.y, width, height)
                requestRender()
                doCallback()
            }
        }
        return true
    }

    fun setIterations(iterations: Int) {
        renderer.setIterations(iterations)
        requestRender()
        doCallback()
    }

    fun setCmapScale(scale: Float) {
        renderer.colorMapScale = scale
        requestRender()
        doCallback()
    }
}

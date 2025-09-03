package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.max
import kotlin.math.min

class MandelGLView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    private val renderer: MandelbrotRenderer
    private val scaleGestureDetector: ScaleGestureDetector
    
    // Current transformation state
    private var currentScale = 1.0f
    private var currentTranslateX = 0.0f
    private var currentTranslateY = 0.0f
    private var isGestureActive = false

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY // Change to continuous for smooth gesture feedback
        
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
    }

    override fun onDetachedFromWindow() {
        renderer.cleanup()
        super.onDetachedFromWindow()
    }

    fun reset() {
        // TODO
        requestRender()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(e)
        
        // Handle single tap only if no gesture is active
        if (!isGestureActive && e.action == MotionEvent.ACTION_UP && e.pointerCount == 1) {
            renderer.handleTouch(e.x, e.y, width, height)
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
    
    // Methods to get current transformation for the renderer
    fun getCurrentTransformation(): FloatArray {
        return floatArrayOf(currentScale, currentTranslateX, currentTranslateY)
    }
    
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var initialFocusX = 0f
        private var initialFocusY = 0f
        private var initialTranslateX = 0f
        private var initialTranslateY = 0f
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isGestureActive = true
            initialFocusX = detector.focusX
            initialFocusY = detector.focusY
            initialTranslateX = currentTranslateX
            initialTranslateY = currentTranslateY
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Update scale with constraints
            val scaleFactor = detector.scaleFactor
            currentScale = max(0.1f, min(currentScale * scaleFactor, 10.0f))
            
            // Calculate translation to keep focus point centered during scaling
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            // Convert screen coordinates to normalized coordinates (-1 to 1)
            val normalizedFocusX = (focusX / (width / 2.0f)) - 1.0f
            val normalizedFocusY = -((focusY / (height / 2.0f)) - 1.0f) // Flip Y for OpenGL
            
            // Calculate how much to translate to keep focus point stationary
            val focusOffsetX = normalizedFocusX * (currentScale - 1.0f)
            val focusOffsetY = normalizedFocusY * (currentScale - 1.0f)
            
            // Apply translation
            currentTranslateX = initialTranslateX - focusOffsetX
            currentTranslateY = initialTranslateY - focusOffsetY
            
            // Update renderer with new transformation
            renderer.setTransformation(currentScale, currentTranslateX, currentTranslateY)
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isGestureActive = false
            // Optionally switch back to RENDERMODE_WHEN_DIRTY here for better performance
            // renderMode = RENDERMODE_WHEN_DIRTY
        }
    }
}

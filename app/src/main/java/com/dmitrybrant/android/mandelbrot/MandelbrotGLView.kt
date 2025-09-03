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
    
    // Gesture state
    private var isGestureActive = false
    private var gestureStartScale = 1.0f
    private var gestureStartFocusX = 0.0f
    private var gestureStartFocusY = 0.0f
    private var currentScale = 1.0f
    private var currentTranslationX = 0.0f
    private var currentTranslationY = 0.0f

    init {
        setEGLContextClientVersion(3)
        renderer = MandelbrotRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        
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

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isGestureActive = true
            gestureStartScale = currentScale
            gestureStartFocusX = detector.focusX
            gestureStartFocusY = detector.focusY
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Update scale with constraints
            val scaleFactor = detector.scaleFactor
            currentScale = max(0.1f, min(currentScale * scaleFactor, 10.0f))
            
            // Calculate translation to keep focus point centered
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            // Apply visual transformation to the view
            scaleX = currentScale
            scaleY = currentScale
            
            // Calculate translation to keep the focus point stationary during scaling
            val deltaX = (focusX - gestureStartFocusX) * (1 - currentScale)
            val deltaY = (focusY - gestureStartFocusY) * (1 - currentScale)
            
            translationX = deltaX + (gestureStartFocusX - width/2f) * (currentScale - 1f)
            translationY = deltaY + (gestureStartFocusY - height/2f) * (currentScale - 1f)
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isGestureActive = false
            
            // Calculate the final zoom and pan parameters
            val finalScale = currentScale
            val finalTranslationX = translationX
            val finalTranslationY = translationY
            
            // Reset visual transformation
            scaleX = 1.0f
            scaleY = 1.0f
            translationX = 0.0f
            translationY = 0.0f
            currentScale = 1.0f
            currentTranslationX = 0.0f
            currentTranslationY = 0.0f
            
            // Apply the transformation to the underlying Mandelbrot parameters
            applyGestureToMandelbrot(finalScale, finalTranslationX, finalTranslationY, detector.focusX, detector.focusY)
            
            requestRender()
        }
    }
    
    private fun applyGestureToMandelbrot(scale: Float, translationX: Float, translationY: Float, focusX: Float, focusY: Float) {
        // Convert screen coordinates to normalized coordinates (-1 to 1)
        val normalizedFocusX = (focusX / (width / 2.0f) - 1.0f).toDouble()
        val normalizedFocusY = (focusY / (height / 2.0f) - 1.0f).toDouble()
        
        // Convert translation to normalized coordinates
        val normalizedTranslationX = (translationX / (width / 2.0f)).toDouble()
        val normalizedTranslationY = -(translationY / (height / 2.0f)).toDouble() // Flip Y
        
        // Apply pan first (translation)
        if (kotlin.math.abs(normalizedTranslationX) > 0.01 || kotlin.math.abs(normalizedTranslationY) > 0.01) {
            renderer.handlePan(normalizedTranslationX, normalizedTranslationY)
        }
        
        // Then apply zoom centered on the focus point
        if (kotlin.math.abs(scale - 1.0f) > 0.01f) {
            renderer.handleZoom(normalizedFocusX, normalizedFocusY, scale.toDouble())
        }
    }
}

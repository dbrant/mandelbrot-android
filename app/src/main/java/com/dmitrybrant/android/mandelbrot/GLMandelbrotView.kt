package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * OpenGL Surface View for rendering the Mandelbrot set using shaders
 */
class GLMandelbrotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    
    companion object {
        private const val TAG = "GLMandelbrotView"
        private const val ZOOM_FACTOR = 2.0
    }
    
    private val renderer: GLMandelbrotRenderer
    private val mathContext = MathContext(50, RoundingMode.HALF_UP)
    
    // Current view parameters
    private var centerX = BigDecimal("-0.5", mathContext)
    private var centerY = BigDecimal("0.0", mathContext)  
    private var radius = BigDecimal("2.0", mathContext)
    private var iterations = 1000
    
    // Touch handling
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    init {
        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)
        
        renderer = GLMandelbrotRenderer()
        setRenderer(renderer)
        
        // Render when the surface is created or touched
        renderMode = RENDERMODE_WHEN_DIRTY
        
        Log.d(TAG, "GLMandelbrotView initialized")
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                val touchX = event.x
                val touchY = event.y
                
                // Check if this was a tap (not a drag)
                val deltaX = abs(touchX - lastTouchX)
                val deltaY = abs(touchY - lastTouchY)
                
                if (deltaX < 50 && deltaY < 50) {
                    // Convert screen coordinates to complex plane
                    val viewWidth = width.toDouble()
                    val viewHeight = height.toDouble()
                    
                    // Normalize to [-1, 1] range (like the vertex shader)
                    val normalizedX = 2.0 * touchX / viewWidth - 1.0
                    val normalizedY = 1.0 - 2.0 * touchY / viewHeight // Flip Y
                    
                    // Convert to complex plane coordinates
                    val complexX = centerX.add(
                        radius.multiply(BigDecimal(normalizedX), mathContext), 
                        mathContext
                    )
                    val complexY = centerY.add(
                        radius.multiply(BigDecimal(normalizedY), mathContext),
                        mathContext
                    )
                    
                    // Zoom in on the clicked point
                    zoomAt(complexX, complexY, ZOOM_FACTOR)
                }
                return true
            }
        }
        return false
    }
    
    /**
     * Zoom in at the specified complex coordinates
     */
    fun zoomAt(newCenterX: BigDecimal, newCenterY: BigDecimal, zoomFactor: Double) {
        centerX = newCenterX
        centerY = newCenterY
        radius = radius.divide(BigDecimal(zoomFactor), mathContext)
        
        // Increase iterations for higher zoom levels
        val zoomLevel = 2.0 / radius.toDouble()
        iterations = max(1000, min(5000, (1000 * kotlin.math.log10(zoomLevel)).toInt()))
        
        Log.d(TAG, "Zoom to ($centerX, $centerY), radius=$radius, iterations=$iterations")
        
        // Update renderer and redraw
        queueEvent {
            renderer.setParameters(centerX, centerY, radius, iterations)
        }
        requestRender()
    }
    
    /**
     * Zoom out
     */
    fun zoomOut() {
        radius = radius.multiply(BigDecimal(ZOOM_FACTOR), mathContext)
        
        // Decrease iterations for lower zoom levels
        val zoomLevel = 2.0 / radius.toDouble()
        iterations = max(500, min(5000, (1000 * kotlin.math.log10(max(1.0, zoomLevel))).toInt()))
        
        Log.d(TAG, "Zoom out: radius=$radius, iterations=$iterations")
        
        queueEvent {
            renderer.setParameters(centerX, centerY, radius, iterations)
        }
        requestRender()
    }
    
    /**
     * Reset to initial view
     */
    fun resetView() {
        centerX = BigDecimal("-0.5", mathContext)
        centerY = BigDecimal("0.0", mathContext)
        radius = BigDecimal("2.0", mathContext)
        iterations = 1000
        
        Log.d(TAG, "Reset view")
        
        queueEvent {
            renderer.setParameters(centerX, centerY, radius, iterations)
        }
        requestRender()
    }
    
    /**
     * Navigate to a specific interesting location
     */
    fun navigateToLocation(x: String, y: String, radiusStr: String) {
        try {
            centerX = BigDecimal(x, mathContext)
            centerY = BigDecimal(y, mathContext)
            radius = BigDecimal(radiusStr, mathContext)
            
            // Set appropriate iterations for this zoom level
            val zoomLevel = 2.0 / radius.toDouble()
            iterations = max(1000, min(8000, (1000 * kotlin.math.log10(max(1.0, zoomLevel))).toInt()))
            
            Log.d(TAG, "Navigate to ($centerX, $centerY), radius=$radius, iterations=$iterations")
            
            queueEvent {
                renderer.setParameters(centerX, centerY, radius, iterations)
            }
            requestRender()
        } catch (e: Exception) {
            Log.e(TAG, "Invalid coordinates: $e")
        }
    }
    
    /**
     * Get current view info for sharing/debugging
     */
    fun getCurrentInfo(): String {
        return "Center: ($centerX, $centerY)\nRadius: $radius\nIterations: $iterations"
    }
}
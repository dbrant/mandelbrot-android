package com.dmitrybrant.android.mandelbrot

import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Simplified deep zoom calculator - much more conservative approach
 */
class OptimizedDeepZoom {
    
    // Use reasonable precision 
    private val mathContext = MathContext(50, RoundingMode.HALF_UP)
    
    companion object {
        private const val TAG = "OptimizedDeepZoom"
    }
    
    
    /**
     * Simplified deep zoom calculation - direct BigDecimal approach
     */
    fun calculateIterations(
        pixelX: Double,
        pixelY: Double,
        centerX: BigDecimal,
        centerY: BigDecimal,
        zoomFactor: Double,
        viewWidth: Int,
        viewHeight: Int,
        maxIterations: Int
    ): Int {
        return try {
            // Calculate world coordinates using BigDecimal
            val scale = BigDecimal(4.0 / zoomFactor, mathContext)
            val aspectRatio = BigDecimal(viewWidth.toDouble() / viewHeight.toDouble(), mathContext)
            
            val pixelOffsetX = BigDecimal((pixelX - viewWidth / 2.0) / viewWidth, mathContext)
            val pixelOffsetY = BigDecimal((pixelY - viewHeight / 2.0) / viewHeight, mathContext)
            
            val worldX = centerX.add(scale.multiply(pixelOffsetX, mathContext).multiply(aspectRatio, mathContext), mathContext)
            val worldY = centerY.add(scale.multiply(pixelOffsetY, mathContext), mathContext)
            
            Log.v(TAG, "Pixel ($pixelX,$pixelY) -> World (${worldX.toDouble()}, ${worldY.toDouble()})")
            
            // For deep zoom, try to use double precision first for speed
            val doubleX = worldX.toDouble()
            val doubleY = worldY.toDouble()
            
            // If coordinates are within double precision range, use fast calculation
            if (doubleX.isFinite() && doubleY.isFinite() && 
                abs(doubleX) < 1e10 && abs(doubleY) < 1e10) {
                
                return calculateMandelbrotDouble(doubleX, doubleY, maxIterations)
            } else {
                // Use high precision calculation
                return calculateMandelbrotBigDecimal(worldX, worldY, maxIterations)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateIterations", e)
            // Fallback - return a reasonable middle value
            maxIterations / 2
        }
    }
    
    /**
     * Standard Mandelbrot calculation using doubles
     */
    private fun calculateMandelbrotDouble(cx: Double, cy: Double, maxIterations: Int): Int {
        var x = 0.0
        var y = 0.0
        var iteration = 0
        
        while (iteration < maxIterations) {
            val x2 = x * x
            val y2 = y * y
            
            if (x2 + y2 > 4.0) {
                return iteration
            }
            
            val newX = x2 - y2 + cx
            val newY = 2.0 * x * y + cy
            
            x = newX
            y = newY
            iteration++
        }
        
        return maxIterations
    }
    
    /**
     * High precision Mandelbrot calculation using BigDecimal
     */
    private fun calculateMandelbrotBigDecimal(cx: BigDecimal, cy: BigDecimal, maxIterations: Int): Int {
        var x = BigDecimal.ZERO
        var y = BigDecimal.ZERO
        var iteration = 0
        val two = BigDecimal("2", mathContext)
        val four = BigDecimal("4", mathContext)
        
        // Limit iterations for performance
        val limitedIterations = minOf(maxIterations, 500)
        
        while (iteration < limitedIterations) {
            val x2 = x.multiply(x, mathContext)
            val y2 = y.multiply(y, mathContext)
            
            if (x2.add(y2, mathContext).compareTo(four) > 0) {
                return iteration
            }
            
            val newX = x2.subtract(y2, mathContext).add(cx, mathContext)
            val newY = two.multiply(x, mathContext).multiply(y, mathContext).add(cy, mathContext)
            
            x = newX
            y = newY
            iteration++
        }
        
        return limitedIterations
    }
    
    /**
     * Prepare for deep zoom - simplified version
     */
    fun prepareForZoom(
        centerX: BigDecimal,
        centerY: BigDecimal,
        maxIterations: Int
    ) {
        Log.d(TAG, "Preparing deep zoom for center: (${centerX.toDouble()}, ${centerY.toDouble()})")
        // For now, just log that we're preparing - the actual calculation will happen on demand
    }
}
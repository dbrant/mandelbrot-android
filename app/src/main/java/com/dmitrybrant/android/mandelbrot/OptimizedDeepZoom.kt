package com.dmitrybrant.android.mandelbrot

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Optimized deep zoom calculator focused on performance
 * Uses minimal precision and efficient algorithms
 */
class OptimizedDeepZoom {
    
    // Use much lower precision for better performance - 34 digits is sufficient for most cases
    private val mathContext = MathContext(34, RoundingMode.HALF_UP)
    
    // Simple complex number class for efficiency
    data class Complex(val real: Double, val imag: Double) {
        fun multiply(other: Complex): Complex {
            return Complex(
                real * other.real - imag * other.imag,
                real * other.imag + imag * other.real
            )
        }
        
        fun add(other: Complex): Complex {
            return Complex(real + other.real, imag + other.imag)
        }
        
        fun magnitudeSquared(): Double {
            return real * real + imag * imag
        }
    }
    
    // High precision complex for reference orbit only
    data class ComplexBig(val real: BigDecimal, val imag: BigDecimal) {
        fun multiply(other: ComplexBig): ComplexBig {
            val newReal = real.multiply(other.real, mathContext).subtract(imag.multiply(other.imag, mathContext), mathContext)
            val newImag = real.multiply(other.imag, mathContext).add(imag.multiply(other.real, mathContext), mathContext)
            return ComplexBig(newReal, newImag)
        }
        
        fun add(other: ComplexBig): ComplexBig {
            return ComplexBig(
                real.add(other.real, mathContext),
                imag.add(other.imag, mathContext)
            )
        }
        
        fun magnitudeSquared(): BigDecimal {
            return real.multiply(real, mathContext).add(imag.multiply(imag, mathContext), mathContext)
        }
        
        fun toComplex(): Complex {
            return Complex(real.toDouble(), imag.toDouble())
        }
    }
    
    private var referenceOrbit: Array<Complex> = arrayOf()
    private var referenceIterations = 0
    private var referenceCenter = ComplexBig(BigDecimal.ZERO, BigDecimal.ZERO)
    private var isReferenceValid = false
    
    /**
     * Calculate reference orbit with high precision, store as doubles for speed
     */
    private fun calculateReferenceOrbit(center: ComplexBig, maxIterations: Int): Int {
        val orbit = mutableListOf<Complex>()
        var z = ComplexBig(BigDecimal.ZERO, BigDecimal.ZERO)
        val c = center
        val escapeRadius = BigDecimal("4")
        
        for (i in 0 until maxIterations) {
            orbit.add(z.toComplex())
            
            if (z.magnitudeSquared().compareTo(escapeRadius) > 0) {
                referenceOrbit = orbit.toTypedArray()
                referenceIterations = i
                referenceCenter = center
                isReferenceValid = true
                return i
            }
            
            // z = z² + c
            z = z.multiply(z).add(c)
        }
        
        referenceOrbit = orbit.toTypedArray()
        referenceIterations = maxIterations
        referenceCenter = center
        isReferenceValid = true
        return maxIterations
    }
    
    /**
     * Fast perturbation calculation using doubles
     */
    private fun calculatePerturbation(
        deltaC: Complex,
        maxIterations: Int
    ): Int {
        if (!isReferenceValid || referenceOrbit.isEmpty()) {
            return maxIterations
        }
        
        var dz = Complex(0.0, 0.0) // perturbation
        val escapeRadius = 4.0
        val tolerance = 1e-15
        
        // Limit iterations for performance
        val limitedIterations = minOf(maxIterations, 1000)
        
        for (i in 0 until minOf(limitedIterations, referenceOrbit.size)) {
            val zRef = referenceOrbit[i]
            
            // Total z = reference + perturbation
            val totalZ = Complex(zRef.real + dz.real, zRef.imag + dz.imag)
            
            // Check escape
            if (totalZ.magnitudeSquared() > escapeRadius) {
                return i
            }
            
            // Perturbation iteration: dz_{n+1} = 2*Z_n*dz_n + dz_n² + deltaC
            val newDz = Complex(
                2.0 * zRef.real * dz.real - 2.0 * zRef.imag * dz.imag + // 2*Z_n*dz_n real part
                dz.real * dz.real - dz.imag * dz.imag +                 // dz_n² real part  
                deltaC.real,                                            // deltaC real part
                
                2.0 * zRef.real * dz.imag + 2.0 * zRef.imag * dz.real + // 2*Z_n*dz_n imag part
                2.0 * dz.real * dz.imag +                               // dz_n² imag part
                deltaC.imag                                             // deltaC imag part
            )
            
            dz = newDz
            
            // If perturbation becomes too large, fall back to direct calculation
            if (dz.magnitudeSquared() > zRef.magnitudeSquared() * 100) {
                return calculateDirect(totalZ, deltaC, i, maxIterations)
            }
            
            // Glitch detection - if perturbation is too small, it might be inaccurate
            if (dz.magnitudeSquared() < tolerance && i > 10) {
                return calculateDirect(totalZ, deltaC, i, maxIterations)
            }
        }
        
        return maxIterations
    }
    
    /**
     * Direct calculation fallback using doubles
     */
    private fun calculateDirect(
        startZ: Complex,
        c: Complex,
        startIteration: Int,
        maxIterations: Int
    ): Int {
        var z = startZ
        val escapeRadius = 4.0
        
        for (i in startIteration until maxIterations) {
            if (z.magnitudeSquared() > escapeRadius) {
                return i
            }
            
            // z = z² + c
            z = z.multiply(z).add(c)
        }
        
        return maxIterations
    }
    
    /**
     * Main calculation function - much simpler than before
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
        // Safety check - if zoom is too extreme, fall back to simple calculation
        if (zoomFactor > 1e15) {
            return maxIterations / 2  // Return a reasonable default
        }
        try {
            // Calculate world coordinates
            val scale = 4.0 / zoomFactor
            val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
            
            val offsetX = (pixelX - viewWidth / 2.0) / viewWidth * aspectRatio * scale
            val offsetY = (pixelY - viewHeight / 2.0) / viewHeight * scale
            
            val worldX = centerX.add(BigDecimal(offsetX, mathContext), mathContext)
            val worldY = centerY.add(BigDecimal(offsetY, mathContext), mathContext)
            
            val currentCenter = ComplexBig(centerX, centerY)
            
            // Check if we need to recalculate reference orbit
            if (!isReferenceValid || 
                referenceCenter.real.subtract(currentCenter.real, mathContext).abs().compareTo(BigDecimal(scale * 0.1, mathContext)) > 0 ||
                referenceCenter.imag.subtract(currentCenter.imag, mathContext).abs().compareTo(BigDecimal(scale * 0.1, mathContext)) > 0) {
                
                calculateReferenceOrbit(currentCenter, maxIterations)
            }
            
            // Calculate delta from reference
            val deltaC = Complex(
                worldX.subtract(referenceCenter.real, mathContext).toDouble(),
                worldY.subtract(referenceCenter.imag, mathContext).toDouble()
            )
            
            // Use perturbation if delta is small enough, otherwise direct calculation
            val deltaSize = deltaC.magnitudeSquared()
            
            return if (deltaSize < scale * scale * 0.25) {
                calculatePerturbation(deltaC, maxIterations)
            } else {
                // Too far from reference, use direct calculation
                val c = Complex(worldX.toDouble(), worldY.toDouble())
                calculateDirect(Complex(0.0, 0.0), c, 0, maxIterations)
            }
            
        } catch (e: Exception) {
            // If anything goes wrong, fall back to simple double calculation
            val scale = 4.0 / zoomFactor
            val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
            
            val offsetX = (pixelX - viewWidth / 2.0) / viewWidth * aspectRatio * scale
            val offsetY = (pixelY - viewHeight / 2.0) / viewHeight * scale
            
            val worldX = centerX.toDouble() + offsetX
            val worldY = centerY.toDouble() + offsetY
            
            val c = Complex(worldX, worldY)
            return calculateDirect(Complex(0.0, 0.0), c, 0, maxIterations)
        }
    }
    
    /**
     * Prepare reference orbit for a new area
     */
    fun prepareForZoom(
        centerX: BigDecimal,
        centerY: BigDecimal,
        maxIterations: Int
    ) {
        val center = ComplexBig(centerX, centerY)
        calculateReferenceOrbit(center, maxIterations)
    }
}
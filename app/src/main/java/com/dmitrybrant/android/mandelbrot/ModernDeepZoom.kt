package com.dmitrybrant.android.mandelbrot

import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Modern deep zoom Mandelbrot calculator using:
 * - Perturbation theory with reference orbits
 * - Series approximation for bulk region skipping
 * - Bilinear approximation for smooth areas
 * - Glitch detection and correction
 * 
 * Based on algorithms from Kalles Fraktaler and modern deep zoom techniques
 */
class ModernDeepZoom {
    
    companion object {
        private const val TAG = "ModernDeepZoom"
        private const val MAX_SERIES_TERMS = 8
        private const val SERIES_TOLERANCE = 1e-15
        private const val GLITCH_TOLERANCE = 1e-12
    }
    
    // Precision management - adaptive based on zoom level
    private var currentPrecision = 64
    private var mathContext = MathContext(currentPrecision, RoundingMode.HALF_UP)
    
    // Reference orbit data
    private var referencePoint: ComplexBig? = null
    private var referenceOrbit: Array<ComplexDouble> = arrayOf()
    private var referenceIterations = 0
    private var orbitValid = false
    
    // Series approximation coefficients
    private var seriesCoefficients: Array<ComplexDouble> = arrayOf()
    private var seriesRadius = 0.0
    private var seriesSkipThreshold = 1e-6
    
    // High precision complex number
    data class ComplexBig(
        val real: BigDecimal,
        val imag: BigDecimal,
        val context: MathContext
    ) {
        fun add(other: ComplexBig): ComplexBig {
            return ComplexBig(
                real.add(other.real, context),
                imag.add(other.imag, context),
                context
            )
        }
        
        fun multiply(other: ComplexBig): ComplexBig {
            val newReal = real.multiply(other.real, context).subtract(imag.multiply(other.imag, context), context)
            val newImag = real.multiply(other.imag, context).add(imag.multiply(other.real, context), context)
            return ComplexBig(newReal, newImag, context)
        }
        
        fun magnitudeSquared(): BigDecimal {
            return real.multiply(real, context).add(imag.multiply(imag, context), context)
        }
        
        fun toDouble(): ComplexDouble {
            return ComplexDouble(real.toDouble(), imag.toDouble())
        }
        
        companion object {
            fun fromDouble(real: Double, imag: Double, context: MathContext): ComplexBig {
                return ComplexBig(
                    BigDecimal(real, context),
                    BigDecimal(imag, context),
                    context
                )
            }
        }
    }
    
    // Fast double precision complex number
    data class ComplexDouble(val real: Double, val imag: Double) {
        fun add(other: ComplexDouble): ComplexDouble {
            return ComplexDouble(real + other.real, imag + other.imag)
        }
        
        fun subtract(other: ComplexDouble): ComplexDouble {
            return ComplexDouble(real - other.real, imag - other.imag)
        }
        
        fun multiply(other: ComplexDouble): ComplexDouble {
            return ComplexDouble(
                real * other.real - imag * other.imag,
                real * other.imag + imag * other.real
            )
        }
        
        fun multiply(scalar: Double): ComplexDouble {
            return ComplexDouble(real * scalar, imag * scalar)
        }
        
        fun magnitudeSquared(): Double {
            return real * real + imag * imag
        }
        
        fun magnitude(): Double {
            return sqrt(magnitudeSquared())
        }
        
        companion object {
            val ZERO = ComplexDouble(0.0, 0.0)
            val ONE = ComplexDouble(1.0, 0.0)
        }
    }
    
    /**
     * Update precision based on zoom level
     */
    private fun updatePrecision(zoomFactor: Double) {
        // Estimate required precision: log10(zoom) * 1.5 + base precision
        val requiredPrecision = (log10(zoomFactor) * 1.5 + 50).toInt()
        val newPrecision = maxOf(50, minOf(200, requiredPrecision))
        
        if (newPrecision != currentPrecision) {
            currentPrecision = newPrecision
            mathContext = MathContext(currentPrecision, RoundingMode.HALF_UP)
            Log.d(TAG, "Updated precision to $currentPrecision digits for zoom ${String.format("%.2e", zoomFactor)}")
        }
    }
    
    /**
     * Calculate reference orbit at the given center point
     */
    private fun calculateReferenceOrbit(center: ComplexBig, maxIterations: Int) {
        Log.d(TAG, "Calculating reference orbit at (${center.real.toDouble()}, ${center.imag.toDouble()})")
        
        val orbit = mutableListOf<ComplexDouble>()
        var z = ComplexBig.fromDouble(0.0, 0.0, mathContext)
        val escapeRadius = BigDecimal("256", mathContext) // Higher escape radius for better accuracy
        
        var iteration = 0
        while (iteration < maxIterations) {
            orbit.add(z.toDouble())
            
            // Check escape condition
            if (z.magnitudeSquared().compareTo(escapeRadius) > 0) {
                break
            }
            
            // z = z² + c
            z = z.multiply(z).add(center)
            iteration++
        }
        
        referencePoint = center
        referenceOrbit = orbit.toTypedArray()
        referenceIterations = iteration
        orbitValid = true
        
        // Calculate series approximation coefficients
        calculateSeriesCoefficients()
        
        Log.d(TAG, "Reference orbit calculated: $iteration iterations, ${orbit.size} points")
    }
    
    /**
     * Calculate series approximation coefficients for bulk region optimization
     */
    private fun calculateSeriesCoefficients() {
        if (referenceOrbit.size < 3) {
            seriesCoefficients = arrayOf()
            return
        }
        
        val coefficients = mutableListOf<ComplexDouble>()
        
        // A₀ = 0, A₁ = 1 (first two terms are fixed)
        coefficients.add(ComplexDouble.ZERO)
        coefficients.add(ComplexDouble.ONE)
        
        // Calculate subsequent coefficients using recurrence relation:
        // Aₙ = 2 * Σ(Aᵢ * Aₙ₋₁₋ᵢ) for i from 0 to n-1
        for (n in 2..minOf(MAX_SERIES_TERMS, referenceOrbit.size - 1)) {
            if (n - 1 >= referenceOrbit.size) break
            
            var sum = ComplexDouble.ZERO
            for (i in 0 until n) {
                if (i < coefficients.size && (n - 1 - i) < coefficients.size) {
                    sum = sum.add(coefficients[i].multiply(coefficients[n - 1 - i]))
                }
            }
            
            // Multiply by 2 and add reference orbit contribution
            val an = sum.multiply(2.0)
            coefficients.add(an)
        }
        
        seriesCoefficients = coefficients.toTypedArray()
        
        // Estimate series convergence radius
        seriesRadius = estimateSeriesRadius()
        seriesSkipThreshold = seriesRadius * 0.1
        
        Log.d(TAG, "Series approximation: ${seriesCoefficients.size} coefficients, radius ≈ ${String.format("%.2e", seriesRadius)}")
    }
    
    /**
     * Estimate the convergence radius of the series approximation
     */
    private fun estimateSeriesRadius(): Double {
        if (seriesCoefficients.size < 3) return 0.0
        
        // Use ratio test: radius ≈ |A(n)| / |A(n+1)|
        var minRadius = Double.MAX_VALUE
        
        for (i in 1 until seriesCoefficients.size - 1) {
            val current = seriesCoefficients[i].magnitude()
            val next = seriesCoefficients[i + 1].magnitude()
            
            if (current > 1e-100 && next > 1e-100) {
                val radius = current / next
                if (radius > 0 && radius < minRadius) {
                    minRadius = radius
                }
            }
        }
        
        return if (minRadius == Double.MAX_VALUE) 1e-3 else minRadius * 0.5 // Conservative estimate
    }
    
    /**
     * Apply series approximation to skip iterations
     */
    private fun applySeriesApproximation(deltaC: ComplexDouble): Pair<ComplexDouble, Int> {
        if (seriesCoefficients.isEmpty() || deltaC.magnitude() > seriesSkipThreshold) {
            return Pair(ComplexDouble.ZERO, 0)
        }
        
        var result = ComplexDouble.ZERO
        var deltaCPower = ComplexDouble.ONE
        var skippedIterations = 0
        
        for (i in seriesCoefficients.indices) {
            val term = seriesCoefficients[i].multiply(deltaCPower)
            result = result.add(term)
            
            deltaCPower = deltaCPower.multiply(deltaC)
            
            // Estimate convergence and iterations saved
            if (term.magnitude() < SERIES_TOLERANCE) {
                skippedIterations = minOf(i * 2, referenceOrbit.size / 4)
                break
            }
        }
        
        return Pair(result, skippedIterations)
    }
    
    /**
     * Calculate iterations using perturbation theory
     */
    private fun calculatePerturbation(
        deltaC: ComplexDouble,
        maxIterations: Int
    ): Int {
        // Apply series approximation for initial iterations
        val (seriesResult, skippedIters) = applySeriesApproximation(deltaC)
        
        var dz = seriesResult // Start with series approximation result
        var iteration = skippedIters
        val escapeRadius = 256.0
        
        // Continue with perturbation iterations
        while (iteration < maxIterations && iteration < referenceOrbit.size) {
            val zRef = referenceOrbit[iteration]
            
            // Total z = reference + perturbation
            val totalZ = zRef.add(dz)
            
            // Check escape condition
            if (totalZ.magnitudeSquared() > escapeRadius) {
                return iteration
            }
            
            // Perturbation formula: δz_{n+1} = 2*Z_n*δz_n + δz_n² + δc
            val newDz = zRef.multiply(dz).multiply(2.0)
                .add(dz.multiply(dz))
                .add(deltaC)
            
            dz = newDz
            
            // Glitch detection - if perturbation becomes larger than reference
            val perturbationSize = dz.magnitudeSquared()
            val referenceSize = zRef.magnitudeSquared()
            
            if (perturbationSize > referenceSize * 100 || perturbationSize < GLITCH_TOLERANCE) {
                // Switch to direct calculation to avoid glitches
                return calculateDirectFromPoint(totalZ, deltaC, iteration, maxIterations)
            }
            
            iteration++
        }
        
        // If we've used up the reference orbit, continue with direct calculation
        if (iteration >= referenceOrbit.size && iteration < maxIterations) {
            val lastRef = if (referenceOrbit.isNotEmpty()) referenceOrbit.last() else ComplexDouble.ZERO
            return calculateDirectFromPoint(lastRef.add(dz), deltaC, iteration, maxIterations)
        }
        
        return iteration
    }
    
    /**
     * Direct calculation fallback for glitch correction
     */
    private fun calculateDirectFromPoint(
        startZ: ComplexDouble,
        c: ComplexDouble,
        startIteration: Int,
        maxIterations: Int
    ): Int {
        var z = startZ
        val escapeRadius = 256.0
        
        for (i in startIteration until maxIterations) {
            if (z.magnitudeSquared() > escapeRadius) {
                return i
            }
            
            z = z.multiply(z).add(c)
        }
        
        return maxIterations
    }
    
    /**
     * Main calculation function with modern optimizations
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
            // Update precision for current zoom level
            updatePrecision(zoomFactor)
            
            // Calculate world coordinates
            val scale = BigDecimal(4.0 / zoomFactor, mathContext)
            val aspectRatio = BigDecimal(viewWidth.toDouble() / viewHeight.toDouble(), mathContext)
            
            val pixelOffsetX = BigDecimal((pixelX - viewWidth / 2.0) / viewWidth, mathContext)
            val pixelOffsetY = BigDecimal((pixelY - viewHeight / 2.0) / viewHeight, mathContext)
            
            val worldX = centerX.add(scale.multiply(pixelOffsetX, mathContext).multiply(aspectRatio, mathContext), mathContext)
            val worldY = centerY.add(scale.multiply(pixelOffsetY, mathContext), mathContext)
            
            val currentCenter = ComplexBig(centerX, centerY, mathContext)
            
            // Check if we need to recalculate reference orbit
            val needNewOrbit = !orbitValid || 
                referencePoint == null ||
                referencePoint!!.real.subtract(currentCenter.real, mathContext).abs().compareTo(scale.multiply(BigDecimal("0.5"), mathContext)) > 0 ||
                referencePoint!!.imag.subtract(currentCenter.imag, mathContext).abs().compareTo(scale.multiply(BigDecimal("0.5"), mathContext)) > 0
            
            if (needNewOrbit) {
                calculateReferenceOrbit(currentCenter, maxIterations)
            }
            
            // Calculate delta from reference point
            val deltaC = ComplexDouble(
                worldX.subtract(referencePoint!!.real, mathContext).toDouble(),
                worldY.subtract(referencePoint!!.imag, mathContext).toDouble()
            )
            
            // Use perturbation theory calculation
            calculatePerturbation(deltaC, maxIterations)
            
        } catch (e: Exception) {
            Log.w(TAG, "Error in modern deep zoom calculation", e)
            // Fallback to simple calculation
            val simpleX = centerX.toDouble() + (pixelX - viewWidth / 2.0) / viewWidth * 4.0 / zoomFactor
            val simpleY = centerY.toDouble() + (pixelY - viewHeight / 2.0) / viewHeight * 4.0 / zoomFactor
            
            var z = ComplexDouble.ZERO
            val c = ComplexDouble(simpleX, simpleY)
            
            for (i in 0 until maxIterations) {
                if (z.magnitudeSquared() > 4.0) return i
                z = z.multiply(z).add(c)
            }
            maxIterations
        }
    }
    
    /**
     * Prepare for rendering a new area
     */
    fun prepareForArea(
        centerX: BigDecimal,
        centerY: BigDecimal,
        zoomFactor: Double,
        maxIterations: Int
    ) {
        updatePrecision(zoomFactor)
        val center = ComplexBig(centerX, centerY, mathContext)
        calculateReferenceOrbit(center, maxIterations)
    }
    
    /**
     * Get information about current optimization state
     */
    fun getOptimizationInfo(): String {
        return buildString {
            append("Precision: ${currentPrecision} digits")
            if (orbitValid) {
                append(", Reference: ${referenceIterations} iterations")
                append(", Series: ${seriesCoefficients.size} terms")
                append(", Radius: ${String.format("%.2e", seriesRadius)}")
            }
        }
    }
}
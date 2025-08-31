package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Advanced Mandelbrot calculator with support for unlimited zooming using:
 * - Arbitrary precision arithmetic (BigDecimal)
 * - Perturbation theory for deep zoom performance
 * - Series approximation for bulk region optimization
 * - Reference point calculation system
 */
class DeepMandelbrotCalculator {
    
    // Precision management - dynamically adjusted based on zoom level
    private var currentPrecision = 32
    private var mathContext = MathContext(currentPrecision, RoundingMode.HALF_UP)
    
    // Constants for arbitrary precision
    private val TWO = BigDecimal("2")
    private val FOUR = BigDecimal("4")
    private val ESCAPE_RADIUS_SQUARED = BigDecimal("4")
    
    // Reference point for perturbation theory
    private var referenceX: BigDecimal = BigDecimal.ZERO
    private var referenceY: BigDecimal = BigDecimal.ZERO
    private var referenceOrbit: Array<ComplexBig> = arrayOf()
    private var referenceIterations = 0
    
    // Series approximation coefficients
    private var seriesCoefficients: Array<ComplexBig> = arrayOf()
    private var seriesRadius = 0.0
    
    data class ComplexBig(
        val real: BigDecimal,
        val imag: BigDecimal,
        val precision: MathContext = MathContext.DECIMAL128
    ) {
        fun multiply(other: ComplexBig): ComplexBig {
            val newReal = real.multiply(other.real, precision).subtract(imag.multiply(other.imag, precision), precision)
            val newImag = real.multiply(other.imag, precision).add(imag.multiply(other.real, precision), precision)
            return ComplexBig(newReal, newImag, precision)
        }
        
        fun add(other: ComplexBig): ComplexBig {
            return ComplexBig(
                real.add(other.real, precision),
                imag.add(other.imag, precision),
                precision
            )
        }
        
        fun subtract(other: ComplexBig): ComplexBig {
            return ComplexBig(
                real.subtract(other.real, precision),
                imag.subtract(other.imag, precision),
                precision
            )
        }
        
        fun magnitudeSquared(): BigDecimal {
            return real.multiply(real, precision).add(imag.multiply(imag, precision), precision)
        }
        
        fun magnitude(): Double {
            return sqrt(magnitudeSquared().toDouble())
        }
        
        companion object {
            fun fromDouble(real: Double, imag: Double, precision: MathContext): ComplexBig {
                return ComplexBig(
                    BigDecimal(real.toString(), precision),
                    BigDecimal(imag.toString(), precision),
                    precision
                )
            }
        }
    }
    
    /**
     * Calculate required precision based on zoom level
     */
    private fun calculateRequiredPrecision(zoomLevel: Double): Int {
        // Rule of thumb: need about 1.5 * log10(zoom) + base precision
        val basePrecision = 32
        val additionalPrecision = (1.5 * log10(zoomLevel)).toInt()
        return maxOf(basePrecision, basePrecision + additionalPrecision)
    }
    
    /**
     * Update precision and math context based on zoom level
     */
    private fun updatePrecision(centerX: BigDecimal, centerY: BigDecimal, zoomFactor: Double) {
        // Estimate zoom level from coordinate precision
        val coordinatePrecision = maxOf(centerX.scale(), centerY.scale())
        val estimatedZoom = 10.0.pow(coordinatePrecision.toDouble())
        
        val newPrecision = calculateRequiredPrecision(maxOf(zoomFactor, estimatedZoom))
        if (newPrecision != currentPrecision) {
            currentPrecision = newPrecision
            mathContext = MathContext(currentPrecision, RoundingMode.HALF_UP)
        }
    }
    
    /**
     * Calculate reference orbit for perturbation theory
     */
    private fun calculateReferenceOrbit(
        referencePoint: ComplexBig,
        maxIterations: Int,
        power: Int = 2
    ): Pair<Array<ComplexBig>, Int> {
        val orbit = mutableListOf<ComplexBig>()
        var z = ComplexBig(BigDecimal.ZERO, BigDecimal.ZERO, mathContext)
        val c = referencePoint
        
        for (i in 0 until maxIterations) {
            orbit.add(z)
            
            // Check escape condition
            if (z.magnitudeSquared().compareTo(ESCAPE_RADIUS_SQUARED) > 0) {
                return Pair(orbit.toTypedArray(), i)
            }
            
            // z = z^power + c (currently supporting power = 2)
            when (power) {
                2 -> z = z.multiply(z).add(c)
                3 -> z = z.multiply(z).multiply(z).add(c)
                4 -> {
                    val z2 = z.multiply(z)
                    z = z2.multiply(z2).add(c)
                }
                else -> z = z.multiply(z).add(c) // Default to z^2
            }
        }
        
        return Pair(orbit.toTypedArray(), maxIterations)
    }
    
    /**
     * Calculate series approximation coefficients for bulk optimization
     */
    private fun calculateSeriesApproximation(
        referenceOrbit: Array<ComplexBig>,
        maxTerms: Int = 10
    ): Array<ComplexBig> {
        if (referenceOrbit.size < 2) return arrayOf()
        
        val coefficients = mutableListOf<ComplexBig>()
        
        // A1 = 1 (first coefficient is always 1)
        coefficients.add(ComplexBig(BigDecimal.ONE, BigDecimal.ZERO, mathContext))
        
        // Calculate subsequent coefficients using recurrence relation
        for (n in 2..minOf(maxTerms, referenceOrbit.size - 1)) {
            if (n - 1 < referenceOrbit.size) {
                val zn = referenceOrbit[n - 1]
                // Simplified: An = 2 * Zn-1 * A1 (for basic series approximation)
                val an = ComplexBig(TWO, BigDecimal.ZERO, mathContext)
                    .multiply(zn)
                    .multiply(coefficients[0])
                coefficients.add(an)
            }
        }
        
        return coefficients.toTypedArray()
    }
    
    /**
     * Apply series approximation to skip iterations
     */
    private fun applySeriesApproximation(
        deltaC: ComplexBig,
        coefficients: Array<ComplexBig>,
        maxRadius: Double = 1e-3
    ): Pair<ComplexBig, Int> {
        if (coefficients.isEmpty()) return Pair(ComplexBig.fromDouble(0.0, 0.0, mathContext), 0)
        
        val deltaCMagnitude = deltaC.magnitude()
        if (deltaCMagnitude > maxRadius) return Pair(ComplexBig.fromDouble(0.0, 0.0, mathContext), 0)
        
        var result = ComplexBig.fromDouble(0.0, 0.0, mathContext)
        var deltaCPower = ComplexBig(BigDecimal.ONE, BigDecimal.ZERO, mathContext)
        var skippedIterations = 0
        
        for (i in coefficients.indices) {
            val term = coefficients[i].multiply(deltaCPower)
            result = result.add(term)
            
            deltaCPower = deltaCPower.multiply(deltaC)
            
            // Estimate how many iterations this represents
            if (term.magnitude() < deltaCMagnitude.pow(i + 1) * 1e-10) {
                skippedIterations = i * 2 // Rough estimate
                break
            }
        }
        
        return Pair(result, minOf(skippedIterations, coefficients.size * 2))
    }
    
    /**
     * Advanced iteration calculation using perturbation theory
     */
    private fun calculateIterationsPerturbation(
        pixelX: BigDecimal,
        pixelY: BigDecimal,
        maxIterations: Int,
        power: Int = 2,
        isJulia: Boolean = false,
        juliaC: ComplexBig? = null
    ): Int {
        val c = if (isJulia && juliaC != null) juliaC else ComplexBig(pixelX, pixelY, mathContext)
        
        // Calculate delta from reference point
        val deltaC = c.subtract(ComplexBig(referenceX, referenceY, mathContext))
        
        // Try series approximation first
        val (seriesResult, skippedIters) = if (seriesCoefficients.isNotEmpty()) {
            applySeriesApproximation(deltaC, seriesCoefficients)
        } else {
            Pair(ComplexBig.fromDouble(0.0, 0.0, mathContext), 0)
        }
        
        // Start iteration from after series approximation
        var z = seriesResult
        var dz = ComplexBig(BigDecimal.ONE, BigDecimal.ZERO, mathContext) // derivative
        
        val startIteration = minOf(skippedIters, referenceOrbit.size - 1, maxIterations)
        
        for (i in startIteration until minOf(maxIterations, referenceOrbit.size)) {
            // Perturbation formula: z_n+1 = (Z_n + z_n)^power + C
            // where Z_n is reference orbit, z_n is perturbation
            val referenceZ = referenceOrbit[i]
            val totalZ = referenceZ.add(z)
            
            // Check escape condition
            if (totalZ.magnitudeSquared().compareTo(ESCAPE_RADIUS_SQUARED) > 0) {
                return i
            }
            
            // Update perturbation using: z_n+1 = 2*Z_n*z_n + z_n^2 + deltaC
            when (power) {
                2 -> {
                    val newZ = TWO.let { two ->
                        ComplexBig(two, BigDecimal.ZERO, mathContext)
                            .multiply(referenceZ)
                            .multiply(z)
                            .add(z.multiply(z))
                            .add(deltaC)
                    }
                    z = newZ
                }
                else -> {
                    // Fallback to direct calculation for higher powers
                    z = totalZ.multiply(totalZ).subtract(referenceZ.multiply(referenceZ))
                }
            }
            
            // If perturbation becomes too large, switch to direct calculation
            if (z.magnitude() > referenceZ.magnitude() * 10) {
                return calculateIterationsDirect(totalZ, c, i, maxIterations, power)
            }
        }
        
        // If we've used up the reference orbit, continue with direct calculation
        if (referenceOrbit.isNotEmpty() && referenceOrbit.size < maxIterations) {
            val lastRef = referenceOrbit.lastOrNull() ?: ComplexBig.fromDouble(0.0, 0.0, mathContext)
            return calculateIterationsDirect(lastRef.add(z), c, referenceOrbit.size, maxIterations, power)
        }
        
        return maxIterations
    }
    
    /**
     * Direct high-precision iteration calculation
     */
    private fun calculateIterationsDirect(
        startZ: ComplexBig,
        c: ComplexBig,
        startIteration: Int,
        maxIterations: Int,
        power: Int = 2
    ): Int {
        var z = startZ
        
        for (i in startIteration until maxIterations) {
            if (z.magnitudeSquared().compareTo(ESCAPE_RADIUS_SQUARED) > 0) {
                return i
            }
            
            when (power) {
                2 -> z = z.multiply(z).add(c)
                3 -> z = z.multiply(z).multiply(z).add(c)
                4 -> {
                    val z2 = z.multiply(z)
                    z = z2.multiply(z2).add(c)
                }
                else -> z = z.multiply(z).add(c)
            }
        }
        
        return maxIterations
    }
    
    /**
     * Main calculation function with modern deep zoom support
     */
    fun calculateIterations(
        pixelX: Double,
        pixelY: Double,
        centerX: BigDecimal,
        centerY: BigDecimal,
        zoomFactor: Double,
        viewWidth: Int,
        viewHeight: Int,
        maxIterations: Int,
        power: Int = 2,
        isJulia: Boolean = false,
        juliaX: Double = 0.0,
        juliaY: Double = 0.0
    ): Int {
        // Update precision based on zoom level
        updatePrecision(centerX, centerY, zoomFactor)
        
        // Calculate world coordinates with high precision
        val scale = BigDecimal(4.0 / zoomFactor, mathContext)
        val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
        
        val worldX = centerX.add(
            scale.multiply(
                BigDecimal((pixelX - viewWidth / 2.0) / viewWidth * aspectRatio, mathContext),
                mathContext
            ),
            mathContext
        )
        
        val worldY = centerY.add(
            scale.multiply(
                BigDecimal((pixelY - viewHeight / 2.0) / viewHeight, mathContext),
                mathContext
            ),
            mathContext
        )
        
        // Use appropriate calculation method based on zoom level
        return if (zoomFactor > 1e12) {
            // Use perturbation theory for deep zoom
            val juliaC = if (isJulia) {
                ComplexBig.fromDouble(juliaX, juliaY, mathContext)
            } else null
            
            calculateIterationsPerturbation(worldX, worldY, maxIterations, power, isJulia, juliaC)
        } else {
            // Use direct calculation for moderate zoom levels
            val c = if (isJulia) {
                ComplexBig.fromDouble(juliaX, juliaY, mathContext)
            } else {
                ComplexBig(worldX, worldY, mathContext)
            }
            
            val startZ = if (isJulia) {
                ComplexBig(worldX, worldY, mathContext)
            } else {
                ComplexBig.fromDouble(0.0, 0.0, mathContext)
            }
            
            calculateIterationsDirect(startZ, c, 0, maxIterations, power)
        }
    }
    
    /**
     * Prepare for deep zoom by calculating reference orbit and series approximation
     */
    fun prepareDeepZoom(
        centerX: BigDecimal,
        centerY: BigDecimal,
        zoomFactor: Double,
        maxIterations: Int,
        power: Int = 2
    ) {
        if (zoomFactor <= 1e12) return // Only needed for deep zoom
        
        updatePrecision(centerX, centerY, zoomFactor)
        
        // Set reference point to center
        referenceX = centerX
        referenceY = centerY
        
        // Calculate reference orbit
        val referencePoint = ComplexBig(referenceX, referenceY, mathContext)
        val (orbit, iterations) = calculateReferenceOrbit(referencePoint, maxIterations, power)
        
        referenceOrbit = orbit
        referenceIterations = iterations
        
        // Calculate series approximation coefficients
        seriesCoefficients = calculateSeriesApproximation(referenceOrbit)
        seriesRadius = 1e-3 / zoomFactor.pow(0.25) // Adaptive radius based on zoom
    }
}
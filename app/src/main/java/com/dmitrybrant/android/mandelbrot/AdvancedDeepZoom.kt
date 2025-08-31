package com.dmitrybrant.android.mandelbrot

import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Advanced deep zoom Mandelbrot calculator based on the sophisticated JavaScript implementation
 * Features:
 * - Scaled floating-point representation [mantissa, exponent]
 * - Advanced series approximation with polynomial coefficients
 * - Reference orbit with adaptive scaling
 * - Perturbation theory with glitch detection
 */
class AdvancedDeepZoom {
    
    companion object {
        private const val TAG = "AdvancedDeepZoom"
        private const val ORBIT_SIZE = 4096 // Maximum orbit length
        private const val ESCAPE_RADIUS_SQUARED = 400.0 // 20^2 for better precision
    }
    
    // High precision arithmetic
    private var precision = 200
    private var mathContext = MathContext(precision, RoundingMode.HALF_UP)
    
    // Scaled floating point number [mantissa, exponent]
    data class ScaledFloat(val mantissa: Double, val exponent: Int) {
        fun add(other: ScaledFloat): ScaledFloat {
            val maxExp = maxOf(exponent, other.exponent)
            val scaledThis = mantissa * 2.0.pow(exponent - maxExp)
            val scaledOther = other.mantissa * 2.0.pow(other.exponent - maxExp)
            return ScaledFloat(scaledThis + scaledOther, maxExp)
        }
        
        fun subtract(other: ScaledFloat): ScaledFloat {
            val maxExp = maxOf(exponent, other.exponent)
            val scaledThis = mantissa * 2.0.pow(exponent - maxExp)
            val scaledOther = other.mantissa * 2.0.pow(other.exponent - maxExp)
            return ScaledFloat(scaledThis - scaledOther, maxExp)
        }
        
        fun multiply(other: ScaledFloat): ScaledFloat {
            var m = mantissa * other.mantissa
            var e = exponent + other.exponent
            
            if (m != 0.0) {
                val logM = round(log2(abs(m))).toInt()
                m /= 2.0.pow(logM)
                e += logM
            }
            return ScaledFloat(m, e)
        }
        
        fun multiply(scalar: Double): ScaledFloat {
            return multiply(ScaledFloat(scalar, 0))
        }
        
        fun toDouble(): Double {
            return mantissa * 2.0.pow(exponent)
        }
        
        fun abs(): ScaledFloat {
            return ScaledFloat(abs(mantissa), exponent)
        }
        
        fun max(other: ScaledFloat): ScaledFloat {
            return if (greaterThan(other)) this else other
        }
        
        fun greaterThan(other: ScaledFloat): Boolean {
            val maxExp = maxOf(exponent, other.exponent)
            val scaledThis = mantissa * 2.0.pow(exponent - maxExp)
            val scaledOther = other.mantissa * 2.0.pow(other.exponent - maxExp)
            return scaledThis > scaledOther
        }
        
        companion object {
            val ZERO = ScaledFloat(0.0, 0)
            val ONE = ScaledFloat(1.0, 0)
            val TWO = ScaledFloat(2.0, 0)
        }
    }
    
    // Complex number using scaled floats
    data class ScaledComplex(val real: ScaledFloat, val imag: ScaledFloat) {
        fun add(other: ScaledComplex): ScaledComplex {
            return ScaledComplex(real.add(other.real), imag.add(other.imag))
        }
        
        fun subtract(other: ScaledComplex): ScaledComplex {
            return ScaledComplex(real.subtract(other.real), imag.subtract(other.imag))
        }
        
        fun multiply(other: ScaledComplex): ScaledComplex {
            // (a + bi)(c + di) = (ac - bd) + (ad + bc)i
            val newReal = real.multiply(other.real).subtract(imag.multiply(other.imag))
            val newImag = real.multiply(other.imag).add(imag.multiply(other.real))
            return ScaledComplex(newReal, newImag)
        }
        
        fun multiply(scalar: ScaledFloat): ScaledComplex {
            return ScaledComplex(real.multiply(scalar), imag.multiply(scalar))
        }
        
        fun magnitudeSquared(): ScaledFloat {
            return real.multiply(real).add(imag.multiply(imag))
        }
        
        companion object {
            val ZERO = ScaledComplex(ScaledFloat.ZERO, ScaledFloat.ZERO)
            val ONE = ScaledComplex(ScaledFloat.ONE, ScaledFloat.ZERO)
        }
    }
    
    // Reference orbit data
    data class OrbitPoint(
        val x: Float,
        val y: Float, 
        val scale: Float
    )
    
    private var referenceCenter: ComplexBig? = null
    private var referenceRadius: BigDecimal? = null
    private var referenceOrbit: Array<OrbitPoint> = arrayOf()
    private var orbitLength = 0
    
    // Series approximation coefficients (B, C, D terms)
    private var polyB = ScaledComplex.ZERO
    private var polyC = ScaledComplex.ZERO
    private var polyD = ScaledComplex.ZERO
    private var polyLimit = 0
    
    // High precision complex
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
    }
    
    /**
     * Calculate reference orbit with advanced scaling
     */
    private fun makeReferenceOrbit(center: ComplexBig, radius: BigDecimal, maxIterations: Int) {
        Log.d(TAG, "Calculating advanced reference orbit")
        
        val cx = center.real
        val cy = center.imag
        var x = BigDecimal.ZERO
        var y = BigDecimal.ZERO
        
        val orbit = mutableListOf<OrbitPoint>()
        
        // Polynomial approximation variables
        var Bx = ScaledFloat.ZERO
        var By = ScaledFloat.ZERO
        var Cx = ScaledFloat.ZERO
        var Cy = ScaledFloat.ZERO
        var Dx = ScaledFloat.ZERO
        var Dy = ScaledFloat.ZERO
        
        var polyLimitFound = false
        
        for (i in 0 until maxIterations) {
            // Get exponents for scaling
            val xExp = if (x.compareTo(BigDecimal.ZERO) == 0) 0 else getExponent(x)
            val yExp = if (y.compareTo(BigDecimal.ZERO) == 0) 0 else getExponent(y)
            val scaleExp = maxOf(xExp, yExp)
            
            // Store scaled orbit point
            val scaledX = if (xExp < -10000) 0f else {
                (x.divide(BigDecimal(2.0).pow(scaleExp - xExp), mathContext)).toFloat()
            }
            val scaledY = if (yExp < -10000) 0f else {
                (y.divide(BigDecimal(2.0).pow(scaleExp - yExp), mathContext)).toFloat()
            }
            
            orbit.add(OrbitPoint(scaledX, scaledY, scaleExp.toFloat()))
            
            // Calculate series approximation coefficients
            val fx = ScaledFloat(scaledX.toDouble(), scaleExp)
            val fy = ScaledFloat(scaledY.toDouble(), scaleExp)
            
            val prevBx = Bx
            val prevBy = By
            val prevCx = Cx
            val prevCy = Cy
            val prevDx = Dx
            val prevDy = Dy
            
            // Update polynomial coefficients based on JS implementation
            Bx = ScaledFloat.TWO.multiply(fx.multiply(Bx).subtract(fy.multiply(By))).add(ScaledFloat.ONE)
            By = ScaledFloat.TWO.multiply(fx.multiply(By).add(fy.multiply(Bx)))
            
            Cx = ScaledFloat.TWO.multiply(fx.multiply(Cx).subtract(fy.multiply(Cy))).add(Bx.multiply(Bx).subtract(By.multiply(By)))
            Cy = ScaledFloat.TWO.multiply(fx.multiply(Cy).add(fy.multiply(Cx))).add(ScaledFloat.TWO.multiply(Bx.multiply(By)))
            
            Dx = ScaledFloat.TWO.multiply(fx.multiply(Dx).subtract(fy.multiply(Dy)).add(Cx.multiply(Bx).subtract(Cy.multiply(By))))
            Dy = ScaledFloat.TWO.multiply(fx.multiply(Dy).add(fy.multiply(Dx)).add(Cx.multiply(By)).add(Cy.multiply(Bx)))
            
            // Check polynomial validity condition (based on JS logic)
            val radiusExp = getExponent(radius)
            val testValue = ScaledFloat(1000.0, radiusExp).multiply(Dx.abs().max(Dy.abs()))
            if (i == 0 || Cx.abs().max(Cy.abs()).greaterThan(testValue)) {
                if (!polyLimitFound) {
                    polyB = ScaledComplex(prevBx, prevBy)
                    polyC = ScaledComplex(prevCx, prevCy)
                    polyD = ScaledComplex(prevDx, prevDy)
                    polyLimit = i
                }
            } else {
                polyLimitFound = true
            }
            
            // Mandelbrot iteration: z = z² + c
            val xx = x.multiply(x, mathContext)
            val xy = x.multiply(y, mathContext)
            val yy = y.multiply(y, mathContext)
            
            x = xx.subtract(yy, mathContext).add(cx, mathContext)
            y = xy.add(xy, mathContext).add(cy, mathContext)
            
            // Check escape condition
            if (xx.add(yy, mathContext).compareTo(BigDecimal(ESCAPE_RADIUS_SQUARED)) > 0) {
                orbitLength = i + 1
                break
            }
        }
        
        if (orbitLength == 0) orbitLength = maxIterations
        
        referenceCenter = center
        referenceRadius = radius
        referenceOrbit = orbit.toTypedArray()
        
        Log.d(TAG, "Reference orbit: $orbitLength iterations, poly limit: $polyLimit")
    }
    
    /**
     * Get the binary exponent of a BigDecimal
     */
    private fun getExponent(value: BigDecimal): Int {
        if (value.compareTo(BigDecimal.ZERO) == 0) return 0
        
        val log2 = ln(value.abs().toDouble()) / ln(2.0)
        return round(log2).toInt()
    }
    
    /**
     * Advanced perturbation calculation based on the JS shader logic
     */
    private fun calculatePerturbation(
        deltaX: Double,
        deltaY: Double,
        radiusExp: Int,
        maxIterations: Int
    ): Int {
        if (referenceOrbit.isEmpty()) return maxIterations
        
        var q = radiusExp - 1
        val cq = q
        q += polyLimit
        
        var dcx = deltaX
        var dcy = deltaY
        
        // Series approximation (polynomial expansion)
        val sqrx = dcx * dcx - dcy * dcy
        val sqry = 2.0 * dcx * dcy
        
        var dx = polyB.real.toDouble() * dcx - polyB.imag.toDouble() * dcy + 
                polyC.real.toDouble() * sqrx - polyC.imag.toDouble() * sqry
        var dy = polyB.real.toDouble() * dcy + polyB.imag.toDouble() * dcx + 
                polyC.real.toDouble() * sqry + polyC.imag.toDouble() * sqrx
        
        var k = polyLimit
        var j = k
        
        // Main perturbation loop (based on shader logic)
        for (i in k until minOf(maxIterations, orbitLength)) {
            j++
            k++
            
            val orbitPoint = referenceOrbit[k - 1]
            val os = orbitPoint.scale.toInt()
            
            dcx = deltaX * 2.0.pow(-q + cq - os)
            dcy = deltaY * 2.0.pow(-q + cq - os)
            val unS = 2.0.pow(q - orbitPoint.scale)
            
            if (!unS.isFinite()) continue
            
            // Perturbation formula
            val x = orbitPoint.x.toDouble()
            val y = orbitPoint.y.toDouble()
            
            val tx = 2.0 * x * dx - 2.0 * y * dy + unS * dx * dx - unS * dy * dy + dcx
            dy = 2.0 * x * dy + 2.0 * y * dx + unS * 2.0 * dx * dy + dcy
            dx = tx
            
            q += os
            
            // Get next orbit point
            val nextPoint = referenceOrbit[k]
            val fx = nextPoint.x * 2.0.pow(nextPoint.scale) + 2.0.pow(q) * dx
            val fy = nextPoint.y * 2.0.pow(nextPoint.scale) + 2.0.pow(q) * dy
            
            // Check escape condition
            if (fx * fx + fy * fy > ESCAPE_RADIUS_SQUARED) {
                return i
            }
            
            // Adaptive scaling (glitch prevention)
            if (dx * dx + dy * dy > 1000000.0) {
                dx /= 2.0
                dy /= 2.0
                q++
                dcx = deltaX * 2.0.pow(-q + cq)
                dcy = deltaY * 2.0.pow(-q + cq)
            }
            
            // Glitch detection and recovery
            val S = 2.0.pow(q)
            if (fx * fx + fy * fy < S * S * dx * dx + S * S * dy * dy || 
                (nextPoint.x == -1f && nextPoint.y == -1f)) {
                dx = fx
                dy = fy
                q = 0
                dcx = deltaX * 2.0.pow(-q + cq)
                dcy = deltaY * 2.0.pow(-q + cq)
                k = 0
            }
        }
        
        return j
    }
    
    /**
     * Main calculation function
     */
    fun calculateIterations(
        pixelX: Double,
        pixelY: Double,
        centerX: BigDecimal,
        centerY: BigDecimal,
        radius: BigDecimal,
        viewWidth: Int,
        viewHeight: Int,
        maxIterations: Int
    ): Int {
        return try {
            val center = ComplexBig(centerX, centerY, mathContext)
            
            // Check if we need new reference orbit
            if (referenceCenter == null || 
                referenceCenter!!.real.subtract(centerX, mathContext).abs().compareTo(radius.multiply(BigDecimal("0.5"))) > 0 ||
                referenceCenter!!.imag.subtract(centerY, mathContext).abs().compareTo(radius.multiply(BigDecimal("0.5"))) > 0) {
                
                makeReferenceOrbit(center, radius, maxIterations)
            }
            
            // Calculate world coordinates
            val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
            val pixelOffsetX = (pixelX - viewWidth / 2.0) / viewWidth * aspectRatio
            val pixelOffsetY = (pixelY - viewHeight / 2.0) / viewHeight
            
            val worldX = centerX.add(radius.multiply(BigDecimal(pixelOffsetX), mathContext), mathContext)
            val worldY = centerY.add(radius.multiply(BigDecimal(pixelOffsetY), mathContext), mathContext)
            
            // Calculate delta from reference center
            val deltaX = worldX.subtract(referenceCenter!!.real, mathContext).toDouble()
            val deltaY = worldY.subtract(referenceCenter!!.imag, mathContext).toDouble()
            
            // Use advanced perturbation calculation
            val radiusExp = getExponent(radius)
            calculatePerturbation(deltaX, deltaY, radiusExp, maxIterations)
            
        } catch (e: Exception) {
            Log.w(TAG, "Error in advanced calculation", e)
            maxIterations / 2
        }
    }
    
    /**
     * Prepare for new area
     */
    fun prepareForArea(
        centerX: BigDecimal,
        centerY: BigDecimal,
        radius: BigDecimal,
        maxIterations: Int
    ) {
        val center = ComplexBig(centerX, centerY, mathContext)
        makeReferenceOrbit(center, radius, maxIterations)
    }
    
    /**
     * Get optimization information
     */
    fun getOptimizationInfo(): String {
        return "Advanced: ${orbitLength} orbit, poly@$polyLimit, precision $precision digits"
    }
}
package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap
import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Direct port of the sophisticated JavaScript Mandelbrot implementation from main.js
 * Uses the same algorithms: reference orbit, scaled floating-point, polynomial series
 */
class JSBasedCalculator {
    
    companion object {
        private const val TAG = "JSBasedCalculator"
        private const val ORBIT_SIZE = 1024 * 1024
    }
    
    // High precision arithmetic with 1200 bits like the JS version
    private val mathContext = MathContext(360, RoundingMode.HALF_UP) // ~1200 bits
    
    // Mandelbrot state (equivalent to mandelbrot_state in JS)
    private var centerX: BigDecimal = BigDecimal("-0.5", mathContext)
    private var centerY: BigDecimal = BigDecimal("0.0", mathContext) 
    private var radius: BigDecimal = BigDecimal("2.0", mathContext)
    private var iterations = 1000
    
    // Reference orbit data (equivalent to orbit array in JS)
    private var orbit = FloatArray(ORBIT_SIZE)
    private var orbitLength = 0
    
    // Polynomial coefficients (B, C, D from JS)
    private var polyCoeffs = FloatArray(6) // Bx, By, Cx, Cy, Dx, Dy
    private var polyLimit = 0
    
    // Scaled floating-point operations (from JS helper functions)
    private fun sub(a: Pair<Double, Int>, b: Pair<Double, Int>): Pair<Double, Int> {
        val (am, ae) = a
        val (bm, be) = b
        val retE = maxOf(ae, be)
        val adjustedAm = if (retE > ae) am * 2.0.pow(ae - retE) else am
        val adjustedBm = if (retE > be) bm * 2.0.pow(be - retE) else bm
        return Pair(adjustedAm - adjustedBm, retE)
    }
    
    private fun add(a: Pair<Double, Int>, b: Pair<Double, Int>): Pair<Double, Int> {
        val (am, ae) = a
        val (bm, be) = b
        val retE = maxOf(ae, be)
        val adjustedAm = if (retE > ae) am * 2.0.pow(ae - retE) else am
        val adjustedBm = if (retE > be) bm * 2.0.pow(be - retE) else bm
        return Pair(adjustedAm + adjustedBm, retE)
    }
    
    private fun mul(a: Pair<Double, Int>, b: Pair<Double, Int>): Pair<Double, Int> {
        val (am, ae) = a
        val (bm, be) = b
        var m = am * bm
        var e = ae + be
        
        if (m != 0.0) {
            val logM = round(log2(abs(m))).toInt()
            m /= 2.0.pow(logM)
            e += logM
        }
        return Pair(m, e)
    }
    
    private fun maxabs(a: Pair<Double, Int>, b: Pair<Double, Int>): Pair<Double, Int> {
        val (am, ae) = a
        val (bm, be) = b
        val retE = maxOf(ae, be)
        val adjustedAm = if (retE > ae) am * 2.0.pow(ae - retE) else am
        val adjustedBm = if (retE > be) bm * 2.0.pow(be - retE) else bm
        return Pair(maxOf(abs(adjustedAm), abs(adjustedBm)), retE)
    }
    
    private fun gt(a: Pair<Double, Int>, b: Pair<Double, Int>): Boolean {
        val (am, ae) = a
        val (bm, be) = b
        val retE = maxOf(ae, be)
        val adjustedAm = if (retE > ae) am * 2.0.pow(ae - retE) else am
        val adjustedBm = if (retE > be) bm * 2.0.pow(be - retE) else bm
        return adjustedAm > adjustedBm
    }
    
    private fun floaty(d: Pair<Double, Int>): Double {
        return 2.0.pow(d.second) * d.first
    }
    
    private fun getExp(value: BigDecimal): Int {
        if (value.compareTo(BigDecimal.ZERO) == 0) return 0
        return (ln(value.abs().toDouble()) / ln(2.0)).roundToInt()
    }
    
    /**
     * Direct port of make_reference_orbit() from main.js
     */
    private fun makeReferenceOrbit(): Triple<FloatArray, FloatArray, Int> {
        Log.d(TAG, "Making reference orbit like JS version")
        
        val cx = centerX
        val cy = centerY
        var x = BigDecimal.ZERO
        var y = BigDecimal.ZERO
        
        // Initialize orbit array (like JS)
        for (i in 0 until ORBIT_SIZE) {
            orbit[i] = -1f
        }
        
        var polylim = 0
        
        // Polynomial variables (like JS)
        var Bx = Pair(0.0, 0)
        var By = Pair(0.0, 0)
        var Cx = Pair(0.0, 0)
        var Cy = Pair(0.0, 0)
        var Dx = Pair(0.0, 0)
        var Dy = Pair(0.0, 0)
        var notFailed = true
        
        var i = 0
        while (i < iterations) {
            // Get exponents (like JS version)
            val xExponent = if (x.compareTo(BigDecimal.ZERO) == 0) -10000 else getExp(x)
            val yExponent = if (y.compareTo(BigDecimal.ZERO) == 0) -10000 else getExp(y)
            
            var scaleExponent = maxOf(xExponent, yExponent)
            if (scaleExponent < -10000) {
                scaleExponent = 0
            }
            
            // Store orbit point with scaling (like JS)
            if (i * 3 + 2 < ORBIT_SIZE) {
                val scaledX = if (x.compareTo(BigDecimal.ZERO) == 0) 0.0 else {
                    x.toDouble() / 2.0.pow(scaleExponent - xExponent)
                }
                val scaledY = if (y.compareTo(BigDecimal.ZERO) == 0) 0.0 else {
                    y.toDouble() / 2.0.pow(scaleExponent - yExponent)
                }
                
                orbit[3 * i] = scaledX.toFloat()
                orbit[3 * i + 1] = scaledY.toFloat()
                orbit[3 * i + 2] = scaleExponent.toFloat()
            }
            
            val fx = Pair(orbit[3 * i].toDouble(), orbit[3 * i + 2].toInt())
            val fy = Pair(orbit[3 * i + 1].toDouble(), orbit[3 * i + 2].toInt())
            
            // Mandelbrot iteration: z = z² + c
            val txx = x.multiply(x, mathContext)
            val txy = x.multiply(y, mathContext)
            val tyy = y.multiply(y, mathContext)
            
            x = txx.subtract(tyy, mathContext).add(cx, mathContext)
            y = txy.add(txy, mathContext).add(cy, mathContext)
            
            // Update polynomial coefficients (exact port from JS)
            val prevBx = Bx; val prevBy = By; val prevCx = Cx; val prevCy = Cy; val prevDx = Dx; val prevDy = Dy
            
            Bx = add(mul(Pair(2.0, 0), sub(mul(fx, Bx), mul(fy, By))), Pair(1.0, 0))
            By = mul(Pair(2.0, 0), add(mul(fx, By), mul(fy, Bx)))
            Cx = sub(add(mul(Pair(2.0, 0), sub(mul(fx, Cx), mul(fy, Cy))), mul(Bx, Bx)), mul(By, By))
            Cy = add(mul(Pair(2.0, 0), add(mul(fx, Cy), mul(fy, Cx))), mul(mul(Pair(2.0, 0), Bx), By))
            Dx = mul(Pair(2.0, 0), add(sub(mul(fx, Dx), mul(fy, Dy)), sub(mul(Cx, Bx), mul(Cy, By))))
            Dy = mul(Pair(2.0, 0), add(add(add(mul(fx, Dy), mul(fy, Dx)), mul(Cx, By)), mul(Cy, Bx)))
            
            // Check polynomial validity (like JS)
            val radiusExp = getExp(radius)
            if (i == 0 || gt(
                maxabs(Cx, Cy),
                mul(Pair(1000.0, radiusExp), maxabs(Dx, Dy))
            )) {
                if (notFailed) {
                    // Store previous valid polynomial
                    polyCoeffs[0] = prevBx.first.toFloat()
                    polyCoeffs[1] = prevBy.first.toFloat() 
                    polyCoeffs[2] = prevCx.first.toFloat()
                    polyCoeffs[3] = prevCy.first.toFloat()
                    polyCoeffs[4] = prevDx.first.toFloat()
                    polyCoeffs[5] = prevDy.first.toFloat()
                    polylim = i
                }
            } else {
                notFailed = false
            }
            
            // Check escape condition
            val fx2 = if (x.compareTo(BigDecimal.ZERO) == 0) Pair(0.0, 0) else Pair(x.toDouble(), getExp(x))
            val fy2 = if (y.compareTo(BigDecimal.ZERO) == 0) Pair(0.0, 0) else Pair(y.toDouble(), getExp(y))
            
            if (gt(add(mul(fx2, fx2), mul(fy2, fy2)), Pair(400.0, 0))) {
                orbitLength = i + 1
                break
            }
            
            i++
        }
        
        if (orbitLength == 0) orbitLength = i
        
        Log.d(TAG, "Reference orbit: $orbitLength iterations, poly limit: $polylim")
        return Triple(orbit, polyCoeffs, polylim)
    }
    
    /**
     * Direct port of the shader perturbation logic from main.js
     */
    private fun calculatePixel(deltaX: Float, deltaY: Float): Int {
        // Get radius exponent
        val radiusExp = 1 + getExp(radius)
        
        // Shader uniform equivalents
        var q = radiusExp - 1
        val cq = q
        q += polyCoeffs.size // poly2[3] equivalent
        
        var dcx = deltaX.toDouble()
        var dcy = deltaY.toDouble()
        
        // Series approximation (from shader)
        val sqrx = dcx * dcx - dcy * dcy
        val sqry = 2.0 * dcx * dcy
        
        var dx = polyCoeffs[0] * dcx - polyCoeffs[1] * dcy + polyCoeffs[2] * sqrx - polyCoeffs[3] * sqry
        var dy = polyCoeffs[0] * dcy + polyCoeffs[1] * dcx + polyCoeffs[2] * sqry + polyCoeffs[3] * sqrx
        
        var k = polyLimit
        var j = k
        
        // Main iteration loop (direct port of shader logic)
        for (i in k until minOf(iterations, orbitLength / 3)) {
            j++
            k++
            
            if (k * 3 + 2 >= orbit.size) break
            
            val os = orbit[3 * (k - 1) + 2].toInt()
            dcx = deltaX * 2.0.pow(-q + cq - os)
            dcy = deltaY * 2.0.pow(-q + cq - os)
            val unS = 2.0.pow(q - orbit[3 * (k - 1) + 2])
            
            if (!unS.isFinite()) continue
            
            val x = orbit[3 * k].toDouble()
            val y = orbit[3 * k + 1].toDouble()
            
            val tx = 2.0 * x * dx - 2.0 * y * dy + unS * dx * dx - unS * dy * dy + dcx
            dy = 2.0 * x * dy + 2.0 * y * dx + unS * 2.0 * dx * dy + dcy
            dx = tx
            
            q += os
            val S = 2.0.pow(q)
            
            val fx = x * 2.0.pow(orbit[3 * k + 2]) + S * dx
            val fy = y * 2.0.pow(orbit[3 * k + 2]) + S * dy
            
            if (fx * fx + fy * fy > 400.0) {
                return i
            }
            
            // Adaptive scaling (from shader)
            if (dx * dx + dy * dy > 1000000.0) {
                dx /= 2.0
                dy /= 2.0
                q++
                dcx = deltaX * 2.0.pow(-q + cq)
                dcy = deltaY * 2.0.pow(-q + cq)
            }
            
            // Glitch detection (from shader)
            if (fx * fx + fy * fy < S * S * dx * dx + S * S * dy * dy || (x == -1.0 && y == -1.0)) {
                dx = fx
                dy = fy
                q = 0
                dcx = deltaX * 2.0.pow(-q + cq)
                dcy = deltaY * 2.0.pow(-q + cq)
                k = 0
                if (orbit.size > 3) {
                    // x = get_orbit_x(0), y = get_orbit_y(0)
                }
            }
        }
        
        return j
    }
    
    /**
     * Set parameters and prepare orbit
     */
    fun setParameters(
        centerX: BigDecimal,
        centerY: BigDecimal, 
        radius: BigDecimal,
        iterations: Int
    ) {
        this.centerX = centerX
        this.centerY = centerY
        this.radius = radius
        this.iterations = iterations
        
        // Calculate reference orbit
        makeReferenceOrbit()
    }
    
    /**
     * Calculate single pixel iteration count
     */
    fun calculateIterations(
        pixelX: Double,
        pixelY: Double,
        viewWidth: Int,
        viewHeight: Int
    ): Int {
        // Convert pixel coordinates to delta (like the JS click handler)
        val deltaX = (pixelX / (viewWidth / 2.0) - 1.0).toFloat()
        val deltaY = (pixelY / (viewHeight / 2.0) - 1.0).toFloat()
        
        return calculatePixel(deltaX, deltaY)
    }
    
    /**
     * Get current state info
     */
    fun getStateInfo(): String {
        return "JS-based: orbit=$orbitLength, poly@$polyLimit, precision=${mathContext.precision}"
    }
}
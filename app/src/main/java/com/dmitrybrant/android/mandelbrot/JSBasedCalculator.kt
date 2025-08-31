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
    private var rawPolyCoeffs = Array(6) { Pair(0.0, 0) } // Raw coefficients before scaling
    private var polyLimit = 0
    
    // Scaled polynomial coefficients (like drawScene function)
    private var poly1 = FloatArray(4) // poly_scaled[0-3] 
    private var poly2 = FloatArray(4) // poly_scaled[4-5], polylim, poly_scale_exp[1]
    private var radiusState = Pair(0.0, 0) // [r, rexp] from drawScene
    
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
                    // Store raw polynomial coefficients (before scaling)
                    rawPolyCoeffs[0] = prevBx
                    rawPolyCoeffs[1] = prevBy 
                    rawPolyCoeffs[2] = prevCx
                    rawPolyCoeffs[3] = prevCy
                    rawPolyCoeffs[4] = prevDx
                    rawPolyCoeffs[5] = prevDy
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
        
        // Apply drawScene transformations (CRITICAL for correct rendering)
        applyDrawSceneTransformations(polylim)
        
        Log.d(TAG, "Reference orbit: $orbitLength iterations, poly limit: $polylim")
        
        // Debug polynomial coefficients
        Log.d(TAG, "Raw poly coeffs: B=[${rawPolyCoeffs[0]}, ${rawPolyCoeffs[1]}], C=[${rawPolyCoeffs[2]}, ${rawPolyCoeffs[3]}], D=[${rawPolyCoeffs[4]}, ${rawPolyCoeffs[5]}]")
        
        return Triple(orbit, polyCoeffs, polylim)
    }
    
    /**
     * Apply the critical transformations from drawScene function in JS
     * This is what was missing and causing solid color rendering!
     */
    private fun applyDrawSceneTransformations(polylim: Int = this.polyLimit) {
        // Get radius in scaled format (like drawScene)
        val rexp = getExp(radius)
        val rMantissa = radius.toDouble() / 2.0.pow(rexp)
        val r = Pair(rMantissa, rexp)
        radiusState = r
        
        Log.d(TAG, "Radius state: ${r.first} * 2^${r.second}")
        
        // Calculate polynomial scaling (exact port from drawScene)
        val polyScaleExp = mul(Pair(1.0, 0), maxabs(rawPolyCoeffs[0], rawPolyCoeffs[1]))
        val polyScale = Pair(1.0, -polyScaleExp.second)
        
        Log.d(TAG, "Poly scale: ${polyScale.first} * 2^${polyScale.second}, scale_exp: ${polyScaleExp.second}")
        
        // Scale polynomial coefficients (exact port from drawScene lines 566-573)
        val polyScaled = arrayOf(
            mul(polyScale, rawPolyCoeffs[0]),                           // Bx
            mul(polyScale, rawPolyCoeffs[1]),                           // By  
            mul(polyScale, mul(r, rawPolyCoeffs[2])),                   // r * Cx
            mul(polyScale, mul(r, rawPolyCoeffs[3])),                   // r * Cy
            mul(polyScale, mul(r, mul(r, rawPolyCoeffs[4]))),          // r² * Dx
            mul(polyScale, mul(r, mul(r, rawPolyCoeffs[5])))           // r² * Dy
        )
        
        // Convert to floaty (exact port from JS)
        poly1[0] = floaty(polyScaled[0]).toFloat()
        poly1[1] = floaty(polyScaled[1]).toFloat()
        poly1[2] = floaty(polyScaled[2]).toFloat()
        poly1[3] = floaty(polyScaled[3]).toFloat()
        
        poly2[0] = floaty(polyScaled[4]).toFloat()
        poly2[1] = floaty(polyScaled[5]).toFloat()
        poly2[2] = polylim.toFloat()
        poly2[3] = polyScaleExp.second.toFloat()
        
        // Store polylim for later use
        this.polyLimit = polylim
        
        Log.d(TAG, "Scaled poly1: [${poly1[0]}, ${poly1[1]}, ${poly1[2]}, ${poly1[3]}]")
        Log.d(TAG, "Scaled poly2: [${poly2[0]}, ${poly2[1]}, ${poly2[2]}, ${poly2[3]}]")
    }
    
    /**
     * Direct port of the shader perturbation logic from main.js
     */
    private fun calculatePixel(deltaX: Float, deltaY: Float): Int {
        // Debug first few pixels
        if (abs(deltaX) < 0.1f && abs(deltaY) < 0.1f) {
            Log.d(TAG, "Calculating pixel delta=($deltaX, $deltaY)")
        }
        
        // Use the same state setup as shader (lines 553-559 in JS)
        val uState = FloatArray(4)
        uState[0] = centerX.toFloat() // mandelbrot_state.center[0] 
        uState[1] = 20.0f // mandelbrot_state.cmapscale
        uState[2] = (1 + getExp(radius)).toFloat() // 1 + get_exp(mandelbrot_state.radius)
        uState[3] = iterations.toFloat() // mandelbrot_state.iterations
        
        if (abs(deltaX) < 0.1f && abs(deltaY) < 0.1f) {
            Log.d(TAG, "uState: [${uState[0]}, ${uState[1]}, ${uState[2]}, ${uState[3]}]")
        }
        
        // Shader variable setup (exact port from shader lines 222-224)
        var q = uState[2].toInt() - 1
        val cq = q
        q += poly2[3].toInt() // poly2[3] contains poly_scale_exp[1]
        
        if (abs(deltaX) < 0.1f && abs(deltaY) < 0.1f) {
            Log.d(TAG, "q=$q, cq=$cq, poly2[3]=${poly2[3]}")
        }
        
        var dcx = deltaX.toDouble()
        var dcy = deltaY.toDouble()
        
        // Series approximation using SCALED coefficients (shader lines 230-237)
        val sqrx = dcx * dcx - dcy * dcy
        val sqry = 2.0 * dcx * dcy
        
        // Use poly1 (scaled coefficients) instead of raw polyCoeffs
        var dx = poly1[0] * dcx - poly1[1] * dcy + poly1[2] * sqrx - poly1[3] * sqry
        var dy = poly1[0] * dcy + poly1[1] * dcx + poly1[2] * sqry + poly1[3] * sqrx
        
        if (abs(deltaX) < 0.1f && abs(deltaY) < 0.1f) {
            Log.d(TAG, "Initial dx=$dx, dy=$dy")
        }
        
        var k = poly2[2].toInt() // polylim from poly2[2]
        var j = k
        
        // Main iteration loop (direct port of shader logic)
        for (i in k until minOf(iterations, orbitLength / 3)) {
            j++
            k++
            
            if (k * 3 + 2 >= orbit.size) break
            
            val os = orbit[3 * (k - 1) + 2].toInt()
            dcx = deltaX * 2.0.pow(-q + cq - os)
            dcy = deltaY * 2.0.pow(-q + cq - os)
            val unS = 2.0.pow(q.toDouble() - orbit[3 * (k - 1) + 2])
            
            if (!unS.isFinite()) continue
            
            val x = orbit[3 * k].toDouble()
            val y = orbit[3 * k + 1].toDouble()
            
            val tx = 2.0 * x * dx - 2.0 * y * dy + unS * dx * dx - unS * dy * dy + dcx
            dy = 2.0 * x * dy + 2.0 * y * dx + unS * 2.0 * dx * dy + dcy
            dx = tx
            
            q += os
            val S = 2.0.pow(q)
            
            val fx = x * 2.0.pow(orbit[3 * k + 2].toDouble()) + S * dx
            val fy = y * 2.0.pow(orbit[3 * k + 2].toDouble()) + S * dy
            
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
        Log.d(TAG, "Setting parameters: center=($centerX, $centerY), radius=$radius, iterations=$iterations")
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
        // Convert pixel coordinates to normalized coordinates [-1, 1] 
        // matching WebGL vertex shader: delta = vec2(aVertexPosition[0], aVertexPosition[1])
        // where aVertexPosition ranges from [-1, 1] for both x and y
        val deltaX = (2.0 * pixelX / viewWidth - 1.0).toFloat()
        val deltaY = (1.0 - 2.0 * pixelY / viewHeight).toFloat() // Flip Y for screen coordinates
        
        return calculatePixel(deltaX, deltaY)
    }
    
    /**
     * Get current state info
     */
    fun getStateInfo(): String {
        return "JS-based: orbit=$orbitLength, poly@$polyLimit, precision=${mathContext.precision}"
    }
}
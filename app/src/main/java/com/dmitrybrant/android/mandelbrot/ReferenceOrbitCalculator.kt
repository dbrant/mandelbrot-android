package com.dmitrybrant.android.mandelbrot

import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*

/**
 * Reference orbit calculator - direct port from main.js make_reference_orbit function
 */
class ReferenceOrbitCalculator {
    
    companion object {
        private const val TAG = "ReferenceOrbitCalc"
        private const val ORBIT_SIZE = 1024 * 1024
    }
    
    private val mathContext = MathContext(360, RoundingMode.HALF_UP) // High precision like JS
    
    /**
     * Direct port of make_reference_orbit() from main.js
     * Returns: (orbitArray, poly1Array, poly2Array, orbitLength)
     */
    fun makeReferenceOrbit(
        centerX: BigDecimal,
        centerY: BigDecimal, 
        radius: BigDecimal,
        iterations: Int
    ): Tuple4<FloatArray, FloatArray, FloatArray, Int> {
        
        Log.d(TAG, "Making reference orbit: center=($centerX, $centerY), radius=$radius")
        
        val cx = centerX
        val cy = centerY
        var x = BigDecimal.ZERO
        var y = BigDecimal.ZERO
        
        // Initialize orbit array
        val orbit = FloatArray(ORBIT_SIZE) { -1f }
        
        // Polynomial coefficients (scaled floating point: [mantissa, exponent])
        var Bx = Pair(0.0, 0)
        var By = Pair(0.0, 0)  
        var Cx = Pair(0.0, 0)
        var Cy = Pair(0.0, 0)
        var Dx = Pair(0.0, 0)
        var Dy = Pair(0.0, 0)
        var polylim = 0
        var notFailed = true
        
        var i = 0
        while (i < iterations) {
            // Get exponents for scaling
            val xExponent = if (x.compareTo(BigDecimal.ZERO) == 0) -10000 else getExp(x)
            val yExponent = if (y.compareTo(BigDecimal.ZERO) == 0) -10000 else getExp(y)
            
            var scaleExponent = maxOf(xExponent, yExponent)
            if (scaleExponent < -10000) {
                scaleExponent = 0
            }
            
            // Store orbit point with scaling (exact port from JS)
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
            val oldBx = Bx; val oldBy = By; val oldCx = Cx; val oldCy = Cy; val oldDx = Dx; val oldDy = Dy
            
            Bx = add(mul(Pair(2.0, 0), sub(mul(fx, Bx), mul(fy, By))), Pair(1.0, 0))
            By = mul(Pair(2.0, 0), add(mul(fx, By), mul(fy, Bx)))
            Cx = sub(add(mul(Pair(2.0, 0), sub(mul(fx, Cx), mul(fy, Cy))), mul(Bx, Bx)), mul(By, By))
            Cy = add(mul(Pair(2.0, 0), add(mul(fx, Cy), mul(fy, Cx))), mul(mul(Pair(2.0, 0), Bx), By))
            Dx = mul(Pair(2.0, 0), add(sub(mul(fx, Dx), mul(fy, Dy)), sub(mul(Cx, Bx), mul(Cy, By))))
            Dy = mul(Pair(2.0, 0), add(add(add(mul(fx, Dy), mul(fy, Dx)), mul(Cx, By)), mul(Cy, Bx)))
            
            // Check polynomial validity
            val radiusExp = getExp(radius)
            if (i == 0 || gt(
                maxabs(Cx, Cy),
                mul(Pair(1000.0, radiusExp), maxabs(Dx, Dy))
            )) {
                if (notFailed) {
                    // Store current polynomial coefficients 
                    val rawPolyCoeffs = arrayOf(Bx, By, Cx, Cy, Dx, Dy)
                    polylim = i
                    
                    // Apply drawScene transformations (polynomial scaling)
                    val (poly1Array, poly2Array) = applyDrawSceneTransformations(rawPolyCoeffs, radius, polylim)
                    
                    if (i == 0) {
                        Log.d(TAG, "Initial polynomial coeffs stored: B=[$Bx, $By], C=[$Cx, $Cy]")
                    }
                }
            } else {
                notFailed = false
            }
            
            // Check escape condition
            val fx2 = if (x.compareTo(BigDecimal.ZERO) == 0) Pair(0.0, 0) else Pair(x.toDouble(), getExp(x))
            val fy2 = if (y.compareTo(BigDecimal.ZERO) == 0) Pair(0.0, 0) else Pair(y.toDouble(), getExp(y))
            
            if (gt(add(mul(fx2, fx2), mul(fy2, fy2)), Pair(400.0, 0))) {
                Log.d(TAG, "Orbit escaped at iteration ${i + 1}")
                break
            }
            
            i++
        }
        
        val orbitLength = if (i >= iterations) iterations else i + 1
        
        // Get final polynomial arrays (if any were stored)
        val rawPolyCoeffs = arrayOf(Bx, By, Cx, Cy, Dx, Dy)
        val (finalPoly1, finalPoly2) = applyDrawSceneTransformations(rawPolyCoeffs, radius, polylim)
        
        Log.d(TAG, "Reference orbit complete: $orbitLength iterations, poly limit: $polylim")
        Log.d(TAG, "Final poly1: [${finalPoly1[0]}, ${finalPoly1[1]}, ${finalPoly1[2]}, ${finalPoly1[3]}]")
        Log.d(TAG, "Final poly2: [${finalPoly2[0]}, ${finalPoly2[1]}, ${finalPoly2[2]}, ${finalPoly2[3]}]")
        
        return Tuple4(orbit, finalPoly1, finalPoly2, orbitLength)
    }
    
    /**
     * Apply the critical transformations from drawScene function (lines 562-584 in main.js)
     */
    private fun applyDrawSceneTransformations(
        rawPolyCoeffs: Array<Pair<Double, Int>>,
        radius: BigDecimal,
        polylim: Int
    ): Pair<FloatArray, FloatArray> {
        
        // Get radius in scaled format
        val rexp = getExp(radius)
        val rMantissa = radius.toDouble() / 2.0.pow(rexp)
        val r = Pair(rMantissa, rexp)
        
        // Calculate polynomial scaling (exact port from drawScene line 562)
        val polyScaleExp = mul(Pair(1.0, 0), maxabs(rawPolyCoeffs[0], rawPolyCoeffs[1]))
        val polyScale = Pair(1.0, -polyScaleExp.second)
        
        // Scale polynomial coefficients (exact port from drawScene lines 566-573)
        val polyScaled = arrayOf(
            mul(polyScale, rawPolyCoeffs[0]),                           // Bx
            mul(polyScale, rawPolyCoeffs[1]),                           // By  
            mul(polyScale, mul(r, rawPolyCoeffs[2])),                   // r * Cx
            mul(polyScale, mul(r, rawPolyCoeffs[3])),                   // r * Cy
            mul(polyScale, mul(r, mul(r, rawPolyCoeffs[4]))),          // r² * Dx
            mul(polyScale, mul(r, mul(r, rawPolyCoeffs[5])))           // r² * Dy
        )
        
        // Convert to float arrays (exact port from JS .map(floaty))
        val poly1 = floatArrayOf(
            floaty(polyScaled[0]).toFloat(),
            floaty(polyScaled[1]).toFloat(),
            floaty(polyScaled[2]).toFloat(),
            floaty(polyScaled[3]).toFloat()
        )
        
        val poly2 = floatArrayOf(
            floaty(polyScaled[4]).toFloat(),
            floaty(polyScaled[5]).toFloat(),
            polylim.toFloat(),
            polyScaleExp.second.toFloat()
        )
        
        return Pair(poly1, poly2)
    }
    
    // Scaled floating-point arithmetic operations (from JS helper functions)
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
        
        // Handle zero values properly
        if (am == 0.0 && bm == 0.0) return Pair(0.0, 0)
        if (am == 0.0) return Pair(abs(bm), be)
        if (bm == 0.0) return Pair(abs(am), ae)
        
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
}

// Helper data class for 4-tuple return type
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
package com.dmitrybrant.android.mandelbrot.simple

import android.graphics.Bitmap
import android.util.Log

class MandelbrotCalculator {

    private var power: Int = DEFAULT_POWER
    private var numIterations: Int = DEFAULT_ITERATIONS
    private var xmin: Double = DEFAULT_X_CENTER - DEFAULT_X_EXTENT / 2.0
    private var xmax: Double = DEFAULT_X_CENTER + DEFAULT_X_EXTENT / 2.0
    private var ymin: Double = DEFAULT_Y_CENTER - DEFAULT_X_EXTENT / 2.0
    private var ymax: Double = DEFAULT_Y_CENTER + DEFAULT_X_EXTENT / 2.0
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var isJulia: Boolean = false
    private var juliaX: Double = DEFAULT_JULIA_X_CENTER
    private var juliaY: Double = DEFAULT_JULIA_Y_CENTER


    private var colorPalette: IntArray = intArrayOf()
    private var numPaletteColors: Int = 0


    private lateinit var pixelBuffer: IntArray
    private var x0array = DoubleArray(0)

    private var terminateJob: Boolean = false

    fun setParameters(
        power: Int,
        numIterations: Int,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        isJulia: Boolean,
        juliaX: Double,
        juliaY: Double,
        viewWidth: Int,
        viewHeight: Int
    ) {
        this.power = power
        this.numIterations = numIterations
        this.xmin = xMin
        this.xmax = xMax
        this.ymin = yMin
        this.ymax = yMax
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.isJulia = isJulia
        this.juliaX = juliaX
        this.juliaY = juliaY
        this.x0array = DoubleArray(viewWidth * 2)
        this.terminateJob = false
    }

    fun setBitmap(bmp: Bitmap?) {
        if (bmp == null) return
        val bufferSize = (bmp.width + 32) * (bmp.height + 32)
        pixelBuffer = IntArray(bufferSize)
    }

    fun updateBitmap(bmp: Bitmap?) {
        if (bmp == null) return
        bmp.setPixels(pixelBuffer, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    fun setColorPalette(colors: IntArray?, numColors: Int) {
        if (colors == null) return
        numPaletteColors = numColors
        colorPalette = colors.copyOf(minOf(numColors, MAX_PALETTE_COLORS))
    }

    fun signalTerminate() {
        terminateJob = true
    }

    fun drawFractal(
        startX: Int,
        startY: Int,
        startWidth: Int,
        startHeight: Int,
        level: Int,
        doAll: Boolean
    ) {
        if (level < 1) return

        val maxY = startY + startHeight
        val maxX = startX + startWidth
        val xscale = (xmax - xmin) / viewWidth
        val yscale = (ymax - ymin) / viewHeight
        
        val iterScale = if (numIterations < numPaletteColors) {
            numPaletteColors / numIterations
        } else {
            1
        }

        Log.d("MandelbrotCalculator", ">>> drawing at level $level")

        // Pre-calculate x values
        for (px in startX until maxX) {
            x0array[px] = xmin + px * xscale
        }

        var yindex = 0
        var py = startY
        while (py < maxY && !terminateJob) {
            val y0 = ymin + py * yscale
            val yptr = py * viewWidth
            
            var xindex = 0
            var px = startX
            while (px < maxX) {
                if (!doAll) {
                    if ((yindex % 2 == 0) && (xindex % 2 == 0)) {
                        px += level
                        xindex++
                        continue
                    }
                }

                val iteration = when (power) {
                    2 -> calculateIterations2(x0array[px], y0, numIterations)
                    3 -> calculateIterations3(x0array[px], y0, numIterations)
                    4 -> calculateIterations4(x0array[px], y0, numIterations)
                    else -> calculateIterations2(x0array[px], y0, numIterations)
                }

                val color = if (iteration >= numIterations) {
                    0
                } else {
                    colorPalette[(iteration * iterScale) % numPaletteColors]
                }

                // Fill the level x level block
                if (level > 1) {
                    var yptr2 = yptr
                    for (iy in py until minOf(py + level, maxY)) {
                        val maxIx = minOf(px + level, maxX)
                        for (ix in px until maxIx) {
                            pixelBuffer[yptr2 + ix] = color
                        }
                        yptr2 += viewWidth
                    }
                } else {
                    pixelBuffer[yptr + px] = color
                }
                
                px += level
                xindex++
            }
            py += level
            yindex++
        }
    }

    private fun calculateIterations2(
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (isJulia) {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            
            while (iteration < maxIterations) {
                val x2 = x * x
                val y2 = y * y
                if (x2 + y2 > 4.0) {
                    break
                }
                y = 2 * x * y + juliaY
                x = x2 - y2 + juliaX
                iteration++
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var iteration = 0
            
            while (x2 + y2 < 4.0 && iteration <= maxIterations) {
                y = 2 * x * y + y0Val
                x = x2 - y2 + x0Val
                x2 = x * x
                y2 = y * y
                iteration++
            }
            return iteration
        }
    }

    private fun calculateIterations3(
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (isJulia) {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            
            while (iteration < maxIterations) {
                val x2 = x * x
                val y2 = y * y
                val x3 = x2 * x
                val y3 = y2 * y
                if (x2 + y2 > 4.0) {
                    break
                }
                y = (3 * x2 * y) - y3 + juliaY
                x = x3 - (3 * y2 * x) + juliaX
                iteration++
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var x3 = 0.0
            var y3 = 0.0
            var iteration = 0
            
            while (x2 + y2 < 4.0 && iteration <= maxIterations) {
                y = (3 * x2 * y) - y3 + y0Val
                x = x3 - (3 * y2 * x) + x0Val
                x2 = x * x
                y2 = y * y
                x3 = x2 * x
                y3 = y2 * y
                iteration++
            }
            return iteration
        }
    }

    private fun calculateIterations4(
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (isJulia) {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            
            while (iteration < maxIterations) {
                val x2 = x * x
                val y2 = y * y
                val x3 = x2 * x
                val y3 = y2 * y
                val x4 = x3 * x
                val y4 = y3 * y
                if (x2 + y2 > 4.0) {
                    break
                }
                y = (4 * x3 * y) - (4 * y3 * x) + juliaY
                x = x4 + y4 - (6 * x2 * y2) + juliaX
                iteration++
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var x3 = 0.0
            var y3 = 0.0
            var x4 = 0.0
            var y4 = 0.0
            var iteration = 0
            
            while (x2 + y2 < 4.0 && iteration <= maxIterations) {
                y = (4 * x3 * y) - (4 * y3 * x) + y0Val
                x = x4 + y4 - (6 * x2 * y2) + x0Val
                x2 = x * x
                y2 = y * y
                x3 = x2 * x
                y3 = y2 * y
                x4 = x3 * x
                y4 = y3 * y
                iteration++
            }
            return iteration
        }
    }

    companion object {
        private val MAX_PALETTE_COLORS = 512
        const val DEFAULT_POWER = 2
        const val DEFAULT_ITERATIONS = 128
        const val MAX_ITERATIONS = 2048
        const val MIN_ITERATIONS = 2
        const val DEFAULT_X_CENTER = -0.5
        const val DEFAULT_Y_CENTER = 0.0
        const val DEFAULT_X_EXTENT = 3.0
        const val DEFAULT_JULIA_X_CENTER = 0.0
        const val DEFAULT_JULIA_Y_CENTER = 0.0
        const val DEFAULT_JULIA_EXTENT = 3.0
    }
}
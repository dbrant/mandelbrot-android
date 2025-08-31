package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap

data class FractalParams(
    var power: Int = 2,
    var numIterations: Int = 100,
    var xmin: Double = -2.0,
    var xmax: Double = 1.0,
    var ymin: Double = -1.5,
    var ymax: Double = 1.5,
    var viewWidth: Int = 0,
    var viewHeight: Int = 0,
    var isJulia: Boolean = false,
    var juliaX: Double = 0.0,
    var juliaY: Double = 0.0,
    var colorPalette: IntArray = intArrayOf(),
    var numPaletteColors: Int = 0,
    var pixelBuffer: IntArray? = null,
    var x0array: DoubleArray? = null,
    @Volatile var terminateJob: Boolean = false
)

object MandelbrotCalculator {
    private const val MAX_PALETTE_COLORS = 512
    private val params = Array(2) { FractalParams() }

    fun setParameters(
        paramIndex: Int,
        power: Int,
        numIterations: Int,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        isJulia: Int,
        juliaX: Double,
        juliaY: Double,
        viewWidth: Int,
        viewHeight: Int
    ) {
        params[paramIndex].apply {
            this.power = power
            this.numIterations = numIterations
            this.xmin = xMin
            this.xmax = xMax
            this.ymin = yMin
            this.ymax = yMax
            this.viewWidth = viewWidth
            this.viewHeight = viewHeight
            this.isJulia = isJulia != 0
            this.juliaX = juliaX
            this.juliaY = juliaY
            this.x0array = DoubleArray(viewWidth * 2)
            this.terminateJob = false
        }
    }

    fun releaseParameters(paramIndex: Int) {
        params[paramIndex].x0array = null
    }

    fun setBitmap(paramIndex: Int, bmp: Bitmap?) {
        if (bmp == null) return
        
        val bufferSize = (bmp.width + 32) * (bmp.height + 32)
        params[paramIndex].pixelBuffer = IntArray(bufferSize)
    }

    fun updateBitmap(paramIndex: Int, bmp: Bitmap?) {
        if (bmp == null) return
        
        val pixelBuffer = params[paramIndex].pixelBuffer ?: return
        val pixels = IntArray(bmp.width * bmp.height)
        
        // Copy from our buffer to the array
        System.arraycopy(pixelBuffer, 0, pixels, 0, bmp.width * bmp.height)
        
        // Set pixels to bitmap
        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    }

    fun releaseBitmap(paramIndex: Int) {
        params[paramIndex].pixelBuffer = null
    }

    fun setColorPalette(paramIndex: Int, colors: IntArray?, numColors: Int) {
        if (colors == null) return
        
        params[paramIndex].apply {
            numPaletteColors = numColors
            colorPalette = colors.copyOf(minOf(numColors, MAX_PALETTE_COLORS))
        }
    }

    fun signalTerminate(paramIndex: Int) {
        params[paramIndex].terminateJob = true
    }

    fun drawFractal(
        paramIndex: Int,
        startX: Int,
        startY: Int,
        startWidth: Int,
        startHeight: Int,
        level: Int,
        doAll: Boolean
    ) {
        if (level < 1) return
        
        val param = params[paramIndex]
        val pixelBuffer = param.pixelBuffer ?: return
        val x0array = param.x0array ?: return
        
        val maxY = startY + startHeight
        val maxX = startX + startWidth
        val xscale = (param.xmax - param.xmin) / param.viewWidth
        val yscale = (param.ymax - param.ymin) / param.viewHeight
        val numIterations = param.numIterations
        val numPaletteColors = param.numPaletteColors
        
        val iterScale = if (numIterations < numPaletteColors) {
            numPaletteColors / numIterations
        } else {
            1
        }

        // Pre-calculate x values
        for (px in startX until maxX) {
            x0array[px] = param.xmin + px * xscale
        }

        var yindex = 0
        var py = startY
        while (py < maxY && !param.terminateJob) {
            val y0 = param.ymin + py * yscale
            val yptr = py * param.viewWidth
            
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

                val iteration = when (param.power) {
                    2 -> calculateIterations2(param, x0array[px], y0, numIterations)
                    3 -> calculateIterations3(param, x0array[px], y0, numIterations)
                    4 -> calculateIterations4(param, x0array[px], y0, numIterations)
                    else -> calculateIterations2(param, x0array[px], y0, numIterations)
                }

                val color = if (iteration >= numIterations) {
                    0
                } else {
                    param.colorPalette[(iteration * iterScale) % numPaletteColors]
                }

                // Fill the level x level block
                if (level > 1) {
                    var yptr2 = yptr
                    for (iy in py until minOf(py + level, maxY)) {
                        val maxIx = minOf(px + level, maxX)
                        for (ix in px until maxIx) {
                            pixelBuffer[yptr2 + ix] = color
                        }
                        yptr2 += param.viewWidth
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
        param: FractalParams,
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (param.isJulia) {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            
            while (iteration < maxIterations) {
                val x2 = x * x
                val y2 = y * y
                if (x2 + y2 > 4.0) {
                    break
                }
                y = 2 * x * y + param.juliaY
                x = x2 - y2 + param.juliaX
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
        param: FractalParams,
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (param.isJulia) {
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
                y = (3 * x2 * y) - y3 + param.juliaY
                x = x3 - (3 * y2 * x) + param.juliaX
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
        param: FractalParams,
        x0Val: Double,
        y0Val: Double,
        maxIterations: Int
    ): Int {
        if (param.isJulia) {
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
                y = (4 * x3 * y) - (4 * y3 * x) + param.juliaY
                x = x4 + y4 - (6 * x2 * y2) + param.juliaX
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
}
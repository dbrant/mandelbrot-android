package com.dmitrybrant.android.mandelbrot

import android.graphics.Bitmap
import java.math.BigDecimal
import kotlin.math.pow

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
    @Volatile var terminateJob: Boolean = false,
    // Deep zoom parameters
    var centerX: BigDecimal = BigDecimal("-0.5"),
    var centerY: BigDecimal = BigDecimal("0.0"),
    var zoomFactor: Double = 1.0,
    var useDeepZoom: Boolean = false
)

object MandelbrotCalculator {
    private const val MAX_PALETTE_COLORS = 512
    private val params = Array(2) { FractalParams() }
    private val deepCalculator = DeepMandelbrotCalculator()
    
    // Threshold for switching to deep zoom mode
    private const val DEEP_ZOOM_THRESHOLD = 1e12

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
            
            // Calculate center and zoom for deep zoom mode
            this.centerX = BigDecimal((xMin + xMax) / 2.0)
            this.centerY = BigDecimal((yMin + yMax) / 2.0)
            this.zoomFactor = 4.0 / (xMax - xMin)
            this.useDeepZoom = this.zoomFactor > DEEP_ZOOM_THRESHOLD
            
            // Prepare deep zoom if needed
            if (this.useDeepZoom) {
                deepCalculator.prepareDeepZoom(
                    this.centerX,
                    this.centerY,
                    this.zoomFactor,
                    numIterations,
                    power
                )
            }
        }
    }
    
    /**
     * Set parameters using BigDecimal center coordinates for unlimited zoom
     */
    fun setParametersDeepZoom(
        paramIndex: Int,
        power: Int,
        numIterations: Int,
        centerX: BigDecimal,
        centerY: BigDecimal,
        zoomFactor: Double,
        isJulia: Int,
        juliaX: Double,
        juliaY: Double,
        viewWidth: Int,
        viewHeight: Int
    ) {
        params[paramIndex].apply {
            this.power = power
            this.numIterations = numIterations
            this.viewWidth = viewWidth
            this.viewHeight = viewHeight
            this.isJulia = isJulia != 0
            this.juliaX = juliaX
            this.juliaY = juliaY
            this.x0array = DoubleArray(viewWidth * 2)
            this.terminateJob = false
            
            // Deep zoom parameters
            this.centerX = centerX
            this.centerY = centerY
            this.zoomFactor = zoomFactor
            this.useDeepZoom = zoomFactor > DEEP_ZOOM_THRESHOLD
            
            // Calculate bounds for legacy compatibility
            val scale = 4.0 / zoomFactor
            val aspectRatio = viewWidth.toDouble() / viewHeight.toDouble()
            val halfWidth = scale * aspectRatio / 2.0
            val halfHeight = scale / 2.0
            
            try {
                this.xmin = centerX.subtract(BigDecimal(halfWidth)).toDouble()
                this.xmax = centerX.add(BigDecimal(halfWidth)).toDouble()
                this.ymin = centerY.subtract(BigDecimal(halfHeight)).toDouble()
                this.ymax = centerY.add(BigDecimal(halfHeight)).toDouble()
            } catch (e: ArithmeticException) {
                // If coordinates are too precise for double, use deep zoom mode
                this.useDeepZoom = true
            }
            
            // Prepare deep zoom
            if (this.useDeepZoom) {
                deepCalculator.prepareDeepZoom(
                    this.centerX,
                    this.centerY,
                    this.zoomFactor,
                    numIterations,
                    power
                )
            }
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
        doAll: Int
    ) {
        drawPixels(paramIndex, startX, startY, startWidth, startHeight, level, doAll != 0)
    }

    private fun drawPixels(
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

                val iteration = if (param.useDeepZoom) {
                    // Use deep zoom calculation
                    deepCalculator.calculateIterations(
                        px.toDouble(),
                        py.toDouble(),
                        param.centerX,
                        param.centerY,
                        param.zoomFactor,
                        param.viewWidth,
                        param.viewHeight,
                        numIterations,
                        param.power,
                        param.isJulia,
                        param.juliaX,
                        param.juliaY
                    )
                } else {
                    // Use standard double precision calculation
                    when (param.power) {
                        2 -> calculateIterations2(param, x0array[px], y0, numIterations)
                        3 -> calculateIterations3(param, x0array[px], y0, numIterations)
                        4 -> calculateIterations4(param, x0array[px], y0, numIterations)
                        else -> calculateIterations2(param, x0array[px], y0, numIterations)
                    }
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
    
    /**
     * Get zoom information for display
     */
    fun getZoomInfo(paramIndex: Int): String {
        val param = params[paramIndex]
        return if (param.useDeepZoom) {
            "Deep Zoom: ${String.format("%.2e", param.zoomFactor)}x"
        } else {
            "Zoom: ${String.format("%.2f", param.zoomFactor)}x"
        }
    }
    
    /**
     * Check if currently using deep zoom
     */
    fun isUsingDeepZoom(paramIndex: Int): Boolean {
        return params[paramIndex].useDeepZoom
    }
    
    /**
     * Get current center coordinates as strings for display
     */
    fun getCenterCoordinates(paramIndex: Int): Pair<String, String> {
        val param = params[paramIndex]
        return if (param.useDeepZoom) {
            Pair(param.centerX.toString(), param.centerY.toString())
        } else {
            val centerX = (param.xmin + param.xmax) / 2.0
            val centerY = (param.ymin + param.ymax) / 2.0
            Pair(centerX.toString(), centerY.toString())
        }
    }
    
    /**
     * Zoom into a specific point
     */
    fun zoomToPoint(
        paramIndex: Int,
        screenX: Int,
        screenY: Int,
        zoomMultiplier: Double
    ) {
        val param = params[paramIndex]
        
        if (param.useDeepZoom || param.zoomFactor * zoomMultiplier > DEEP_ZOOM_THRESHOLD) {
            // Convert screen coordinates to world coordinates
            val aspectRatio = param.viewWidth.toDouble() / param.viewHeight.toDouble()
            val scale = BigDecimal(4.0 / param.zoomFactor)
            
            val offsetX = BigDecimal((screenX - param.viewWidth / 2.0) / param.viewWidth * aspectRatio)
            val offsetY = BigDecimal((screenY - param.viewHeight / 2.0) / param.viewHeight)
            
            val newCenterX = param.centerX.add(scale.multiply(offsetX))
            val newCenterY = param.centerY.add(scale.multiply(offsetY))
            val newZoomFactor = param.zoomFactor * zoomMultiplier
            
            setParametersDeepZoom(
                paramIndex,
                param.power,
                param.numIterations,
                newCenterX,
                newCenterY,
                newZoomFactor,
                if (param.isJulia) 1 else 0,
                param.juliaX,
                param.juliaY,
                param.viewWidth,
                param.viewHeight
            )
        } else {
            // Use double precision coordinates
            val xRange = param.xmax - param.xmin
            val yRange = param.ymax - param.ymin
            
            val clickX = param.xmin + (screenX.toDouble() / param.viewWidth) * xRange
            val clickY = param.ymin + (screenY.toDouble() / param.viewHeight) * yRange
            
            val newXRange = xRange / zoomMultiplier
            val newYRange = yRange / zoomMultiplier
            
            setParameters(
                paramIndex,
                param.power,
                param.numIterations,
                clickX - newXRange / 2,
                clickX + newXRange / 2,
                clickY - newYRange / 2,
                clickY + newYRange / 2,
                if (param.isJulia) 1 else 0,
                param.juliaX,
                param.juliaY,
                param.viewWidth,
                param.viewHeight
            )
        }
    }
}
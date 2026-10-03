package com.dmitrybrant.android.mandelbrot.simple

import android.graphics.Bitmap
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MandelbrotCalculator {

    private var power: Int = DEFAULT_POWER
    private var numIterations: Int = DEFAULT_ITERATIONS
    private var ymin: Double = DEFAULT_Y_CENTER - DEFAULT_X_EXTENT / 2.0
    private var yscale: Double = 0.0
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var isJulia: Boolean = false
    private var juliaX: Double = DEFAULT_JULIA_X_CENTER
    private var juliaY: Double = DEFAULT_JULIA_Y_CENTER


    private var colorPalette: IntArray = intArrayOf()

    // Color for each iteration count, where counts >= numIterations (inside the set) are black.
    private var colorTable = IntArray(0)


    private lateinit var pixelBuffer: IntArray
    private var x0array = DoubleArray(0)

    @Volatile private var terminateJob: Boolean = false

    private val executor = ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 5, TimeUnit.SECONDS, LinkedBlockingQueue()) {
        Thread(it, "MandelbrotWorker").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

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
        this.ymin = yMin
        this.yscale = (yMax - yMin) / viewHeight
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.isJulia = isJulia
        this.juliaX = juliaX
        this.juliaY = juliaY

        // Pre-calculate x values
        val xscale = (xMax - xMin) / viewWidth
        this.x0array = DoubleArray(viewWidth) { xMin + it * xscale }

        val numColors = colorPalette.size
        val iterScale = if (numIterations < numColors) numColors / numIterations else 1
        this.colorTable = IntArray(numIterations + 1) {
            if (it >= numIterations || numColors == 0) 0 else colorPalette[(it * iterScale) % numColors]
        }
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

    fun setColorPalette(colors: IntArray?) {
        if (colors == null) return
        colorPalette = colors.copyOf()
    }

    fun signalTerminate() {
        terminateJob = true
    }

    /**
     * Draws the whole view in blocks of level x level pixels, spreading the rows over all CPU
     * cores, and returns when done. Unless doAll is set, the pixels already drawn by the previous
     * pass (at twice this level) are skipped.
     */
    fun drawFractal(level: Int, doAll: Boolean) {
        if (level < 1) return
        // Rows are handed out one at a time, so that every thread stays busy until the end.
        val nextRow = AtomicInteger()
        val tasks = List(NUM_THREADS) { Callable { drawRows(level, doAll, nextRow) } }
        executor.invokeAll(tasks).forEach { it.get() }
    }

    private fun drawRows(level: Int, doAll: Boolean, nextRow: AtomicInteger) {
        val columns = IntArray(viewWidth)
        val iterations = IntArray(viewWidth)
        while (!terminateJob) {
            val yindex = nextRow.getAndIncrement()
            val py = yindex * level
            if (py >= viewHeight) break
            drawRow(py, yindex, level, doAll, columns, iterations)
        }
    }

    private fun drawRow(py: Int, yindex: Int, level: Int, doAll: Boolean, columns: IntArray, iterations: IntArray) {
        val y0 = ymin + py * yscale
        val checkCardioid = !isJulia && power == 2

        // Collect the pixels in this row that need calculating.
        val skipEvenColumns = !doAll && yindex % 2 == 0
        val step = if (skipEvenColumns) level * 2 else level
        var px = if (skipEvenColumns) level else 0
        var count = 0
        while (px < viewWidth) {
            if (checkCardioid && isInCardioidOrBulb(x0array[px], y0)) {
                fillBlock(px, py, level, 0)
            } else {
                columns[count++] = px
            }
            px += step
        }

        // A Mandelbrot orbit goes 0 -> c -> ..., so it is started at c with one iteration done.
        val maxIterations = if (isJulia) numIterations else numIterations - 1
        when (power) {
            3 -> iterateRow3(columns, count, y0, maxIterations, iterations)
            4 -> iterateRow4(columns, count, y0, maxIterations, iterations)
            else -> iterateRow2(columns, count, y0, maxIterations, iterations)
        }

        val offset = if (isJulia) 0 else 1
        for (i in 0 until count) {
            fillBlock(columns[i], py, level, colorTable[iterations[i] + offset])
        }
    }

    // Fill the level x level block
    private fun fillBlock(px: Int, py: Int, level: Int, color: Int) {
        if (level == 1) {
            pixelBuffer[py * viewWidth + px] = color
            return
        }
        val maxX = minOf(px + level, viewWidth)
        val maxY = minOf(py + level, viewHeight)
        var yptr = py * viewWidth
        for (iy in py until maxY) {
            pixelBuffer.fill(color, yptr + px, yptr + maxX)
            yptr += viewWidth
        }
    }

    /**
     * Points in the main cardioid and the period-2 bulb never escape, and are the most expensive
     * ones to iterate, so they are checked for directly (for power 2 only).
     */
    private fun isInCardioidOrBulb(x: Double, y: Double): Boolean {
        val y2 = y * y
        val xq = x - 0.25
        val q = xq * xq + y2
        if (q * (q + xq) <= 0.25 * y2) {
            return true
        }
        val xb = x + 1.0
        return xb * xb + y2 <= 0.0625
    }

    // z^2 = (x^2 - y^2) + 2xy i
    private fun iterateRow2(columns: IntArray, count: Int, y0: Double, maxIterations: Int, iterations: IntArray) =
        iterateRow(columns, count, y0, maxIterations, iterations,
            { _, _, x2, y2 -> x2 - y2 },
            { x, y, _, _ -> 2 * x * y })

    // z^3 = x(x^2 - 3y^2) + y(3x^2 - y^2) i
    private fun iterateRow3(columns: IntArray, count: Int, y0: Double, maxIterations: Int, iterations: IntArray) =
        iterateRow(columns, count, y0, maxIterations, iterations,
            { x, _, x2, y2 -> x * (x2 - 3 * y2) },
            { _, y, x2, y2 -> y * (3 * x2 - y2) })

    // z^4 = (z^2)^2 = (a + bi)^2, where a = x^2 - y^2 and b = 2xy
    private fun iterateRow4(columns: IntArray, count: Int, y0: Double, maxIterations: Int, iterations: IntArray) =
        iterateRow(columns, count, y0, maxIterations, iterations,
            { x, y, x2, y2 -> val a = x2 - y2; val b = 2 * x * y; (a + b) * (a - b) },
            { x, y, x2, y2 -> 2 * (x2 - y2) * (2 * x * y) })

    /**
     * Calculates the iteration counts of the given pixels in a row (at height y0), where nextX and
     * nextY give the real and imaginary parts of z^power from x, y, x^2 and y^2.
     *
     * Two pixels (A and B) are iterated side by side, since each iteration depends on the previous
     * one, and a single orbit leaves most of the CPU's floating point units idle. Whenever one of
     * them finishes, the next pixel is loaded in its place.
     *
     * Orbits are also checked for exactly repeating a previous value (Brent's method), in which
     * case they are periodic and will never escape. This makes most points inside the set much
     * cheaper, without changing the result.
     */
    private inline fun iterateRow(
        columns: IntArray, count: Int, y0: Double, maxIterations: Int, iterations: IntArray,
        nextX: (x: Double, y: Double, x2: Double, y2: Double) -> Double,
        nextY: (x: Double, y: Double, x2: Double, y2: Double) -> Double
    ) {
        val x0s = x0array
        val julia = isJulia
        val jx = juliaX
        val jy = juliaY
        var next = 0

        // Both slots start out empty (i.e. finished), which makes the loop load them.
        var ai = -1; var ax = 0.0; var ay = 0.0; var acx = 0.0; var acy = 0.0
        var aIter = maxIterations; var aRefX = 0.0; var aRefY = 0.0; var aRefIter = 0
        var bi = -1; var bx = 0.0; var by = 0.0; var bcx = 0.0; var bcy = 0.0
        var bIter = maxIterations; var bRefX = 0.0; var bRefY = 0.0; var bRefIter = 0

        while (true) {
            val ax2 = ax * ax
            val ay2 = ay * ay
            val bx2 = bx * bx
            val by2 = by * by
            if (ax2 + ay2 > BAILOUT || aIter >= maxIterations) {
                if (ai >= 0) iterations[ai] = aIter
                if (next >= count) {
                    // Nothing left to load, so move B into A, and finish it below.
                    ai = bi; ax = bx; ay = by; acx = bcx; acy = bcy
                    aIter = bIter; aRefX = bRefX; aRefY = bRefY; aRefIter = bRefIter
                    break
                }
                ai = next++
                ax = x0s[columns[ai]]; ay = y0
                acx = if (julia) jx else ax; acy = if (julia) jy else ay
                aIter = 0; aRefX = ax; aRefY = ay; aRefIter = 1
                continue
            }
            if (bx2 + by2 > BAILOUT || bIter >= maxIterations) {
                if (bi >= 0) iterations[bi] = bIter
                if (next >= count) break
                bi = next++
                bx = x0s[columns[bi]]; by = y0
                bcx = if (julia) jx else bx; bcy = if (julia) jy else by
                bIter = 0; bRefX = bx; bRefY = by; bRefIter = 1
                continue
            }

            val ax1 = nextX(ax, ay, ax2, ay2) + acx
            ay = nextY(ax, ay, ax2, ay2) + acy
            ax = ax1
            val bx1 = nextX(bx, by, bx2, by2) + bcx
            by = nextY(bx, by, bx2, by2) + bcy
            bx = bx1
            aIter++
            bIter++

            if (ax == aRefX && ay == aRefY) {
                aIter = maxIterations
            } else if (aIter == aRefIter) {
                aRefX = ax; aRefY = ay; aRefIter = aRefIter shl 1
            }
            if (bx == bRefX && by == bRefY) {
                bIter = maxIterations
            } else if (bIter == bRefIter) {
                bRefX = bx; bRefY = by; bRefIter = bRefIter shl 1
            }
        }

        // Finish the last remaining pixel by itself.
        while (aIter < maxIterations) {
            val ax2 = ax * ax
            val ay2 = ay * ay
            if (ax2 + ay2 > BAILOUT) break
            val ax1 = nextX(ax, ay, ax2, ay2) + acx
            ay = nextY(ax, ay, ax2, ay2) + acy
            ax = ax1
            aIter++
            if (ax == aRefX && ay == aRefY) {
                aIter = maxIterations
            } else if (aIter == aRefIter) {
                aRefX = ax; aRefY = ay; aRefIter = aRefIter shl 1
            }
        }
        if (ai >= 0) iterations[ai] = aIter
    }

    companion object {
        private val NUM_THREADS = Runtime.getRuntime().availableProcessors()
        private const val MAX_PALETTE_COLORS = 512
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
        const val BAILOUT = 4.0
    }
}

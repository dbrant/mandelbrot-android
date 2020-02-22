package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.graphics.*
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.math.sqrt

abstract class MandelbrotViewBase @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : View(context, attrs, defStyleAttr) {

    interface OnPointSelected {
        fun pointSelected(x: Double, y: Double)
    }

    interface OnCoordinatesChanged {
        fun newCoordinates(xmin: Double, xmax: Double, ymin: Double, ymax: Double)
    }

    private var isJulia = false
    private var paramIndex = 0
    private val currentThreads: MutableList<Thread> = ArrayList()
    @Volatile private var terminateThreads = false
    private var paint: Paint? = null
    private var viewportBitmap: Bitmap? = null
    private var viewportRect: Rect? = null
    private var showCrosshairs = false
    private var screenWidth = 0
    private var screenHeight = 0

    var xCenter = 0.0
    var yCenter = 0.0
    var xExtent = 0.0

    private var xmin = 0.0
    private var xmax = 0.0
    private var ymin = 0.0
    private var ymax = 0.0
    private var jx = 0.0
    private var jy = 0.0

    private var numIterations = DEFAULT_ITERATIONS

    fun getNumIterations(): Int {
        return numIterations
    }

    fun setNumIterations(iter: Int) {
        numIterations = iter
        if (numIterations < MIN_ITERATIONS) {
            numIterations = MIN_ITERATIONS
        }
        if (numIterations > MAX_ITERATIONS) {
            numIterations = MAX_ITERATIONS
        }
    }

    private val startCoarseness = 16
    private var endCoarseness = 1
    private var previousX = 0f
    private var previousY = 0f
    private val pinchStartPoint = PointF()
    private var pinchStartDistance = 0.0f
    private var touchMode = TOUCH_NONE
    private var displayDensity = 0f

    private var onPointSelected: OnPointSelected? = null
    fun setOnPointSelected(listener: OnPointSelected) {
        onPointSelected = listener
    }

    private var onCoordinatesChanged: OnCoordinatesChanged? = null
    fun setOnCoordinatesChanged(listener: OnCoordinatesChanged) {
        onCoordinatesChanged = listener
    }

    protected fun setup(isJulia: Boolean) {
        if (isInEditMode) {
            return
        }
        this.isJulia = isJulia
        paramIndex = if (isJulia) 1 else 0
        displayDensity = resources.displayMetrics.density
        paint = Paint()
        paint!!.style = Paint.Style.FILL
        paint!!.color = Color.WHITE
        paint!!.strokeWidth = 1.5f * displayDensity
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isInEditMode) {
            return
        }
        if (screenWidth != this.width || screenHeight != this.height) {
            screenWidth = this.width
            screenHeight = this.height
            if (screenWidth == 0 || screenHeight == 0) {
                return
            }
            terminateThreads()
            initMinMax()
            viewportBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            MandelNative.setBitmap(paramIndex, viewportBitmap)
            viewportRect = Rect(0, 0, viewportBitmap!!.width - 1, viewportBitmap!!.height - 1)
            render()
            return
        }
        if (screenWidth == 0 || screenHeight == 0) {
            return
        }
        MandelNative.updateBitmap(paramIndex, viewportBitmap)
        canvas.drawBitmap(viewportBitmap!!, viewportRect, viewportRect!!, paint)
        if (showCrosshairs) {
            canvas.drawLine(screenWidth / 2 - CROSSHAIR_WIDTH * displayDensity, screenHeight / 2.toFloat(), screenWidth / 2 + CROSSHAIR_WIDTH * displayDensity, screenHeight / 2.toFloat(), paint!!)
            canvas.drawLine(screenWidth / 2.toFloat(), screenHeight / 2 - CROSSHAIR_WIDTH * displayDensity, screenWidth / 2.toFloat(), screenHeight / 2 + CROSSHAIR_WIDTH * displayDensity, paint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                endCoarseness = startCoarseness
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (touchMode != TOUCH_ROTATE) {
                        previousX = event.x
                        previousY = event.y
                    }
                    touchMode = TOUCH_ROTATE
                    val x = event.x
                    val y = event.y
                    val dx = x - previousX
                    val dy = y - previousY
                    previousX = x
                    previousY = y
                    if (dx != 0f || dy != 0f) {
                        val amountX = dx.toDouble() / screenWidth.toDouble() * (xmax - xmin)
                        val amountY = dy.toDouble() / screenHeight.toDouble() * (ymax - ymin)
                        xmin -= amountX
                        xmax -= amountX
                        ymin -= amountY
                        ymax -= amountY
                    }
                } else if (event.pointerCount == 2) {
                    if (touchMode != TOUCH_ZOOM) {
                        pinchStartDistance = getPinchDistance(event)
                        getPinchCenterPoint(event, pinchStartPoint)
                        previousX = pinchStartPoint.x
                        previousY = pinchStartPoint.y
                        touchMode = TOUCH_ZOOM
                    } else {
                        val pt = PointF()
                        getPinchCenterPoint(event, pt)
                        previousX = pt.x
                        previousY = pt.y
                        val pinchScale = getPinchDistance(event) / pinchStartDistance
                        pinchStartDistance = getPinchDistance(event)
                        if (pinchScale > 0) {
                            val xCenter = xmin + (xmax - xmin) * (pt.x.toDouble() / screenWidth)
                            val yCenter = ymin + (ymax - ymin) * (pt.y.toDouble() / screenHeight)
                            xmin = xCenter - (xCenter - xmin) / pinchScale
                            xmax = xCenter + (xmax - xCenter) / pinchScale
                            ymin = yCenter - (yCenter - ymin) / pinchScale
                            ymax = yCenter + (ymax - yCenter) / pinchScale
                        }
                    }
                }
                if (onPointSelected != null) {
                    onPointSelected!!.pointSelected(xmin + event.x.toDouble() * (xmax - xmin) / screenWidth,
                            ymin + event.y.toDouble() * (ymax - ymin) / screenHeight)
                }
                endCoarseness = startCoarseness
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinchStartPoint.x = 0.0f
                pinchStartPoint.y = 0.0f
                touchMode = TOUCH_NONE
                endCoarseness = 1
                if (onPointSelected != null) {
                    onPointSelected!!.pointSelected(xmin + event.x.toDouble() * (xmax - xmin) / screenWidth,
                            ymin + event.y.toDouble() * (ymax - ymin) / screenHeight)
                }
            }
        }
        render()
        return true
    }

    fun requestCoordinates() {
        if (onCoordinatesChanged != null) {
            onCoordinatesChanged!!.newCoordinates(xmin, xmax, ymin, ymax)
        }
    }

    fun render() {
        terminateThreads()
        if (visibility != VISIBLE) {
            return
        }
        if (onCoordinatesChanged != null) {
            onCoordinatesChanged!!.newCoordinates(xmin, xmax, ymin, ymax)
        }
        xExtent = xmax - xmin
        xCenter = xmin + xExtent / 2.0
        yCenter = ymin + (ymax - ymin) / 2.0
        MandelNative.setParameters(paramIndex, numIterations, xmin, xmax, ymin, ymax,
                if (isJulia) 1 else 0, jx, jy, screenWidth, screenHeight)
        var t: Thread
        t = MandelThread(0, 0, screenWidth, screenHeight / 2, startCoarseness)
        t.start()
        currentThreads.add(t)
        t = MandelThread(0, screenHeight / 2, screenWidth, screenHeight / 2, startCoarseness)
        t.start()
        currentThreads.add(t)
    }

    fun terminateThreads() {
        try {
            MandelNative.signalTerminate(paramIndex)
            terminateThreads = true
            for (t in currentThreads) {
                if (t.isAlive) {
                    t.join(DateUtils.SECOND_IN_MILLIS)
                }
                if (t.isAlive) {
                    Log.w(TAG, "Thread is still alive after 1sec...")
                }
            }
            terminateThreads = false
            currentThreads.clear()
        } catch (ex: Exception) {
            Log.w(TAG, "Exception while terminating threads: " + ex.message)
        }
    }

    fun reset() {
        if (isJulia) {
            xCenter = DEFAULT_JULIA_X_CENTER
            yCenter = DEFAULT_JULIA_Y_CENTER
            xExtent = DEFAULT_JULIA_EXTENT
        } else {
            xCenter = DEFAULT_X_CENTER
            yCenter = DEFAULT_Y_CENTER
            xExtent = DEFAULT_X_EXTENT
        }
        numIterations = DEFAULT_ITERATIONS
        initMinMax()
        render()
    }

    fun setColorScheme(colors: IntArray) {
        MandelNative.setColorPalette(paramIndex, colors, colors.size)
    }

    fun setJuliaCoords(jx: Double, jy: Double) {
        this.jx = jx
        this.jy = jy
    }

    fun setCrosshairsEnabled(enabled: Boolean) {
        showCrosshairs = enabled
    }

    @Throws(IOException::class)
    fun savePicture(fileName: String) {
        savePicture(FileOutputStream(fileName))
    }

    @Throws(IOException::class)
    fun savePicture(stream: OutputStream) {
        viewportBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
    }

    private fun initMinMax() {
        val ratio = screenHeight.toDouble() / (if (screenWidth == 0) 1 else screenWidth).toDouble()
        xmin = xCenter - xExtent / 2.0
        xmax = xCenter + xExtent / 2.0
        ymin = yCenter - ratio * xExtent / 2.0
        ymax = yCenter + ratio * xExtent / 2.0
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y.toDouble()).toFloat()
    }

    private fun getPinchCenterPoint(event: MotionEvent, pt: PointF) {
        pt.x = (event.getX(0) + event.getX(1)) * 0.5f
        pt.y = (event.getY(0) + event.getY(1)) * 0.5f
    }

    private inner class MandelThread internal constructor(private val startX: Int, private val startY: Int, private val startWidth: Int, private val startHeight: Int, private val level: Int) : Thread() {
        override fun run() {
            var curLevel = level
            while (true) {
                MandelNative.drawFractal(paramIndex, startX, startY, startWidth, startHeight, curLevel, if (curLevel == level) 1 else 0)
                postInvalidate()
                if (terminateThreads) {
                    break
                }
                if (curLevel <= endCoarseness) {
                    break
                }
                curLevel /= 2
            }
        }
    }

    companion object {
        private const val TAG = "MandelbrotViewBase"
        const val DEFAULT_ITERATIONS = 128
        const val MAX_ITERATIONS = 2048
        const val MIN_ITERATIONS = 2
        const val DEFAULT_X_CENTER = -0.5
        const val DEFAULT_Y_CENTER = 0.0
        const val DEFAULT_X_EXTENT = 3.0
        const val DEFAULT_JULIA_X_CENTER = 0.0
        const val DEFAULT_JULIA_Y_CENTER = 0.0
        const val DEFAULT_JULIA_EXTENT = 3.0
        private const val TOUCH_NONE = 0
        private const val TOUCH_ROTATE = 1
        private const val TOUCH_ZOOM = 2
        private const val CROSSHAIR_WIDTH = 16f
    }
}
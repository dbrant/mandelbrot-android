package com.dmitrybrant.android.mandelbrot;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.graphics.PointF;
import androidx.annotation.NonNull;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;

public abstract class MandelbrotViewBase extends View {
    private static final String TAG = "MandelbrotViewBase";
    public static final int DEFAULT_ITERATIONS = 128;
    public static final int MAX_ITERATIONS = 2048;
    public static final int MIN_ITERATIONS = 2;
    public static final double DEFAULT_X_CENTER = -0.5;
    public static final double DEFAULT_Y_CENTER = 0.0;
    public static final double DEFAULT_X_EXTENT = 3.0;
    public static final double DEFAULT_JULIA_X_CENTER = 0.0;
    public static final double DEFAULT_JULIA_Y_CENTER = 0.0;
    public static final double DEFAULT_JULIA_EXTENT = 3.0;

    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_ROTATE = 1;
    private static final int TOUCH_ZOOM = 2;

    private static final float CROSSHAIR_WIDTH = 16f;

    private boolean isJulia;
    private int paramIndex;

    private List<Thread> currentThreads = new ArrayList<>();
    private volatile boolean terminateThreads;

    private Paint paint;
    private Bitmap viewportBitmap;
    private Rect viewportRect;
    private boolean showCrosshairs;

    private int screenWidth;
    private int screenHeight;

    private double xcenter;
    public double getXCenter() { return xcenter; }
    public void setXCenter(double xcenter) { this.xcenter = xcenter; }

    private double ycenter;
    public double getYCenter() { return ycenter; }
    public void setYCenter(double ycenter) { this.ycenter = ycenter; }

    private double xextent;
    public double getXExtent() { return xextent; }
    public void setXExtent(double xextent) { this.xextent = xextent; }

    private double xmin;
    private double xmax;
    private double ymin;
    private double ymax;
    private double jx;
    private double jy;

    private int numIterations = DEFAULT_ITERATIONS;
    public int getNumIterations() { return numIterations; }
    public void setNumIterations(int iter) {
        numIterations = iter;
        if (numIterations < MIN_ITERATIONS) {
            numIterations = MIN_ITERATIONS;
        }
        if (numIterations > MAX_ITERATIONS) {
            numIterations = MAX_ITERATIONS;
        }
    }

    private int startCoarseness = 16;
    private int endCoarseness = 1;

    private float previousX;
    private float previousY;
    private PointF pinchStartPoint = new PointF();
    private float pinchStartDistance = 0.0f;
    private int touchMode = TOUCH_NONE;

    private float displayDensity;

    public interface OnPointSelected {
        void pointSelected(double x, double y);
    }
    private OnPointSelected onPointSelected;
    public void setOnPointSelected(OnPointSelected listener) {
        onPointSelected = listener;
    }

    public interface OnCoordinatesChanged {
        void newCoordinates(double xmin, double xmax, double ymin, double ymax);
    }
    private OnCoordinatesChanged onCoordinatesChanged;
    public void setOnCoordinatesChanged(OnCoordinatesChanged listener) {
        onCoordinatesChanged = listener;
    }

    public MandelbrotViewBase(Context context) {
        super(context);
    }

    public MandelbrotViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MandelbrotViewBase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void init(boolean isJulia) {
        if (isInEditMode()) {
            return;
        }
        this.isJulia = isJulia;
        this.paramIndex = isJulia ? 1 : 0;
        displayDensity = getResources().getDisplayMetrics().density;

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(1.5f * displayDensity);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isInEditMode()) {
            return;
        }
        if ((screenWidth != this.getWidth()) || (screenHeight != this.getHeight())) {
            screenWidth = this.getWidth();
            screenHeight = this.getHeight();
            if ((screenWidth == 0) || (screenHeight == 0)) {
                return;
            }

            terminateThreads();
            initMinMax();

            viewportBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            mandelnative.SetBitmap(paramIndex, viewportBitmap);
            viewportRect = new Rect(0, 0, viewportBitmap.getWidth() - 1, viewportBitmap.getHeight() - 1);

            render();
            return;
        }
        if ((screenWidth == 0) || (screenHeight == 0)) {
            return;
        }

        mandelnative.UpdateBitmap(paramIndex, viewportBitmap);
        canvas.drawBitmap(viewportBitmap, viewportRect, viewportRect, paint);

        if (showCrosshairs) {
            canvas.drawLine(screenWidth / 2 - CROSSHAIR_WIDTH * displayDensity, screenHeight / 2, screenWidth / 2 + CROSSHAIR_WIDTH * displayDensity, screenHeight / 2, paint);
            canvas.drawLine(screenWidth / 2, screenHeight / 2 - CROSSHAIR_WIDTH * displayDensity, screenWidth / 2, screenHeight / 2 + CROSSHAIR_WIDTH * displayDensity, paint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event){
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                previousX = event.getX();
                previousY = event.getY();
                endCoarseness = startCoarseness;
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1) {
                    if (touchMode != TOUCH_ROTATE) {
                        previousX = event.getX();
                        previousY = event.getY();
                    }
                    touchMode = TOUCH_ROTATE;
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - previousX;
                    float dy = y - previousY;
                    previousX = x;
                    previousY = y;

                    if((dx != 0) || (dy != 0)){
                        double amountX = ((double)dx / (double)screenWidth) * (xmax - xmin);
                        double amountY = ((double)dy / (double)screenHeight) * (ymax - ymin);
                        xmin -= amountX; xmax -= amountX;
                        ymin -= amountY; ymax -= amountY;
                    }

                } else if (event.getPointerCount() == 2) {
                    if (touchMode != TOUCH_ZOOM) {
                        pinchStartDistance = getPinchDistance(event);
                        getPinchCenterPoint(event, pinchStartPoint);
                        previousX = pinchStartPoint.x;
                        previousY = pinchStartPoint.y;
                        touchMode = TOUCH_ZOOM;
                    } else {
                        PointF pt = new PointF();
                        getPinchCenterPoint(event, pt);
                        previousX = pt.x;
                        previousY = pt.y;
                        float pinchScale = getPinchDistance(event) / pinchStartDistance;
                        pinchStartDistance = getPinchDistance(event);

                        if (pinchScale > 0) {
                            double xCenter = xmin + ((xmax - xmin) * ((double) pt.x / screenWidth));
                            double yCenter = ymin + ((ymax - ymin) * ((double) pt.y / screenHeight));
                            xmin = xCenter - (xCenter - xmin) / pinchScale;
                            xmax = xCenter + (xmax - xCenter) / pinchScale;
                            ymin = yCenter - (yCenter - ymin) / pinchScale;
                            ymax = yCenter + (ymax - yCenter) / pinchScale;
                        }
                    }
                }
                if (onPointSelected != null) {
                    onPointSelected.pointSelected(xmin + ((double)event.getX() * (xmax - xmin) / screenWidth),
                            ymin + ((double)event.getY() * (ymax - ymin) / screenHeight));
                }
                endCoarseness = startCoarseness;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pinchStartPoint.x = 0.0f;
                pinchStartPoint.y = 0.0f;
                touchMode = TOUCH_NONE;
                endCoarseness = 1;
                if (onPointSelected != null) {
                    onPointSelected.pointSelected(xmin + ((double)event.getX() * (xmax - xmin) / screenWidth),
                            ymin + ((double)event.getY() * (ymax - ymin) / screenHeight));
                }
                break;
        }

        render();
        return true;
    }

    public void requestCoordinates() {
        if (onCoordinatesChanged != null) {
            onCoordinatesChanged.newCoordinates(xmin, xmax, ymin, ymax);
        }
    }

    public void render() {
        terminateThreads();
        if (getVisibility() != View.VISIBLE) {
            return;
        }
        if (onCoordinatesChanged != null) {
            onCoordinatesChanged.newCoordinates(xmin, xmax, ymin, ymax);
        }

        xextent = xmax - xmin;
        xcenter = xmin + xextent / 2.0;
        ycenter = ymin + (ymax - ymin) / 2.0;

        mandelnative.SetParameters(paramIndex, numIterations, xmin, xmax, ymin, ymax,
                isJulia ? 1 : 0, jx, jy, screenWidth, screenHeight);
        Thread t;

        t = new MandelThread(0, 0, screenWidth, screenHeight / 2, startCoarseness);
        t.start();
        currentThreads.add(t);
        t = new MandelThread(0, screenHeight / 2, screenWidth, screenHeight / 2, startCoarseness);
        t.start();
        currentThreads.add(t);
    }

    public void terminateThreads() {
        try {
            mandelnative.SignalTerminate(paramIndex);
            terminateThreads = true;

            for(Thread t : currentThreads){
                if (t.isAlive()) {
                    t.join(DateUtils.SECOND_IN_MILLIS);
                }
                if (t.isAlive()) {
                    Log.w(TAG, "Thread is still alive after 1sec...");
                }
            }

            terminateThreads = false;
            currentThreads.clear();
        } catch(Exception ex) {
            Log.w(TAG, "Exception while terminating threads: " + ex.getMessage());
        }
    }

    public void reset() {
        if (isJulia) {
            xcenter = DEFAULT_JULIA_X_CENTER;
            ycenter = DEFAULT_JULIA_Y_CENTER;
            xextent = DEFAULT_JULIA_EXTENT;
        } else {
            xcenter = DEFAULT_X_CENTER;
            ycenter = DEFAULT_Y_CENTER;
            xextent = DEFAULT_X_EXTENT;
        }
        numIterations = DEFAULT_ITERATIONS;
        initMinMax();
        render();
    }

    public void setColorScheme(int[] colors) {
        mandelnative.SetColorPalette(paramIndex, colors, colors.length);
    }

    public void setJuliaCoords(double jx, double jy) {
        this.jx = jx;
        this.jy = jy;
    }

    public void setCrosshairsEnabled(boolean enabled) {
        showCrosshairs = enabled;
    }

    public void savePicture(@NonNull String fileName) throws IOException
    {
        savePicture(new FileOutputStream(fileName));
    }

    public void savePicture(@NonNull OutputStream stream) throws IOException
    {
        viewportBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        stream.flush();
        stream.close();
    }

    private void initMinMax() {
        double ratio = (double)screenHeight / (double)(screenWidth == 0 ? 1 : screenWidth);
        xmin = xcenter - xextent / 2.0;
        xmax = xcenter + xextent / 2.0;
        ymin = ycenter - ratio * xextent / 2.0;
        ymax = ycenter + ratio * xextent / 2.0;
    }

    private float getPinchDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void getPinchCenterPoint(MotionEvent event, PointF pt) {
        pt.x = (event.getX(0) + event.getX(1)) * 0.5f;
        pt.y = (event.getY(0) + event.getY(1)) * 0.5f;
    }

    private class MandelThread extends Thread
    {
        MandelThread (int x, int y, int width, int height, int level) {
            startX = x;
            startY = y;
            startWidth = width;
            startHeight = height;
            this.level = level;
        }

        private int startX, startY;
        private int startWidth, startHeight;
        private int level;

        @Override
        public void run(){
            int curLevel = level;
            while (true) {
                mandelnative.DrawFractal(paramIndex, startX, startY, startWidth, startHeight, curLevel, curLevel == level ? 1 : 0);
                MandelbrotViewBase.this.postInvalidate();
                if (terminateThreads) {
                    break;
                }
                if (curLevel <= endCoarseness) {
                    break;
                }
                curLevel /= 2;
            }
        }    
    }
}

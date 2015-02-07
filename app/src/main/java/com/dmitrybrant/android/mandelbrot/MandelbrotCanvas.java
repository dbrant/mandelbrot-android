package com.dmitrybrant.android.mandelbrot;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class MandelbrotCanvas extends View {
    private static final String TAG = "MandelbrotCanvas";
    public static final int DEFAULT_ITERATIONS = 128;
    public static final int MAX_ITERATIONS = 2048;
    public static final int MIN_ITERATIONS = 2;
    public static final double DEFAULT_X_CENTER = -0.5;
    public static final double DEFAULT_Y_CENTER = 0.0;
    public static final double DEFAULT_X_EXTENT = 3.0;

    private MandelbrotActivity parentActivity;

    private List<Thread> currentThreads = new ArrayList<>();
    private volatile boolean terminateThreads = false;

    private Paint paint;
    private Bitmap theBitmap;
    private Rect theRect;

    private int screenWidth = 0;
    private int screenHeight = 0;

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
        if (numIterations < MandelbrotCanvas.MIN_ITERATIONS) {
            numIterations = MandelbrotCanvas.MIN_ITERATIONS;
        }
        if (numIterations > MandelbrotCanvas.MAX_ITERATIONS) {
            numIterations = MandelbrotCanvas.MAX_ITERATIONS;
        }
    }

    public int currentColorScheme = 0;
    public boolean juliaMode = false;

    public int startCoarseness = 16;
    public int endCoarseness = 1;

    private int touchStartX;
    private int touchStartY;
    private boolean zooming = false;
    private ScaleGestureDetector gesture;

    public MandelbrotCanvas(Context context) {
        super(context);
        parentActivity = (MandelbrotActivity)context;

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);

        setBackgroundColor(Color.BLACK);
        setFocusable(false);
        gesture = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if ((screenWidth != this.getWidth()) || (screenHeight != this.getHeight())) {
            screenWidth = this.getWidth();
            screenHeight = this.getHeight();
            if ((screenWidth == 0) || (screenHeight == 0)) {
                return;
            }

            terminateThreads();
            initMinMax();

            theBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            mandelnative.SetBitmap(theBitmap);
            theRect = new Rect(0, 0, theBitmap.getWidth() - 1, theBitmap.getHeight() - 1);

            RenderMandelbrot();
            return;
        }
        if ((screenWidth == 0) || (screenHeight == 0)) {
            return;
        }

        mandelnative.UpdateBitmap(theBitmap);
        canvas.drawBitmap(theBitmap, theRect, theRect, paint);

        parentActivity.txtIterations.setText("Iterations: " + Integer.toString(numIterations));

        String str = "Real: " + Double.toString(xmin) + " to " + Double.toString(xmax) + "\n";
        str += "Imag: " + Double.toString(ymin) + " to " + Double.toString(ymax);
        if (juliaMode) {
            str += "\nJulia: " + Double.toString(jx) + ", " + Double.toString(jy);
        }
        parentActivity.txtInfo.setText(str);
    }

    public void terminateThreads() {
        try {
            mandelnative.SignalTerminate();
            terminateThreads = true;

            for(Thread t : currentThreads){
                if (t.isAlive()) {
                    t.join(DateUtils.SECOND_IN_MILLIS);
                }
                if (t.isAlive()) {
                    Log.e(TAG, "Thread is still alive after 1sec...");
                }
            }

            terminateThreads = false;
            currentThreads.clear();
        } catch(Exception ex) {
            Log.w(TAG, "Exception while terminating threads: " + ex.getMessage());
        }
    }

    void initMinMax() {
        double ratio = (double)screenHeight / (double)screenWidth;

        xmin = xcenter - xextent / 2.0;
        xmax = xcenter + xextent / 2.0;
        ymin = ycenter - ratio * xextent / 2.0;
        ymax = ycenter + ratio * xextent / 2.0;

        jx = xcenter;
        jy = ycenter;

        setColorScheme();
    }

    void Reset() {
        xcenter = DEFAULT_X_CENTER;
        ycenter = DEFAULT_Y_CENTER;
        xextent = DEFAULT_X_EXTENT;
        numIterations = DEFAULT_ITERATIONS;
        juliaMode = false;
        currentColorScheme = 0;
        initMinMax();
        RenderMandelbrot();
    }

    void setColorScheme() {
        int[] colors = new int[2];
        currentColorScheme = currentColorScheme % 5;

        if(currentColorScheme == 0){
            colors = new int[320];
            int col=0, i;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | ((i * 4) << 8) | (((63 - i) * 4) << 16);
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | 0xff00 | (i * 4);
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | (((63 - i) * 4) << 8) | 0xff;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | ((i * 4) << 16) | 0xff;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | 0xff0000 | ((63 - i) * 4);
        }
        if(currentColorScheme == 1){
            colors = new int[320];
            int col=0, i;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | ((i * 4) << 8) | (((63 - i) * 4) << 16);
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | 0xff00 | (i * 4);
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | (((63 - i) * 4) << 8) | 0xff;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | ((i * 4) << 16) | 0xff;
            for(i=0; i<64; i++)
                colors[col++] = 0xff000000 | 0xff0000 | ((63 - i) * 4);
            
            for (i=0; i<colors.length; i++) {
                colors[i] = 0xff000000 | (~colors[i]);
            }
        }
        if (currentColorScheme == 2) {
            colors = new int[256];
            int col=0, i;
            for(i=0; i<128; i++)
                colors[col++] = 0xff000000 | ((i*2)<<16) | ((i*2)<<8) | ((i*2));
            for(i=0; i<128; i++)
                colors[col++] = 0xff000000 | (((127-i)*2)<<16) | (((127-i)*2)<<8) | (((127-i)*2));
        }
        if (currentColorScheme == 3) {
            colors = new int[256];
            int col=0, i;
            for(i=0; i<128; i++)
                colors[col++] = 0xff000000 | (((127-i)*2)<<16) | (((127-i)*2)<<8) | (((127-i)*2));
            for(i=0; i<128; i++)
                colors[col++] = 0xff000000 | ((i*2)<<16) | ((i*2)<<8) | ((i*2));
        }
        if (currentColorScheme == 4) {
            colors = new int[2];
            colors[0] = 0xff000000;
            colors[1] = 0xffffffff;
        }
        mandelnative.SetColorPalette(colors, colors.length);
    }

    void SavePicture(String fileName) throws IOException
    {
        FileOutputStream fs = new FileOutputStream(fileName);
        theBitmap.compress(Bitmap.CompressFormat.PNG, 100, fs);
        fs.flush();
        fs.close();
    }

    private class MandelThread extends Thread
    {
        public MandelThread (int x, int y, int width, int height, int level) {
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
                if (juliaMode) {
                    mandelnative.JuliaPixels(startX, startY, startWidth, startHeight, curLevel, curLevel == level ? 1 : 0);
                } else {
                    mandelnative.MandelbrotPixels(startX, startY, startWidth, startHeight, curLevel, curLevel == level ? 1 : 0);
                }
                MandelbrotCanvas.this.postInvalidate();
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

    protected void RenderMandelbrot() {
        terminateThreads();
        xextent = xmax - xmin;
        xcenter = xmin + xextent / 2.0;
        ycenter = ymin + (ymax - ymin) / 2.0;

        mandelnative.SetParameters(numIterations, xmin, xmax, ymin, ymax, jx, jy, screenWidth, screenHeight, startCoarseness);
        Thread t;

        if (juliaMode) {
            int juliaSize = screenWidth > screenHeight ? 2*screenHeight/3 : 2*screenWidth/3;
            t = new MandelThread(0, screenHeight - juliaSize - 1, juliaSize, juliaSize, startCoarseness);
            t.start();
            currentThreads.add(t);
        } else {
            t = new MandelThread(0, 0, screenWidth, screenHeight / 2, startCoarseness);
            t.start();
            currentThreads.add(t);
            t = new MandelThread(0, screenHeight / 2, screenWidth, screenHeight / 2, startCoarseness);
            t.start();
            currentThreads.add(t);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            double s = detector.getScaleFactor();
            if (s != 1.0 && s > 0.0 && !juliaMode) {
                zooming = true;
                endCoarseness = startCoarseness;

                double xoff = xmin + ((double)detector.getFocusX() * (xmax - xmin) / screenWidth);
                double yoff = ymin + ((double)detector.getFocusY() * (ymax - ymin) / screenHeight);

                xmax = xoff + ((xmax - xoff) / s);
                xmin = xoff + ((xmin - xoff) / s);
                ymax = yoff + ((ymax - yoff) / s);
                ymin = yoff + ((ymin - yoff) / s);

                RenderMandelbrot();
            }
            return true;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event){
        gesture.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartX = (int)event.getX();
            touchStartY = (int)event.getY();
            zooming = false;
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            zooming = false;
            endCoarseness = 1;
            if(juliaMode){
                jx = xmin + ((double)event.getX() * (xmax - xmin) / screenWidth);
                jy = ymin + ((double)event.getY() * (ymax - ymin) / screenHeight);
            }
            RenderMandelbrot();
            return true;
        } else if(event.getAction() == MotionEvent.ACTION_MOVE){
            if(juliaMode){
                jx = xmin + ((double)event.getX() * (xmax - xmin) / screenWidth);
                jy = ymin + ((double)event.getY() * (ymax - ymin) / screenHeight);
                RenderMandelbrot();
            }
            else{
                if(!zooming){
                    endCoarseness = startCoarseness;
                    
                    int dx = (int)event.getX() - touchStartX;
                    int dy = (int)event.getY() - touchStartY;
                    if((dx != 0) || (dy != 0)){
                        double amountX = ((double)dx / (double)screenWidth) * (xmax - xmin);
                        double amountY = ((double)dy / (double)screenHeight) * (ymax - ymin);
                        xmin -= amountX; xmax -= amountX;
                        ymin -= amountY; ymax -= amountY;            
                        RenderMandelbrot();
                    }
                    touchStartX = (int)event.getX();
                    touchStartY = (int)event.getY();
                }
            }
            return true;
        }
        return false;
    }

}

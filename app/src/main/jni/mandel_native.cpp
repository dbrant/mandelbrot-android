#include <stdlib.h>
#include <android/log.h>

#include "mandel_native.h"

static int numIterations = 128;
static double xmin, xmax, ymin, ymax;
static int screenWidth = 0, screenHeight = 0;
uint32_t colorPalette[MAX_PALETTE_COLORS];
uint32_t colorPaletteJulia[MAX_PALETTE_COLORS];
int numPaletteColors = 2;

uint32_t* pixelBuffer = NULL;
int pixelBufferLen = 0;
static double* x0array = NULL;
static double jx, jy;
volatile static bool terminateJob = false;

#ifdef __cplusplus
extern "C" {
#endif


void setParameters(int iterations, double xMin, double xMax, double yMin, double yMax, double _jx, double _jy, int screenWid, int screenHgt, int coarseness){
	//LOGD("setting parameters...");
	numIterations = iterations;
	xmin = xMin;
	xmax = xMax;
	ymin = yMin;
	ymax = yMax;
	screenWidth = screenWid;
	screenHeight = screenHgt;
	
	jx = _jx;
	jy = _jy;
	
	if(x0array != NULL)
		free(x0array);
	x0array = (double*)malloc(sizeof(double)*screenWidth*2);
	
	//make a default color palette...
	/*
	int col = 0, i;
	numPaletteColors = 320;
	for(i=0; i<64; i++)
		colorPalette[col++] = 0xff000000 | ((i * 4) << 8) | (((63 - i) * 4) << 16);
	for(i=0; i<64; i++)
		colorPalette[col++] = 0xff000000 | 0xff00 | (i * 4);
	for(i=0; i<64; i++)
		colorPalette[col++] = 0xff000000 | (((63 - i) * 4) << 8) | 0xff;
	for(i=0; i<64; i++)
		colorPalette[col++] = 0xff000000 | ((i * 4) << 16) | 0xff;
	for(i=0; i<64; i++)
		colorPalette[col++] = 0xff000000 | 0xff0000 | ((63 - i) * 4);
	for(i=0; i<numPaletteColors; i++)
		colorPaletteJulia[i] = colorPalette[(numPaletteColors/2 + numPaletteColors - i - 1) % numPaletteColors];
	*/
	
	terminateJob = false;
}

void releaseParameters(){
	//LOGD("releasing parameters...");
	if(x0array != NULL){
		free(x0array);
		x0array = NULL;
	}
}

void mandelbrotPixels(int startX, int startY, int startWidth, int startHeight, int level, int doall){
	int maxY = startY + startHeight;
	int maxX = startX + startWidth;
	double x, y, x0, y0, tempx, tempy;
	double xsq, ysq;
	double xscale = (xmax - xmin) / screenWidth;
	double yscale = (ymax - ymin) / screenHeight;
	int iteration, color;
	int iterScale = 1;
	int px, py, xindex=0, yindex=0, yptr, yptr2;
	
	//LOGD("Drawing thread: %d, %d, %d, %d, %d, %d, %d", screenWidth, screenHeight, startX, startY, startWidth, startHeight, level);
	if(level < 1)
		return;
	
	if(pixelBuffer == NULL){
		LOGE("Pixel buffer is null! Cannot continue.");
		return;
	}
	
	for(px = startX; px < maxX; px++)
		x0array[px] = xmin + (double)px * xscale;
	
	if(numIterations < numPaletteColors)
		iterScale = numPaletteColors / numIterations;
	
	for(py = startY; py < maxY; py += level, yindex++){
		y0 = ymin + (double)py * yscale;
		yptr = py*screenWidth;
		
		for(px = startX; px < maxX; px += level, xindex++){
			if(!doall){
				if((yindex % 2 == 0) && (xindex % 2 == 0))
					continue;
			}
			
			x = y = xsq = ysq = 0.0;
			iteration = 0;
			x0 = x0array[px];
			
			while(xsq + ysq < 4.0){
				y = x * y;
				y += y;
				y += y0;
				x = xsq - ysq + x0;
				xsq = x*x;
				ysq = y*y;
				if(++iteration > numIterations)
					break;
			}
			
			if(iteration >= numIterations)
				color = 0;
			else
				color = colorPalette[(iteration * iterScale) % numPaletteColors];
			
			if(level > 1){
				yptr2 = yptr;
				for(int iy=py+level-1; iy>=py; iy--){
					if(iy >= maxY)
						continue;
					for(int ix=px+level-1; ix>=px; ix--)
						pixelBuffer[yptr2 + ix] = color;
					yptr2 += screenWidth;
				}
			}else{
				pixelBuffer[yptr + px] = color;
			}
		}
		
		if(terminateJob){
			//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Terminating drawing...");
			break;
		}
	}
}


#define JULIA_XMIN		(-1.5)
#define JULIA_XMAX		(1.5)
#define JULIA_YMIN		(-1.5)
#define JULIA_YMAX		(1.5)

void juliaPixels(int startX, int startY, int startWidth, int startHeight, int level, int doall){
	int maxY = startY + startHeight;
	int maxX = startX + startWidth;
	double jxmin = JULIA_XMIN, jxmax = JULIA_XMAX;
	double jymin = JULIA_YMIN, jymax = JULIA_YMAX;
	double x, y, x0, y0, tempx, tempy;
	double xsq, ysq;
	int iteration, color;
	int iterScale = 1;
	int px, py, xindex=0, yindex=0, yptr, yptr2;
	
	/*
	double screenratio = (double)startWidth / (double)startHeight;
	if(screenratio > 1.0){
		double xcenter = jxmin + (jxmax - jxmin) / 2.0;
		jxmin = xcenter - screenratio * (jymax - jymin) / 2.0;
		jxmax = xcenter + screenratio * (jymax - jymin) / 2.0;
	}else{
		screenratio = (double)startHeight / (double)startWidth;
		double ycenter = jymin + (jymax - jymin) / 2.0;
		jymin = ycenter - screenratio * (jxmax - jxmin) / 2.0;
		jymax = ycenter + screenratio * (jxmax - jxmin) / 2.0;
	}
	*/
	
	double xscale = (jxmax - jxmin) / (startWidth);
	double yscale = (jymax - jymin) / (startHeight);
	
	//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Rendering Julia: %f, %f", jx, jy);
	if(level < 1)
		return;
	
	if(pixelBuffer == NULL){
		LOGE("Pixel buffer is null! Cannot continue.");
		return;
	}
	
	for(px = startX; px < maxX; px++)
		x0array[px] = jxmin + (double)(px - startX) * xscale;
	
	if(numIterations < numPaletteColors)
		iterScale = numPaletteColors / numIterations;
	
	for(py = startY; py < maxY; py += level, yindex++){
		y0 = jymin + (double)(py - startY) * yscale;
		yptr = py*screenWidth;
		
		for(px = startX; px < maxX; px += level, xindex++){
			if(!doall){
				if((yindex % 2 == 0) && (xindex % 2 == 0))
					continue;
			}
			
			x = x0array[px];
			y = y0;
			iteration = 0;
			
			while(iteration++ < numIterations){
				xsq = x*x;
				ysq = y*y;
				if(xsq + ysq > 4.0)
					break;
				
				tempx = xsq - ysq + jx;
				tempy = 2*x*y + jy;
				x = tempx;
				y = tempy;
			}
			
			if(iteration >= numIterations)
				color = 0;
			else
				color = colorPaletteJulia[(iteration * iterScale) % numPaletteColors];
			
			if(level > 1){
				yptr2 = yptr;
				for(int iy=py+level-1; iy>=py; iy--){
					if(iy >= maxY)
						continue;
					for(int ix=px+level-1; ix>=px; ix--)
						pixelBuffer[yptr2 + ix] = color;
					yptr2 += screenWidth;
				}
			}else{
				pixelBuffer[yptr + px] = color;
			}
		}
		
		if(terminateJob){
			//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Terminating drawing...");
			break;
		}
	}
}


void signalTerminate(){
	terminateJob = true;
}


#ifdef __cplusplus
}
#endif

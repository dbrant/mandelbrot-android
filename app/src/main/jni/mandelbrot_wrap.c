#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <android/bitmap.h>

#include "mandel_native.h"

#define JULIA_XMIN		(-1.5)
#define JULIA_XMAX		(1.5)
#define JULIA_YMIN		(-1.5)
#define JULIA_YMAX		(1.5)

static fractalParams params[2];
volatile static int terminateJob = 0;

#ifdef __cplusplus
extern "C" {
#endif


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_InitParams(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	params[paramIndex].pixelBuffer = NULL;
	params[paramIndex].pixelBufferLen = 0;
	params[paramIndex].x0array = NULL;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject bitmap) {
	AndroidBitmapInfo bitmapInfo;
	int ret;
	ret = AndroidBitmap_getInfo(jenv, bitmap, &bitmapInfo);
	if(ret < 0) {
		LOGE("AndroidBitmap_getInfo() failed: error %d", ret);
		return;
    }
	LOGD("Setting bitmap: %d x %d, stride: %d", bitmapInfo.width, bitmapInfo.height, bitmapInfo.stride);
	if (bitmapInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
		LOGE("Incorrect bitmap format...");
		return;
    }

	if(params[paramIndex].pixelBuffer != NULL){
		LOGD("freeing buffer...");
		free(params[paramIndex].pixelBuffer);
	}
	
	params[paramIndex].pixelBufferLen = sizeof(uint32_t) * (bitmapInfo.width + 32) * (bitmapInfo.height + 32);
	LOGD("Creating buffer: %d bytes", params[paramIndex].pixelBufferLen);
	params[paramIndex].pixelBuffer = malloc(params[paramIndex].pixelBufferLen);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_ReleaseBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	if(params[paramIndex].pixelBuffer != NULL){
		LOGD("freeing buffer...");
		free(params[paramIndex].pixelBuffer);
	}
	params[paramIndex].pixelBuffer = NULL;
	params[paramIndex].pixelBufferLen = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_UpdateBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject bitmap) {
	void* bitmapPixels;
	int ret;
	AndroidBitmapInfo bitmapInfo;
	ret = AndroidBitmap_getInfo(jenv, bitmap, &bitmapInfo);
	if(ret < 0) {
		LOGE("AndroidBitmap_getInfo() failed: error %d", ret);
		return;
    }
	if ((ret = AndroidBitmap_lockPixels(jenv, bitmap, &bitmapPixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed: error %d", ret);
		return;
    }
	if(params[paramIndex].pixelBuffer == NULL){
		LOGE("Pixel buffer is null! Cannot update.");
		return;
	}
	memcpy(bitmapPixels, params[paramIndex].pixelBuffer, sizeof(uint32_t) * (bitmapInfo.width) * (bitmapInfo.height));
	AndroidBitmap_unlockPixels(jenv, bitmap);
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetColorPalette(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject colorArray, jint numColors) {
	jint *c_array;
    int i = 0;
	c_array = (*jenv)->GetIntArrayElements(jenv, colorArray, NULL);
	if (c_array == NULL) {
		LOGE("Color palette array is null...");
		return;
	}
	params[paramIndex].numPaletteColors = numColors;
	for (i=0; i<numColors; i++) {
		params[paramIndex].colorPalette[i] = c_array[i];
	}
	(*jenv)->ReleaseIntArrayElements(jenv, colorArray, c_array, 0);
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetParameters(JNIEnv *jenv, jclass jcls, jint paramIndex,
                       jint numIterations, jdouble xMin, jdouble xMax, jdouble yMin, jdouble yMax,
					   jint isJulia, jdouble juliaX, jdouble juliaY, jint viewWidth, jint viewHeight) {
	LOGD("setting parameters...");
	params[paramIndex].numIterations = numIterations;
	params[paramIndex].xmin = xMin;
	params[paramIndex].xmax = xMax;
	params[paramIndex].ymin = yMin;
	params[paramIndex].ymax = yMax;
	params[paramIndex].viewWidth = viewWidth;
	params[paramIndex].viewHeight = viewHeight;
	params[paramIndex].isJulia = isJulia;
	params[paramIndex].juliaX = juliaX;
	params[paramIndex].juliaY = juliaY;
	if (params[paramIndex].x0array != NULL) {
		free(params[paramIndex].x0array);
	}
	params[paramIndex].x0array = (double*)malloc(sizeof(double) * params[paramIndex].viewWidth * 2);
	terminateJob = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_ReleaseParameters(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	if(params[paramIndex].x0array != NULL){
		free(params[paramIndex].x0array);
		params[paramIndex].x0array = NULL;
	}
}



void drawMandelbrot(int paramIndex, int startX, int startY, int startWidth, int startHeight, int level, int doall){
	int maxY = startY + startHeight;
	int maxX = startX + startWidth;
	double x, y, x0, y0, tempx, tempy;
	double xsq, ysq;
	int ix, iy;
	double xscale = (params[paramIndex].xmax - params[paramIndex].xmin) / params[paramIndex].viewWidth;
	double yscale = (params[paramIndex].ymax - params[paramIndex].ymin) / params[paramIndex].viewHeight;
	int iteration, color;
	int iterScale = 1;
	int px, py, xindex=0, yindex=0, yptr, yptr2;
	int numIterations = params[paramIndex].numIterations;
	int numPaletteColors = params[paramIndex].numPaletteColors;

	//LOGD("Drawing thread: %d, %d, %d, %d, %d, %d, %d", screenWidth, screenHeight, startX, startY, startWidth, startHeight, level);
	if (level < 1) {
		return;
	}

	if(params[paramIndex].pixelBuffer == NULL){
		LOGE("Pixel buffer is null! Cannot continue.");
		return;
	}

	for (px = startX; px < maxX; px++) {
		params[paramIndex].x0array[px] = params[paramIndex].xmin + (double)px * xscale;
	}

	if (numIterations < numPaletteColors) {
		iterScale = numPaletteColors / numIterations;
	}

	for (py = startY; py < maxY; py += level, yindex++){
		y0 = params[paramIndex].ymin + (double)py * yscale;
		yptr = py * params[paramIndex].viewWidth;
		
		for (px = startX; px < maxX; px += level, xindex++){
			if (!doall) {
				if ((yindex % 2 == 0) && (xindex % 2 == 0)) {
					continue;
				}
			}
			
			x = y = xsq = ysq = 0.0;
			iteration = 0;
			x0 = params[paramIndex].x0array[px];
			
			while (xsq + ysq < 4.0) {
				y = x * y;
				y += y;
				y += y0;
				x = xsq - ysq + x0;
				xsq = x*x;
				ysq = y*y;
				if (++iteration > numIterations) {
					break;
				}
			}
			
			if (iteration >= numIterations) {
				color = 0;
			} else {
				color = params[paramIndex].colorPalette[(iteration * iterScale) % numPaletteColors];
			}

			if (level > 1) {
				yptr2 = yptr;
				for (iy = py+level-1; iy >= py; iy--) {
					if (iy >= maxY) {
						continue;
					}
					for (ix = px+level-1; ix >= px; ix--) {
						params[paramIndex].pixelBuffer[yptr2 + ix] = color;
					}
					yptr2 += params[paramIndex].viewWidth;
				}
			} else {
				params[paramIndex].pixelBuffer[yptr + px] = color;
			}
		}
		if (terminateJob) {
			//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Terminating drawing...");
			break;
		}
	}
}


void drawJulia(int paramIndex, int startX, int startY, int startWidth, int startHeight, int level, int doall){
	int maxY = startY + startHeight;
	int maxX = startX + startWidth;
	double jxmin = JULIA_XMIN, jxmax = JULIA_XMAX;
	double jymin = JULIA_YMIN, jymax = JULIA_YMAX;
	double x, y, x0, y0, tempx, tempy;
	double xsq, ysq;
	int ix, iy;
	int iteration, color;
	int iterScale = 1;
	int px, py, xindex=0, yindex=0, yptr, yptr2;
	double xscale = (jxmax - jxmin) / (startWidth);
	double yscale = (jymax - jymin) / (startHeight);
	int numIterations = params[paramIndex].numIterations;
	int numPaletteColors = params[paramIndex].numPaletteColors;

	if (level < 1) {
		return;
	}
	if (params[paramIndex].pixelBuffer == NULL) {
		LOGE("Pixel buffer is null! Cannot continue.");
		return;
	}
	
	for (px = startX; px < maxX; px++) {
		params[paramIndex].x0array[px] = jxmin + (double)(px - startX) * xscale;
	}
	
	if (numIterations < numPaletteColors) {
		iterScale = numPaletteColors / numIterations;
	}

	for (py = startY; py < maxY; py += level, yindex++) {
		y0 = jymin + (double)(py - startY) * yscale;
		yptr = py * params[paramIndex].viewWidth;

		for (px = startX; px < maxX; px += level, xindex++) {
			if (!doall) {
				if ((yindex % 2 == 0) && (xindex % 2 == 0)) {
					continue;
				}
			}

			x = params[paramIndex].x0array[px];
			y = y0;
			iteration = 0;

			while (iteration++ < numIterations) {
				xsq = x*x;
				ysq = y*y;
				if (xsq + ysq > 4.0) {
					break;
				}
				tempx = xsq - ysq + params[paramIndex].juliaX;
				tempy = 2*x*y + params[paramIndex].juliaY;
				x = tempx;
				y = tempy;
			}

			if (iteration >= numIterations) {
				color = 0;
			} else {
				color = params[paramIndex].colorPalette[(iteration * iterScale) % numPaletteColors];
			}

			if (level > 1) {
				yptr2 = yptr;
				for (iy = py+level-1; iy >= py; iy--){
					if (iy >= maxY) {
						continue;
					}
					for (ix = px+level-1; ix >= px; ix--) {
						params[paramIndex].pixelBuffer[yptr2 + ix] = color;
					}
					yptr2 += params[paramIndex].viewWidth;
				}
			} else {
				params[paramIndex].pixelBuffer[yptr + px] = color;
			}
		}
		if (terminateJob) {
			//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Terminating drawing...");
			break;
		}
	}
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_DrawFractal(JNIEnv *jenv, jclass jcls, jint paramIndex,
                                        jint startX, jint startY, jint startWidth, jint startHeight, jint level, jint doAll) {
	if (params[paramIndex].isJulia) {
		drawJulia(paramIndex, (int)startX, (int)startY, (int)startWidth, (int)startHeight, (int)level, (int)doAll);
	} else {
		drawMandelbrot(paramIndex, (int)startX, (int)startY, (int)startWidth, (int)startHeight, (int)level, (int)doAll);
	}
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SignalTerminate(JNIEnv *jenv, jclass jcls) {
	terminateJob = 1;
}


#ifdef __cplusplus
}
#endif


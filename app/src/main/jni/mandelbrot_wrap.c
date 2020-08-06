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

#ifdef __cplusplus
extern "C" {
#endif


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_initParams(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	params[paramIndex].pixelBuffer = NULL;
	params[paramIndex].pixelBufferLen = 0;
	params[paramIndex].x0array = NULL;
	params[paramIndex].terminateJob = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_setBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject bitmap) {
	AndroidBitmapInfo bitmapInfo;
	int ret;
	LOGD("setting bitmap (%d)", paramIndex);
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

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_releaseBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	LOGD("releasing bitmap (%d)", paramIndex);
	if(params[paramIndex].pixelBuffer != NULL){
		LOGD("freeing buffer...");
		free(params[paramIndex].pixelBuffer);
	}
	params[paramIndex].pixelBuffer = NULL;
	params[paramIndex].pixelBufferLen = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_updateBitmap(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject bitmap) {
	void* bitmapPixels;
	int ret;
	AndroidBitmapInfo bitmapInfo;
	LOGD("updating bitmap (%d)", paramIndex);
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


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_setColorPalette(JNIEnv *jenv, jclass jcls, jint paramIndex, jobject colorArray, jint numColors) {
	jint *c_array;
    int i = 0;
	LOGD("setting color palette (%d)", paramIndex);
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


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_setParameters(JNIEnv *jenv, jclass jcls, jint paramIndex,
                       jint power, jint numIterations, jdouble xMin, jdouble xMax, jdouble yMin, jdouble yMax,
					   jint isJulia, jdouble juliaX, jdouble juliaY, jint viewWidth, jint viewHeight) {
	LOGD("setting parameters (%d)", paramIndex);
	params[paramIndex].power = power;
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
	params[paramIndex].terminateJob = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_releaseParameters(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	if(params[paramIndex].x0array != NULL){
		free(params[paramIndex].x0array);
		params[paramIndex].x0array = NULL;
	}
}


void drawPixels(int paramIndex, int startX, int startY, int startWidth, int startHeight, int level, int doall){
	int maxY = startY + startHeight;
	int maxX = startX + startWidth;
	double x, y, x0, y0;
	double x2, y2, x3, y3, x4, y4;
	int ix, iy;
	double xscale = (params[paramIndex].xmax - params[paramIndex].xmin) / params[paramIndex].viewWidth;
	double yscale = (params[paramIndex].ymax - params[paramIndex].ymin) / params[paramIndex].viewHeight;
	int iteration, color;
	int iterScale = 1;
	int px, py, xindex=0, yindex=0, yptr, yptr2, maxIx;
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

            if (params[paramIndex].power == 2) {

                if (params[paramIndex].isJulia) {
                    x = params[paramIndex].x0array[px];
                    y = y0;
                    iteration = 0;
                    while (iteration++ < numIterations) {
                        x2 = x*x;
                        y2 = y*y;
                        if (x2 + y2 > 4.0) {
                            break;
                        }
                        y = 2*x*y + params[paramIndex].juliaY;
                        x = x2 - y2 + params[paramIndex].juliaX;
                    }
                } else {
                    x = y = x2 = y2 = 0.0;
                    iteration = 0;
                    x0 = params[paramIndex].x0array[px];
                    while (x2 + y2 < 4.0) {
                        y = 2*x*y + y0;
                        x = x2 - y2 + x0;
                        x2 = x*x;
                        y2 = y*y;
                        if (++iteration > numIterations) {
                            break;
                        }
                    }
                }

            } else if (params[paramIndex].power == 3) {

                if (params[paramIndex].isJulia) {
                    x = params[paramIndex].x0array[px];
                    y = y0;
                    iteration = 0;
                    while (iteration++ < numIterations) {
                        x2 = x*x;
                        y2 = y*y;
                        x3 = x2*x;
                        y3 = y2*y;
                        if (x2 + y2 > 4.0) {
                            break;
                        }
                        y = (3*x2*y) - y3 + params[paramIndex].juliaY;
                        x = x3 - (3*y2*x) + params[paramIndex].juliaX;
                    }
                } else {
                    x = y = x2 = y2 = x3 = y3 = 0.0;
                    iteration = 0;
                    x0 = params[paramIndex].x0array[px];
                    while (x2 + y2 < 4.0) {
                        y = (3*x2*y) - y3 + y0;
                        x = x3 - (3*y2*x) + x0;
                        x2 = x*x;
                        y2 = y*y;
                        x3 = x2*x;
                        y3 = y2*y;
                        if (++iteration > numIterations) {
                            break;
                        }
                    }
                }

			} else if (params[paramIndex].power == 4) {

                if (params[paramIndex].isJulia) {
                    x = params[paramIndex].x0array[px];
                    y = y0;
                    iteration = 0;
                    while (iteration++ < numIterations) {
                        x2 = x*x;
                        y2 = y*y;
                        x3 = x2*x;
                        y3 = y2*y;
                        x4 = x3*x;
                        y4 = y3*y;
                        if (x2 + y2 > 4.0) {
                            break;
                        }
                        y = (4*x3*y) - (4*y3*x) + params[paramIndex].juliaY;
                        x = x4 + y4 - (6*x2*y2) + params[paramIndex].juliaX;
                    }
                } else {
                    x = y = x2 = y2 = x3 = y3 = x4 = y4 = 0.0;
                    iteration = 0;
                    x0 = params[paramIndex].x0array[px];
                    while (x2 + y2 < 4.0) {
                        y = (4*x3*y) - (4*y3*x) + y0;
                        x = x4 + y4 - (6*x2*y2) + x0;
                        x2 = x*x;
                        y2 = y*y;
                        x3 = x2*x;
                        y3 = y2*y;
                        x4 = x3*x;
                        y4 = y3*y;
                        if (++iteration > numIterations) {
                            break;
                        }
                    }
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
					maxIx = px+level-1;
					if (maxIx >= maxX) {
						maxIx = maxX - 1;
					}
					for (ix = maxIx; ix >= px; ix--) {
						params[paramIndex].pixelBuffer[yptr2 + ix] = color;
					}
					yptr2 += params[paramIndex].viewWidth;
				}
			} else {
				params[paramIndex].pixelBuffer[yptr + px] = color;
			}
		}
		if (params[paramIndex].terminateJob) {
			//__android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, "Terminating drawing...");
			break;
		}
	}
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_drawFractal(JNIEnv *jenv, jclass jcls, jint paramIndex,
                                        jint startX, jint startY, jint startWidth, jint startHeight, jint level, jint doAll) {
	LOGD("drawing (%d)", paramIndex);
	drawPixels(paramIndex, (int)startX, (int)startY, (int)startWidth, (int)startHeight, (int)level, (int)doAll);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_MandelNative_signalTerminate(JNIEnv *jenv, jclass jcls, jint paramIndex) {
	LOGD("terminating...");
	params[paramIndex].terminateJob = 1;
}


#ifdef __cplusplus
}
#endif


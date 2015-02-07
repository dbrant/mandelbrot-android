#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <android/bitmap.h>

#include "mandel_native.h"

#ifdef __cplusplus
extern "C" {
#endif


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetBitmap(JNIEnv *jenv, jclass jcls, jobject bitmap) {
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

	if(pixelBuffer != NULL){
		LOGD("freeing buffer...");
		free(pixelBuffer);
	}
	
	pixelBufferLen = sizeof(uint32_t) * (bitmapInfo.width + 32) * (bitmapInfo.height + 32);
	LOGD("Creating buffer: %d bytes", pixelBufferLen);
	pixelBuffer = malloc(pixelBufferLen);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_ReleaseBitmap(JNIEnv *jenv, jclass jcls) {
	if(pixelBuffer != NULL){
		LOGD("freeing buffer...");
		free(pixelBuffer);
	}
	pixelBuffer = NULL;
	pixelBufferLen = 0;
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_UpdateBitmap(JNIEnv *jenv, jclass jcls, jobject bitmap) {
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
	if(pixelBuffer == NULL){
		LOGE("Pixel buffer is null! Cannot update.");
		return;
	}

	memcpy(bitmapPixels, pixelBuffer, sizeof(uint32_t) * (bitmapInfo.width) * (bitmapInfo.height));
	AndroidBitmap_unlockPixels(jenv, bitmap);
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetColorPalette(JNIEnv *jenv, jclass jcls, jobject colorArray, jint numColors) {
	jint *c_array;
    int i = 0;
	c_array = (*jenv)->GetIntArrayElements(jenv, colorArray, NULL);
	if (c_array == NULL) {
		LOGE("Color palette array is null...");
		return;
	}
	numPaletteColors = numColors;
	for(i=0; i<numPaletteColors; i++)
		colorPalette[i] = c_array[i];
	for(i=0; i<numPaletteColors; i++)
		colorPaletteJulia[i] = colorPalette[(numPaletteColors/2 + numPaletteColors - i - 1) % numPaletteColors];
		
	(*jenv)->ReleaseIntArrayElements(jenv, colorArray, c_array, 0);
}


JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SetParameters(JNIEnv *jenv, jclass jcls, jint jarg1, jdouble jarg2, jdouble jarg3, jdouble jarg4, jdouble jarg5, jdouble jarg6, jdouble jarg7, jint jarg8, jint jarg9, jint jarg10) {
	setParameters((int)jarg1,(double)jarg2,(double)jarg3,(double)jarg4,(double)jarg5,(double)jarg6,(double)jarg7,(int)jarg8,(int)jarg9,(int)jarg10);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_ReleaseParameters(JNIEnv *jenv, jclass jcls) {
	releaseParameters();
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_MandelbrotPixels(JNIEnv *jenv, jclass jcls, jint jarg1, jint jarg2, jint jarg3, jint jarg4, jint jarg5, jint jarg6) {
	mandelbrotPixels((int)jarg1,(int)jarg2,(int)jarg3,(int)jarg4,(int)jarg5,(int)jarg6);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_JuliaPixels(JNIEnv *jenv, jclass jcls, jint jarg1, jint jarg2, jint jarg3, jint jarg4, jint jarg5, jint jarg6) {
	juliaPixels((int)jarg1,(int)jarg2,(int)jarg3,(int)jarg4,(int)jarg5,(int)jarg6);
}

JNIEXPORT void JNICALL Java_com_dmitrybrant_android_mandelbrot_mandelnative_SignalTerminate(JNIEnv *jenv, jclass jcls) {
	signalTerminate();
}


#ifdef __cplusplus
}
#endif


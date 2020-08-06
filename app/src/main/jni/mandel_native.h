#pragma once

#define ANDROID_LOG_TAG "mandelbrot"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,ANDROID_LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,ANDROID_LOG_TAG,__VA_ARGS__)

#define MAX_PALETTE_COLORS		512

typedef struct {
    int power;
	int numIterations;
	double xmin;
	double xmax;
	double ymin;
	double ymax;
	int viewWidth;
	int viewHeight;
	int isJulia;
	double juliaX;
	double juliaY;
	uint32_t colorPalette[MAX_PALETTE_COLORS];
	int numPaletteColors;
	uint32_t* pixelBuffer;
	int pixelBufferLen;
	double* x0array;
	volatile int terminateJob;
} fractalParams;


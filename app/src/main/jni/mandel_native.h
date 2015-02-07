#pragma once

#define ANDROID_LOG_TAG "mandelbrot"
#define LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,ANDROID_LOG_TAG,__VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,ANDROID_LOG_TAG,__VA_ARGS__)

#define MAX_PALETTE_COLORS		512


#ifdef __cplusplus
extern "C" {
#endif

extern uint32_t* pixelBuffer;
extern int pixelBufferLen;

extern uint32_t colorPalette[];
extern uint32_t colorPaletteJulia[];
extern int numPaletteColors;


void setParameters(int iterations, double xMin, double xMax, double yMin, double yMax, double jx, double jy, int screenWid, int screenHgt, int coarseness);
void releaseParameters();

void mandelbrotPixels(int startX, int startY, int startWidth, int startHeight, int level, int doall);
void juliaPixels(int startX, int startY, int startWidth, int startHeight, int level, int doall);

void signalTerminate();

#ifdef __cplusplus
}
#endif

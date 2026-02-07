#ifndef mandel_native_h
#define mandel_native_h

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    float* orbitData;
    int orbitSize;
    float polyScaled[6];
    int polyLim;
    int polyScaleExp;
    double radiusExp;
} OrbitResultC;

void* mandel_createState(double x, double y, double r, int iterations);
void mandel_destroyState(void* statePtr);
void mandel_setState(void* statePtr, double x, double y, double r, int iterations);
void mandel_setStateStr(void* statePtr, const char* x_str, const char* y_str, const char* r_str, int iterations);
void mandel_zoomIn(void* statePtr, double dx, double dy, double factor);
void mandel_zoomOut(void* statePtr, double factor);
OrbitResultC mandel_generateOrbit(void* statePtr);
void mandel_setIterations(void* statePtr, int iterations);
const char* mandel_getCenterX(void* statePtr);
const char* mandel_getCenterY(void* statePtr);
const char* mandel_getRadius(void* statePtr);

#ifdef __cplusplus
}
#endif

#endif /* mandel_native_h */

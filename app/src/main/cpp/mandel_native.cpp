#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <mpfr.h>
#include <gmp.h>
#include <android/log.h>

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, "MandelNative", __VA_ARGS__)

// If you don’t have MPFR ready yet, keep a double version for first run.
// Then replace the doubles with mpfr_t and the same sequence as your JS.
struct JOrbitResult {
    std::vector<float> orbit;     // 1024*1024, triplets x,y,scale per step in R channel rows
    float polyScaled[6];          // matches JS poly_scaled[0..5]
    int   polyLim;
    int   polyScaleExp;
    int   qStart;
};

// Helper: convert string → mpfr_t
static void mpfr_set_str_auto(mpfr_t rop, const std::string &s, mpfr_rnd_t rnd) {
    mpfr_set_str(rop, s.c_str(), 10, rnd);
}

static JOrbitResult computeOrbitMPFR(const std::string& reStr,
                                     const std::string& imStr,
                                     const std::string& radiusStr,
                                     int iterations) {
    JOrbitResult out;
    out.orbit.resize(1024*1024, -1.0f);
    for (int k=0;k<6;k++) out.polyScaled[k] = 0.0f;
    out.polyLim = 0;
    out.polyScaleExp = 0;
    out.qStart = 0;

    mpfr_prec_t prec = 512; // adjust as needed
    mpfr_t cx, cy, zx, zy, tx, ty, sqr, texp;
    mpfr_inits2(prec, cx, cy, zx, zy, tx, ty, sqr, texp, (mpfr_ptr) 0);

    mpfr_set_str_auto(cx, reStr, MPFR_RNDN);
    mpfr_set_str_auto(cy, imStr, MPFR_RNDN);
    mpfr_set_d(zx, 0.0, MPFR_RNDN);
    mpfr_set_d(zy, 0.0, MPFR_RNDN);

    // Poly accumulators (mpfr)
    mpfr_t Bx, By, Cx, Cy, Dx, Dy;
    mpfr_inits2(prec,Bx,By,Cx,Cy,Dx,Dy,(mpfr_ptr)0);
    mpfr_set_d(Bx,0,MPFR_RNDN);
    mpfr_set_d(By,0,MPFR_RNDN);
    mpfr_set_d(Cx,0,MPFR_RNDN);
    mpfr_set_d(Cy,0,MPFR_RNDN);
    mpfr_set_d(Dx,0,MPFR_RNDN);
    mpfr_set_d(Dy,0,MPFR_RNDN);

    int k = 0;
    for (; k < iterations && k < (1024*1024/3); k++) {
        // Mantissa+exponent
        long expx = mpfr_get_exp(zx);
        long expy = mpfr_get_exp(zy);
        long expmax = (expx>expy)?expx:expy;
        if (!mpfr_number_p(zx) || !mpfr_number_p(zy)) break;

        // Normalize mantissa = value / 2^expmax
        mpfr_div_2si(tx, zx, expmax, MPFR_RNDN);
        mpfr_div_2si(ty, zy, expmax, MPFR_RNDN);
        out.orbit[3*k+0] = (float)mpfr_get_d(tx, MPFR_RNDN);
        out.orbit[3*k+1] = (float)mpfr_get_d(ty, MPFR_RNDN);
        out.orbit[3*k+2] = (float)expmax;

        // Poly accumulation
        if (k>0) {
            // Bx += 2*zx, By += 2*zy
            mpfr_mul_ui(tx,zx,2,MPFR_RNDN);
            mpfr_add(Bx,Bx,tx,MPFR_RNDN);
            mpfr_mul_ui(ty,zy,2,MPFR_RNDN);
            mpfr_add(By,By,ty,MPFR_RNDN);

            // Cx += zx, Cy += zy
            mpfr_add(Cx,Cx,zx,MPFR_RNDN);
            mpfr_add(Cy,Cy,zy,MPFR_RNDN);

            // Dx += zx^2 - zy^2 ; Dy += 2*zx*zy
            mpfr_sqr(tx,zx,MPFR_RNDN);
            mpfr_sqr(ty,zy,MPFR_RNDN);
            mpfr_sub(tx,tx,ty,MPFR_RNDN);
            mpfr_add(Dx,Dx,tx,MPFR_RNDN);

            mpfr_mul(tx,zx,zy,MPFR_RNDN);
            mpfr_mul_ui(tx,tx,2,MPFR_RNDN);
            mpfr_add(Dy,Dy,tx,MPFR_RNDN);
        }

        // z = z^2 + c
        mpfr_sqr(tx,zx,MPFR_RNDN); // zx^2
        mpfr_sqr(ty,zy,MPFR_RNDN); // zy^2
        mpfr_sub(sqr,tx,ty,MPFR_RNDN);
        mpfr_add(zx,sqr,cx,MPFR_RNDN);

        mpfr_mul(tx,zx,zy,MPFR_RNDN); // careful: need old zx
        mpfr_mul_ui(tx,tx,2,MPFR_RNDN);
        mpfr_add(zy,tx,cy,MPFR_RNDN);

        mpfr_sqr(tx,zx,MPFR_RNDN);
        mpfr_sqr(ty,zy,MPFR_RNDN);
        mpfr_add(sqr,tx,ty,MPFR_RNDN);
        if (mpfr_cmp_d(sqr,400.0)>0) break;
    }

    // Select polyLim = i = k (simpler version; you can add heuristics like JS)
    int polylim = k;
    out.polyLim = polylim;

    // Evaluate poly accumulators
    mpfr_t vals[6]; for(int i=0;i<6;i++) mpfr_init2(vals[i],prec);
    mpfr_set(vals[0],Bx,MPFR_RNDN);
    mpfr_set(vals[1],By,MPFR_RNDN);
    mpfr_set(vals[2],Cx,MPFR_RNDN);
    mpfr_set(vals[3],Cy,MPFR_RNDN);
    mpfr_set(vals[4],Dx,MPFR_RNDN);
    mpfr_set(vals[5],Dy,MPFR_RNDN);

    // poly_scale_exp = max exponent of these
    long poly_scale_exp = -999999;
    for(int i=0;i<6;i++) {
        long e = mpfr_get_exp(vals[i]);
        if (e>poly_scale_exp) poly_scale_exp = e;
    }
    out.polyScaleExp = (int)poly_scale_exp;

    // Scale them
    for(int i=0;i<6;i++) {
        mpfr_div_2si(vals[i],vals[i],poly_scale_exp,MPFR_RNDN);
        out.polyScaled[i] = (float)mpfr_get_d(vals[i],MPFR_RNDN);
    }

    // qStart ~ exponent of last z
    out.qStart = (int)mpfr_get_exp(zx);

    // clear
    mpfr_clears(cx,cy,zx,zy,tx,ty,sqr,texp,Bx,By,Cx,Cy,Dx,Dy,(mpfr_ptr)0);
    for(int i=0;i<6;i++) mpfr_clear(vals[i]);

    return out;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelNative_makeReferenceOrbit(
        JNIEnv* env, jobject thiz,
        jstring reStr, jstring imStr, jstring radiusStr, jint iterations) {

    const char* reC = env->GetStringUTFChars(reStr, 0);
    const char* imC = env->GetStringUTFChars(imStr, 0);
    const char* rC  = env->GetStringUTFChars(radiusStr, 0);


    __android_log_print(ANDROID_LOG_INFO,  __FUNCTION__, ">>>>>>>>> %s", reC);
    __android_log_print(ANDROID_LOG_INFO,  __FUNCTION__, ">>>>>>>>> %s", imC);
    __android_log_print(ANDROID_LOG_INFO,  __FUNCTION__, ">>>>>>>>> %s", rC);

    auto res = computeOrbitMPFR(reC, imC, rC, iterations);

    env->ReleaseStringUTFChars(reStr, reC);
    env->ReleaseStringUTFChars(imStr, imC);
    env->ReleaseStringUTFChars(radiusStr, rC);

    // Build OrbitResult Kotlin data class
    jclass localClass = env->FindClass("com/dmitrybrant/android/mandelbrot/OrbitResult");
    //jclass globalClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    jmethodID ctor = env->GetMethodID(localClass, "<init>", "([F[FIII)V");

    // orbit float[]
    jfloatArray orbitArr = env->NewFloatArray((jsize)res.orbit.size());
    env->SetFloatArrayRegion(orbitArr, 0, (jsize)res.orbit.size(), res.orbit.data());

    // polyScaled float[6]
    jfloatArray polyArr = env->NewFloatArray(6);
    env->SetFloatArrayRegion(polyArr, 0, 6, res.polyScaled);

    jobject obj = env->NewObject(localClass, ctor,
                                 orbitArr, polyArr,
                                 (jint)res.polyLim,
                                 (jint)res.polyScaleExp,
                                 (jint)res.qStart);
    return obj;
}

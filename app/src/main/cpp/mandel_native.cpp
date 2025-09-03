#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <string>
#include <algorithm>
#include <mpfr.h>
#include <android/log.h>

#define LOG_TAG "MandelbrotNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CALC_WIDTH 1024
#define CALC_HEIGHT 1024
#define CALC_ITERATIONS 1000
#define CALC_BAILOUT 400
#define MPFR_DIGITS 1200

class MandelbrotState {
private:
    mpfr_t center_x, center_y, radius;

public:
    int iterations;

    std::shared_ptr<std::vector<float>> orbitPtr = std::make_shared<std::vector<float>>(CALC_WIDTH * CALC_HEIGHT);

    MandelbrotState() : iterations(CALC_ITERATIONS) {
        mpfr_init2(center_x, MPFR_DIGITS);
        mpfr_init2(center_y, MPFR_DIGITS);
        mpfr_init2(radius, MPFR_DIGITS);

        reset();
    }

    ~MandelbrotState() {
        mpfr_clear(center_x);
        mpfr_clear(center_y);
        mpfr_clear(radius);
    }

    void set(double x, double y, double r, int iterations) {
        mpfr_set_d(center_x, x, MPFR_RNDN);
        mpfr_set_d(center_y, y, MPFR_RNDN);
        mpfr_set_d(radius, r, MPFR_RNDN);
        this->iterations = iterations;
    }

    void set(const std::string& x_str, const std::string& y_str, const std::string& r_str, int iterations) {
        int result_x = mpfr_set_str(center_x, x_str.c_str(), 10, MPFR_RNDN);
        int result_y = mpfr_set_str(center_y, y_str.c_str(), 10, MPFR_RNDN);
        int result_r = mpfr_set_str(radius, r_str.c_str(), 10, MPFR_RNDN);
        this->iterations = iterations;
        
        if (result_x != 0 || result_y != 0 || result_r != 0) {
            LOGI("Warning: Failed to parse some coordinate strings");
        }
    }

    void update(double dx, double dy) {
        mpfr_t mx, my;
        mpfr_init2(mx, MPFR_DIGITS);
        mpfr_init2(my, MPFR_DIGITS);

        // mx = radius * dx
        mpfr_mul_d(mx, radius, dx, MPFR_RNDN);
        // my = radius * (-dy)
        mpfr_mul_d(my, radius, -dy, MPFR_RNDN);

        // radius = radius * 0.5
        mpfr_mul_d(radius, radius, 0.5, MPFR_RNDN);

        // center_x += mx
        mpfr_add(center_x, center_x, mx, MPFR_RNDN);
        // center_y += my
        mpfr_add(center_y, center_y, my, MPFR_RNDN);

        mpfr_clear(mx);
        mpfr_clear(my);
    }

    void zoomOut(double factor) {
        mpfr_mul_d(radius, radius, factor, MPFR_RNDN);
    }

    void reset() {
        iterations = CALC_ITERATIONS;
        mpfr_set_d(center_x, -0.5, MPFR_RNDN);
        mpfr_set_d(center_y, 0.0, MPFR_RNDN);
        mpfr_set_d(radius, 2.0, MPFR_RNDN);
    }

    mpfr_t* getCenterX() { return &center_x; }
    mpfr_t* getCenterY() { return &center_y; }
    mpfr_t* getRadius() { return &radius; }

    std::string getCenterXString() const {
        char* str = mpfr_get_str(nullptr, nullptr, 10, 0, center_x, MPFR_RNDN);
        std::string result(str);
        mpfr_free_str(str);
        return result;
    }

    std::string getCenterYString() const {
        char* str = mpfr_get_str(nullptr, nullptr, 10, 0, center_y, MPFR_RNDN);
        std::string result(str);
        mpfr_free_str(str);
        return result;
    }

    std::string getRadiusString() const {
        char* str = mpfr_get_str(nullptr, nullptr, 10, 0, radius, MPFR_RNDN);
        std::string result(str);
        mpfr_free_str(str);
        return result;
    }
};

// Helper functions for double-double arithmetic (mantissa, exponent pairs)
struct DoubleDouble {
    double mantissa;
    double exponent;

    DoubleDouble(double m = 0.0, double e = 0.0) : mantissa(m), exponent(e) {}
};

DoubleDouble sub(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = am * std::pow(2, a.exponent - ret_e);
    } else {
        bm = bm * std::pow(2, b.exponent - ret_e);
    }
    return DoubleDouble(am - bm, ret_e);
}

DoubleDouble add(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = am * std::pow(2, a.exponent - ret_e);
    } else {
        bm = bm * std::pow(2, b.exponent - ret_e);
    }
    return DoubleDouble(am + bm, ret_e);
}

DoubleDouble mul(const DoubleDouble& a, const DoubleDouble& b) {
    double m = a.mantissa * b.mantissa;
    double e = a.exponent + b.exponent;
    if (m != 0) {
        double logm = std::round(std::log2(std::abs(m)));
        m = m / std::pow(2, logm);
        e = e + logm;
    }
    return DoubleDouble(m, e);
}

DoubleDouble maxabs(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = am * std::pow(2, a.exponent - ret_e);
    } else {
        bm = bm * std::pow(2, b.exponent - ret_e);
    }
    return DoubleDouble(std::max(std::abs(am), std::abs(bm)), ret_e);
}

bool gt(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = am * std::pow(2, a.exponent - ret_e);
    } else {
        bm = bm * std::pow(2, b.exponent - ret_e);
    }
    return am > bm;
}

float floaty(const DoubleDouble& d) {
    return std::pow(2, d.exponent) * d.mantissa;
}

struct OrbitData {
    std::vector<double> poly;
    int polylim;
    std::vector<float> polyScaled;
    int polyScaleExp;
};

OrbitData makeReferenceOrbit(MandelbrotState& state) {
    LOGI("makeReferenceOrbit: Starting orbit generation");

    mpfr_t x, y, cx, cy;
    mpfr_init2(x, MPFR_DIGITS);
    mpfr_init2(y, MPFR_DIGITS);
    mpfr_init2(cx, MPFR_DIGITS);
    mpfr_init2(cy, MPFR_DIGITS);

    // Initialize starting point
    mpfr_set_d(x, 0.0, MPFR_RNDN);
    mpfr_set_d(y, 0.0, MPFR_RNDN);
    mpfr_set(cx, *state.getCenterX(), MPFR_RNDN);
    mpfr_set(cy, *state.getCenterY(), MPFR_RNDN);

    std::vector<float>& orbit = *state.orbitPtr;
    std::fill(orbit.begin(), orbit.end(), -1.0);

    mpfr_t txx, txy, tyy;
    mpfr_init2(txx, MPFR_DIGITS);
    mpfr_init2(txy, MPFR_DIGITS);
    mpfr_init2(tyy, MPFR_DIGITS);

    int polylim = 0;

    DoubleDouble Bx(0, 0), By(0, 0), Cx(0, 0), Cy(0, 0), Dx(0, 0), Dy(0, 0);
    std::vector<DoubleDouble> poly = {Bx, By, Cx, Cy, Dx, Dy};
    bool not_failed = true;

    int i;
    for (i = 0; i < state.iterations; i++) {
        // Get exponents for scaling
        mpfr_exp_t x_exponent = mpfr_get_exp(x);
        mpfr_exp_t y_exponent = mpfr_get_exp(y);
        mpfr_exp_t scale_exponent = std::max(x_exponent, y_exponent);

        if (scale_exponent < -10000) {
            scale_exponent = 0;
        }

        if (3 * i + 2 < orbit.size()) {
            if (mpfr_zero_p(x) && mpfr_zero_p(y)) {
                orbit[3 * i] = 0.0;
                orbit[3 * i + 1] = 0.0;
                orbit[3 * i + 2] = 0.0;
            } else {
                mpfr_exp_t dummy_exp;
                double x_mantissa = mpfr_get_d_2exp(&dummy_exp, x, MPFR_RNDN);
                double y_mantissa = mpfr_get_d_2exp(&dummy_exp, y, MPFR_RNDN);

                orbit[3 * i] = mpfr_zero_p(x) ? 0.0 : (x_mantissa / std::pow(2, scale_exponent - x_exponent));
                orbit[3 * i + 1] = mpfr_zero_p(y) ? 0.0 : (y_mantissa / std::pow(2, scale_exponent - y_exponent));
                orbit[3 * i + 2] = scale_exponent;
            }
        }

        DoubleDouble fx(orbit[3 * i], orbit[3 * i + 2]);
        DoubleDouble fy(orbit[3 * i + 1], orbit[3 * i + 2]);

        std::vector<DoubleDouble> prev_poly = {Bx, By, Cx, Cy, Dx, Dy};
        // Now do the Mandelbrot iteration: z = z^2 + c
        mpfr_mul(txx, x, x, MPFR_RNDN);
        mpfr_mul(txy, x, y, MPFR_RNDN);
        mpfr_mul(tyy, y, y, MPFR_RNDN);
        mpfr_sub(x, txx, tyy, MPFR_RNDN);
        mpfr_add(x, x, cx, MPFR_RNDN);
        mpfr_add(y, txy, txy, MPFR_RNDN);
        mpfr_add(y, y, cy, MPFR_RNDN);

        // B_n+1 = 2 * z_n * B_n + 1
        DoubleDouble new_Bx = add(mul(DoubleDouble(2, 0), sub(mul(fx, Bx), mul(fy, By))), DoubleDouble(1, 0));
        DoubleDouble new_By = mul(DoubleDouble(2, 0), add(mul(fx, By), mul(fy, Bx)));

        // C_n+1 = 2 * z_n * C_n + B_n^2
        DoubleDouble new_Cx = sub(add(mul(DoubleDouble(2, 0), sub(mul(fx, Cx), mul(fy, Cy))), mul(Bx, Bx)), mul(By, By));
        DoubleDouble new_Cy = add(mul(DoubleDouble(2, 0), add(mul(fx, Cy), mul(fy, Cx))), mul(mul(DoubleDouble(2, 0), Bx), By));

        // D_n+1 = 2 * z_n * D_n + 2 * B_n * C_n
        DoubleDouble new_Dx = mul(DoubleDouble(2, 0), add(sub(mul(fx, Dx), mul(fy, Dy)), sub(mul(Cx, Bx), mul(Cy, By))));
        DoubleDouble new_Dy = mul(DoubleDouble(2, 0), add(add(add(mul(fx, Dy), mul(fy, Dx)), mul(Cx, By)), mul(Cy, Bx)));

        // Update the coefficients
        Bx = new_Bx; By = new_By; Cx = new_Cx; Cy = new_Cy; Dx = new_Dx; Dy = new_Dy;

        mpfr_exp_t fx_new_exp, fy_new_exp;
        double fx_new_mantissa = mpfr_get_d_2exp(&fx_new_exp, x, MPFR_RNDN);
        double fy_new_mantissa = mpfr_get_d_2exp(&fy_new_exp, y, MPFR_RNDN);
        
        // These are used for polynomial selection and escape test
        DoubleDouble fx_new(fx_new_mantissa, fx_new_exp);
        DoubleDouble fy_new(fy_new_mantissa, fy_new_exp);

        mpfr_t radius_for_poly;
        mpfr_init2(radius_for_poly, MPFR_DIGITS);
        mpfr_set(radius_for_poly, *state.getRadius(), MPFR_RNDN);
        mpfr_exp_t radius_exp = mpfr_get_exp(radius_for_poly);
        
        DoubleDouble threshold = mul(DoubleDouble(1000, radius_exp), maxabs(Dx, Dy));
        
        if (i == 0 || gt(maxabs(Cx, Cy), threshold)) {
            if (not_failed) {
                poly = prev_poly;
                polylim = i;
            }
        } else {
            not_failed = false;
        }
        
        mpfr_clear(radius_for_poly);

        DoubleDouble z_squared = add(mul(fx_new, fx_new), mul(fy_new, fy_new));
        if (gt(z_squared, DoubleDouble(CALC_BAILOUT, 0))) {
            break;
        }
    }

    mpfr_clear(x);
    mpfr_clear(y);
    mpfr_clear(cx);
    mpfr_clear(cy);
    mpfr_clear(txx);
    mpfr_clear(txy);
    mpfr_clear(tyy);

    LOGI("Orbit generation completed: %d iterations, polylim: %d", i, polylim);

    std::vector<double> poly_double;
    for (const auto& p : poly) {
        poly_double.push_back(floaty(p));
    }

    LOGI("Polynomial coefficients: [%f, %f, %f, %f, %f, %f]",
         poly_double[0], poly_double[1], poly_double[2], poly_double[3], poly_double[4], poly_double[5]);

    mpfr_t radius_mpfr;
    mpfr_init2(radius_mpfr, MPFR_DIGITS);
    mpfr_set(radius_mpfr, *state.getRadius(), MPFR_RNDN);

    mpfr_exp_t rexp = mpfr_get_exp(radius_mpfr);
    mpfr_exp_t exp_temp;
    double r_mantissa = mpfr_get_d_2exp(&exp_temp, radius_mpfr, MPFR_RNDN);
    DoubleDouble r(r_mantissa, rexp);
    mpfr_clear(radius_mpfr);

    DoubleDouble poly_scale_exp = mul(DoubleDouble(1, 0), maxabs(poly[0], poly[1]));
    DoubleDouble poly_scale(1, -poly_scale_exp.exponent);

    std::vector<float> poly_scaled = {
            floaty(mul(poly_scale, poly[0])),
            floaty(mul(poly_scale, poly[1])),
            floaty(mul(poly_scale, mul(r, poly[2]))),
            floaty(mul(poly_scale, mul(r, poly[3]))),
            floaty(mul(poly_scale, mul(r, mul(r, poly[4])))),
            floaty(mul(poly_scale, mul(r, mul(r, poly[5]))))
    };

    LOGI("Scaled coefficients: [%f, %f, %f, %f, %f, %f]",
         poly_scaled[0], poly_scaled[1], poly_scaled[2], poly_scaled[3], poly_scaled[4], poly_scaled[5]);

    return { poly_double, polylim, poly_scaled, (int)poly_scale_exp.exponent };
}

// JNI wrapper functions
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_createState(JNIEnv *env, jobject clazz) {
    return reinterpret_cast<jlong>(new MandelbrotState());
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_destroyState(JNIEnv *env, jobject clazz, jlong statePtr) {
    LOGI("Cleaning up native resources.");
    delete reinterpret_cast<MandelbrotState*>(statePtr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setState(JNIEnv *env, jobject clazz, jlong statePtr, jdouble x, jdouble y, jdouble r, jint iterations) {
    reinterpret_cast<MandelbrotState*>(statePtr)->set(x, y, r, iterations);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_updateState(JNIEnv *env, jobject clazz, jlong statePtr, jdouble dx, jdouble dy) {
    reinterpret_cast<MandelbrotState*>(statePtr)->update(dx, dy);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setStateStr(JNIEnv *env, jobject clazz, jlong statePtr, jstring x_str, jstring y_str, jstring r_str, jint iterations) {
    const char* x_cstr = env->GetStringUTFChars(x_str, nullptr);
    const char* y_cstr = env->GetStringUTFChars(y_str, nullptr);
    const char* r_cstr = env->GetStringUTFChars(r_str, nullptr);
    
    reinterpret_cast<MandelbrotState*>(statePtr)->set(
        std::string(x_cstr), std::string(y_cstr), std::string(r_cstr), iterations
    );
    
    env->ReleaseStringUTFChars(x_str, x_cstr);
    env->ReleaseStringUTFChars(y_str, y_cstr);
    env->ReleaseStringUTFChars(r_str, r_cstr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_zoomOut(JNIEnv *env, jobject clazz, jlong statePtr, jdouble factor) {
    reinterpret_cast<MandelbrotState*>(statePtr)->zoomOut(factor);
}

JNIEXPORT jobject JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_generateOrbit(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    jclass localClass = env->FindClass("com/dmitrybrant/android/mandelbrot/OrbitResult");
    jmethodID ctor = env->GetMethodID(localClass, "<init>", "(Ljava/nio/ByteBuffer;[FIID)V");

    void* dataPtr = state->orbitPtr->data();
    jobject orbitBuffer = env->NewDirectByteBuffer(dataPtr, state->orbitPtr->size() * sizeof(float));

    jfloatArray polyArr = env->NewFloatArray(data.polyScaled.size());
    env->SetFloatArrayRegion(polyArr, 0, data.polyScaled.size(), data.polyScaled.data());

    mpfr_t log_val;
    mpfr_init2(log_val, MPFR_DIGITS);
    mpfr_log2(log_val, *state->getRadius(), MPFR_RNDN);
    double radiusExp = mpfr_get_d(log_val, MPFR_RNDN);
    mpfr_clear(log_val);

    jobject obj = env->NewObject(localClass, ctor,
                                 orbitBuffer, polyArr,
                                 (jint)data.polylim,
                                 (jint)data.polyScaleExp,
                                 (jdouble)radiusExp);
    return obj;
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterX(JNIEnv *env, jobject clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getCenterXString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterY(JNIEnv *env, jobject clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getCenterYString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getRadius(JNIEnv *env, jobject clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getRadiusString();
    return env->NewStringUTF(str.c_str());
}

}

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


class MandelbrotState {
private:
    mpfr_t center_x, center_y, radius;

public:
    int iterations;
    double cmapscale;

    MandelbrotState() : iterations(1000), cmapscale(20.0) {
        mpfr_init2(center_x, 1200);
        mpfr_init2(center_y, 1200);
        mpfr_init2(radius, 1200);

        // Initialize to default values
        mpfr_set_d(center_x, -0.5, MPFR_RNDN);
        mpfr_set_d(center_y, 0.0, MPFR_RNDN);
        mpfr_set_d(radius, 2.0, MPFR_RNDN);
    }

    ~MandelbrotState() {
        mpfr_clear(center_x);
        mpfr_clear(center_y);
        mpfr_clear(radius);
    }

    void set(double x, double y, double r) {
        mpfr_set_d(center_x, x, MPFR_RNDN);
        mpfr_set_d(center_y, y, MPFR_RNDN);
        mpfr_set_d(radius, r, MPFR_RNDN);
    }

    void setFromStrings(const std::string& x_str, const std::string& y_str, const std::string& r_str) {
        mpfr_set_str(center_x, x_str.c_str(), 10, MPFR_RNDN);
        mpfr_set_str(center_y, y_str.c_str(), 10, MPFR_RNDN);
        mpfr_set_str(radius, r_str.c_str(), 10, MPFR_RNDN);
    }

    void update(double dx, double dy) {
        mpfr_t mx, my;
        mpfr_init2(mx, 1200);
        mpfr_init2(my, 1200);

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

    void zoomOut() {
        mpfr_mul_d(radius, radius, 2.0, MPFR_RNDN);
    }

    void reset() {
        iterations = 1000;
        cmapscale = 20.1;
        mpfr_set_d(center_x, 0.0, MPFR_RNDN);
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

double floaty(const DoubleDouble& d) {
    return std::pow(2, d.exponent) * d.mantissa;
}

struct OrbitData {
    std::vector<float> orbit;
    std::vector<double> poly;
    int polylim;
    std::vector<float> polyScaled;
    int polyScaleExp;
};

OrbitData makeReferenceOrbit(MandelbrotState& state) {
    LOGI("makeReferenceOrbit: Starting orbit generation");

    mpfr_t x, y, cx, cy;
    mpfr_init2(x, 1200);
    mpfr_init2(y, 1200);
    mpfr_init2(cx, 1200);
    mpfr_init2(cy, 1200);

    // Initialize starting point
    mpfr_set_d(x, 0.0, MPFR_RNDN);
    mpfr_set_d(y, 0.0, MPFR_RNDN);
    mpfr_set(cx, *state.getCenterX(), MPFR_RNDN);
    mpfr_set(cy, *state.getCenterY(), MPFR_RNDN);

    // Debug: print center values
    double cx_debug = mpfr_get_d(cx, MPFR_RNDN);
    double cy_debug = mpfr_get_d(cy, MPFR_RNDN);
    LOGI("Center: (%f, %f), iterations: %d", cx_debug, cy_debug, state.iterations);

    std::vector<float> orbit(1024 * 1024, -1.0f);

    mpfr_t txx, txy, tyy;
    mpfr_init2(txx, 1200);
    mpfr_init2(txy, 1200);
    mpfr_init2(tyy, 1200);

    int polylim = 0;

    // Initialize polynomial coefficients
    DoubleDouble Bx(1, 0), By(0, 0), Cx(0, 0), Cy(0, 0), Dx(0, 0), Dy(0, 0);
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

        // Store orbit data (ensure we don't overflow the array)
        if (3 * i + 2 < orbit.size()) {
            mpfr_exp_t exp_temp;
            double x_mantissa = mpfr_get_d_2exp(&exp_temp, x, MPFR_RNDN);
            double y_mantissa = mpfr_get_d_2exp(&exp_temp, y, MPFR_RNDN);

            // Handle special case where x or y is exactly zero
            if (mpfr_zero_p(x)) {
                orbit[3 * i] = 0.0f;
            } else {
                orbit[3 * i] = x_mantissa / std::pow(2, scale_exponent - x_exponent);
            }

            if (mpfr_zero_p(y)) {
                orbit[3 * i + 1] = 0.0f;
            } else {
                orbit[3 * i + 1] = y_mantissa / std::pow(2, scale_exponent - y_exponent);
            }

            orbit[3 * i + 2] = scale_exponent;

            // Debug first few orbit points
            if (i < 5) {
                LOGI("Orbit[%d]: (%f, %f, %f)", i, orbit[3*i], orbit[3*i+1], orbit[3*i+2]);
            }
        }

        // Create DoubleDouble representations for current point
        mpfr_exp_t fx_exp, fy_exp;
        double fx_mantissa = mpfr_get_d_2exp(&fx_exp, x, MPFR_RNDN);
        double fy_mantissa = mpfr_get_d_2exp(&fy_exp, y, MPFR_RNDN);
        DoubleDouble fx(fx_mantissa, fx_exp);
        DoubleDouble fy(fy_mantissa, fy_exp);

        // Mandelbrot iteration: z = z^2 + c
        mpfr_mul(txx, x, x, MPFR_RNDN);
        mpfr_mul(txy, x, y, MPFR_RNDN);
        mpfr_mul(tyy, y, y, MPFR_RNDN);
        mpfr_sub(x, txx, tyy, MPFR_RNDN);
        mpfr_add(x, x, cx, MPFR_RNDN);
        mpfr_add(y, txy, txy, MPFR_RNDN);
        mpfr_add(y, y, cy, MPFR_RNDN);

        // Update polynomial coefficients for perturbation theory
        std::vector<DoubleDouble> prev_poly = {Bx, By, Cx, Cy, Dx, Dy};

        // B_n+1 = 2 * z_n * B_n + 1
        Bx = add(mul(DoubleDouble(2, 0), sub(mul(fx, Bx), mul(fy, By))), DoubleDouble(1, 0));
        By = mul(DoubleDouble(2, 0), add(mul(fx, By), mul(fy, Bx)));

        // C_n+1 = 2 * z_n * C_n + B_n^2
        Cx = sub(add(mul(DoubleDouble(2, 0), sub(mul(fx, Cx), mul(fy, Cy))), mul(Bx, Bx)), mul(By, By));
        Cy = add(mul(DoubleDouble(2, 0), add(mul(fx, Cy), mul(fy, Cx))), mul(mul(DoubleDouble(2, 0), Bx), By));

        // D_n+1 = 2 * z_n * D_n + 2 * B_n * C_n
        Dx = mul(DoubleDouble(2, 0), add(sub(mul(fx, Dx), mul(fy, Dy)), sub(mul(Cx, Bx), mul(Cy, By))));
        Dy = mul(DoubleDouble(2, 0), add(add(add(mul(fx, Dy), mul(fy, Dx)), mul(Cx, By)), mul(Cy, Bx)));

        // Get new point values
        mpfr_exp_t fx_new_exp, fy_new_exp;
        double fx_new_mantissa = mpfr_get_d_2exp(&fx_new_exp, x, MPFR_RNDN);
        double fy_new_mantissa = mpfr_get_d_2exp(&fy_new_exp, y, MPFR_RNDN);
        DoubleDouble fx_new(fx_new_mantissa, fx_new_exp);
        DoubleDouble fy_new(fy_new_mantissa, fy_new_exp);

        // Check polynomial validity for perturbation theory
        mpfr_exp_t radius_exp = mpfr_get_exp(*state.getRadius());
        DoubleDouble radius_factor(1000, radius_exp);

        if (i == 0 || gt(maxabs(Cx, Cy), mul(radius_factor, maxabs(Dx, Dy)))) {
            if (not_failed) {
                poly = prev_poly;
                polylim = i;
            }
        } else {
            not_failed = false;
        }

        // Check escape condition |z|^2 > 4
        DoubleDouble z_squared = add(mul(fx_new, fx_new), mul(fy_new, fy_new));
        if (gt(z_squared, DoubleDouble(4, 0))) {
            break;
        }
    }

    // Clean up
    mpfr_clear(x);
    mpfr_clear(y);
    mpfr_clear(cx);
    mpfr_clear(cy);
    mpfr_clear(txx);
    mpfr_clear(txy);
    mpfr_clear(tyy);

    LOGI("Orbit generation completed: %d iterations, polylim: %d", i, polylim);

    // Convert poly to double vector
    std::vector<double> poly_double;
    for (const auto& p : poly) {
        poly_double.push_back(floaty(p));
    }

    LOGI("Polynomial coefficients: [%f, %f, %f, %f, %f, %f]",
         poly_double[0], poly_double[1], poly_double[2], poly_double[3], poly_double[4], poly_double[5]);

    // Calculate scaled polynomial coefficients (matching JS logic)
    mpfr_t radius_mpfr;
    mpfr_init2(radius_mpfr, 1200);
    mpfr_set(radius_mpfr, *state.getRadius(), MPFR_RNDN);

    mpfr_exp_t rexp = mpfr_get_exp(radius_mpfr);
    mpfr_exp_t exp_temp;
    double r_mantissa = mpfr_get_d_2exp(&exp_temp, radius_mpfr, MPFR_RNDN);
    DoubleDouble r(r_mantissa, rexp);

    // Calculate polynomial scaling
    DoubleDouble poly_scale_magnitude = maxabs(poly[0], poly[1]);
    if (poly_scale_magnitude.mantissa == 0.0) {
        // Handle case where polynomial coefficients are zero
        poly_scale_magnitude = DoubleDouble(1, 0);
    }

    DoubleDouble poly_scale(1, -poly_scale_magnitude.exponent);

    std::vector<float> poly_scaled = {
            static_cast<float>(floaty(mul(poly_scale, poly[0]))),
            static_cast<float>(floaty(mul(poly_scale, poly[1]))),
            static_cast<float>(floaty(mul(poly_scale, mul(r, poly[2])))),
            static_cast<float>(floaty(mul(poly_scale, mul(r, poly[3])))),
            static_cast<float>(floaty(mul(poly_scale, mul(r, mul(r, poly[4]))))),
            static_cast<float>(floaty(mul(poly_scale, mul(r, mul(r, poly[5])))))
    };

    LOGI("Scaled polynomial coefficients: [%f, %f, %f, %f, %f, %f]",
         poly_scaled[0], poly_scaled[1], poly_scaled[2], poly_scaled[3], poly_scaled[4], poly_scaled[5]);

    mpfr_clear(radius_mpfr);

    return {orbit, poly_double, polylim, poly_scaled, (int)poly_scale_magnitude.exponent};
}

// JNI wrapper functions
extern "C" {
JNIEXPORT jlong JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_createState(JNIEnv *env, jclass clazz) {
    return reinterpret_cast<jlong>(new MandelbrotState());
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_destroyState(JNIEnv *env, jclass clazz, jlong statePtr) {
    delete reinterpret_cast<MandelbrotState*>(statePtr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setState(JNIEnv *env, jclass clazz, jlong statePtr, jdouble x, jdouble y, jdouble r) {
    reinterpret_cast<MandelbrotState*>(statePtr)->set(x, y, r);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_updateState(JNIEnv *env, jclass clazz, jlong statePtr, jdouble dx, jdouble dy) {
    reinterpret_cast<MandelbrotState*>(statePtr)->update(dx, dy);
}

JNIEXPORT jfloatArray JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_generateOrbit(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    jfloatArray result = env->NewFloatArray(data.orbit.size());
    env->SetFloatArrayRegion(result, 0, data.orbit.size(), data.orbit.data());

    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getPolynomialCoefficients(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    jfloatArray result = env->NewFloatArray(data.polyScaled.size());
    env->SetFloatArrayRegion(result, 0, data.polyScaled.size(), data.polyScaled.data());

    return result;
}

JNIEXPORT jint JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getPolynomialLimit(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    return data.polylim;
}

JNIEXPORT jint JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getPolynomialScaleExp(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    return data.polyScaleExp;
}

JNIEXPORT jdouble JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getRadiusExponent(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    mpfr_t log_val;
    mpfr_init2(log_val, 1200);
    mpfr_log2(log_val, *state->getRadius(), MPFR_RNDN);
    double result = mpfr_get_d(log_val, MPFR_RNDN);
    mpfr_clear(log_val);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterX(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getCenterXString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterY(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getCenterYString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getRadius(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState*>(statePtr)->getRadiusString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_testBasicFunctionality(JNIEnv *env, jclass clazz) {
    // Test basic MPFR functionality
    mpfr_t test;

    LOGI(">>>>>>>>>>>>> mpfr_init2");
    mpfr_init2(test, 1200);

    LOGI(">>>>>>>>>>>>> mpfr_set_d");
    mpfr_set_d(test, 3.14159, MPFR_RNDN);

    mpfr_exp_t e;

    LOGI(">>>>>>>>>>>>> mpfr_get_str");
    char* str = mpfr_get_str(nullptr, &e, 10, 10, test, MPFR_RNDN);

    LOGI(">>>>>>>>>>>>> NewStringUTF");
    jstring result = env->NewStringUTF(str);

    LOGI(">>>>>>>>>>>>> mpfr_free_str");
    mpfr_free_str(str);

    LOGI(">>>>>>>>>>>>> mpfr_clear");
    mpfr_clear(test);

    LOGI(">>>>>>>>>>>>> result: %s", str);
    return result;
}

}
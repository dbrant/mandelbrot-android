/*
 * Adapted from https://github.com/HastingsGreer/mandeljs
 * 
 * Copyright 2025+ Dmitry Brant
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <string>
#include <algorithm>
#include <array>
#include <atomic>
#include <mutex>
#include <mpfr.h>
#include <android/log.h>

#define LOG_TAG "MandelbrotNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define CALC_WIDTH 1024
#define CALC_HEIGHT 1024
#define CALC_BAILOUT 400
#define MPFR_DIGITS 1200

std::string mpfr_to_string(mpfr_t *x, int base = 10, size_t precision = 0) {
    mpfr_exp_t exp;
    char *mantissa = mpfr_get_str(nullptr, &exp, base, precision, *x, MPFR_RNDN);
    if (!mantissa) return {};

    std::string result;
    bool is_negative = (mantissa[0] == '-');
    std::string digits = is_negative ? mantissa + 1 : mantissa;

    if (is_negative)
        result.push_back('-');

    if (exp <= 0) {
        // number is < 1
        result += "0.";
        result.append(-exp, '0'); // leading zeros after decimal point
        result += digits;
    } else if ((size_t)exp >= digits.size()) {
        // number is >= 1, no decimal point needed (just add trailing zeros if needed)
        result += digits;
        result.append(exp - digits.size(), '0');
    } else {
        // insert decimal point inside digits
        result.append(digits.substr(0, exp));
        result.push_back('.');
        result.append(digits.substr(exp));
    }

    // trim trailing zeros
    if (auto dot_pos = result.find('.'); dot_pos != std::string::npos) {
        while (!result.empty() && result.back() == '0')
            result.pop_back();
        if (!result.empty() && result.back() == '.')
            result.pop_back(); // remove decimal point if nothing follows
    }

    mpfr_free_str(mantissa);
    return result;
}

class MandelbrotState {
private:
    // The view is changed on the UI thread and read on the GL thread, so all access to
    // these fields goes through the mutex.
    std::mutex mutex;
    mpfr_t center_x, center_y, radius;
    int iterations;

public:
    std::shared_ptr<std::vector<float>> orbitPtr = std::make_shared<std::vector<float>>(CALC_WIDTH * CALC_HEIGHT);

    // Set when the view is being torn down: stops any orbit computation in progress (and any
    // later one) so that the GL thread can exit promptly.
    std::atomic<bool> cancelled{false};

    MandelbrotState(double x, double y, double r, int iterations) {
        mpfr_init2(center_x, MPFR_DIGITS);
        mpfr_init2(center_y, MPFR_DIGITS);
        mpfr_init2(radius, MPFR_DIGITS);

        set(x, y, r, iterations);
    }

    ~MandelbrotState() {
        mpfr_clear(center_x);
        mpfr_clear(center_y);
        mpfr_clear(radius);
    }

    void set(double x, double y, double r, int iter) {
        std::lock_guard<std::mutex> lock(mutex);
        mpfr_set_d(center_x, x, MPFR_RNDN);
        mpfr_set_d(center_y, y, MPFR_RNDN);
        mpfr_set_d(radius, r, MPFR_RNDN);
        this->iterations = iter;
    }

    void set(const std::string& x_str, const std::string& y_str, const std::string& r_str, int iter) {
        std::lock_guard<std::mutex> lock(mutex);
        int result_x = mpfr_set_str(center_x, x_str.c_str(), 10, MPFR_RNDN);
        int result_y = mpfr_set_str(center_y, y_str.c_str(), 10, MPFR_RNDN);
        int result_r = mpfr_set_str(radius, r_str.c_str(), 10, MPFR_RNDN);
        this->iterations = iter;
        
        if (result_x != 0 || result_y != 0 || result_r != 0) {
            LOGI("Warning: Failed to parse some coordinate strings");
        }
    }

    void setIterations(int iter) {
        std::lock_guard<std::mutex> lock(mutex);
        iterations = iter;
    }

    void zoomIn(double dx, double dy, double factor) {
        std::lock_guard<std::mutex> lock(mutex);
        mpfr_t mx, my, offset_x, offset_y;
        mpfr_init2(mx, MPFR_DIGITS);
        mpfr_init2(my, MPFR_DIGITS);
        mpfr_init2(offset_x, MPFR_DIGITS);
        mpfr_init2(offset_y, MPFR_DIGITS);

        // Calculate the world-space position of the clicked point
        // clicked_point = center + radius * (dx, -dy)
        mpfr_mul_d(mx, radius, dx, MPFR_RNDN);
        mpfr_mul_d(my, radius, -dy, MPFR_RNDN);
        mpfr_add(offset_x, center_x, mx, MPFR_RNDN);  // clicked_x = center_x + mx
        mpfr_add(offset_y, center_y, my, MPFR_RNDN);  // clicked_y = center_y + my

        // Apply zoom to radius
        mpfr_mul_d(radius, radius, factor, MPFR_RNDN);

        // Calculate new center to keep clicked point at same screen position
        // new_center = clicked_point - new_radius * (dx, -dy)
        mpfr_mul_d(mx, radius, dx, MPFR_RNDN);
        mpfr_mul_d(my, radius, -dy, MPFR_RNDN);
        mpfr_sub(center_x, offset_x, mx, MPFR_RNDN);  // center_x = clicked_x - new_mx
        mpfr_sub(center_y, offset_y, my, MPFR_RNDN);  // center_y = clicked_y - new_my

        mpfr_clear(mx);
        mpfr_clear(my);
        mpfr_clear(offset_x);
        mpfr_clear(offset_y);
    }

    void zoomOut(double factor) {
        std::lock_guard<std::mutex> lock(mutex);
        mpfr_mul_d(radius, radius, factor, MPFR_RNDN);
    }

    // Copies the current view, so that the orbit can be computed from a consistent state
    // without holding the lock for the duration.
    void copyTo(mpfr_t x, mpfr_t y, mpfr_t r, int& iter) {
        std::lock_guard<std::mutex> lock(mutex);
        mpfr_set(x, center_x, MPFR_RNDN);
        mpfr_set(y, center_y, MPFR_RNDN);
        mpfr_set(r, radius, MPFR_RNDN);
        iter = iterations;
    }

    std::string getCenterX() {
        std::lock_guard<std::mutex> lock(mutex);
        return mpfr_to_string(&center_x);
    }

    std::string getCenterY() {
        std::lock_guard<std::mutex> lock(mutex);
        return mpfr_to_string(&center_y);
    }

    std::string getRadius() {
        std::lock_guard<std::mutex> lock(mutex);
        return mpfr_to_string(&radius);
    }
};

// Helper functions for double-double arithmetic (mantissa, exponent pairs)
struct DoubleDouble {
    double mantissa;
    double exponent;

    DoubleDouble(double m = 0.0, double e = 0.0) : mantissa(m), exponent(e) {}
};

// Returns m * 2^e. The exponents used here are always whole numbers, and wherever 2^e is a
// finite, nonzero double, ldexp gives exactly the same result as m * std::pow(2, e), much faster.
double times_pow2(double m, double e) {
    if (e >= -1074 && e <= 1023) {
        return std::ldexp(m, (int)e);
    }
    return m * std::pow(2, e);
}

// Returns m / 2^e, exactly as m / std::pow(2, e) would; see times_pow2.
double div_pow2(double m, double e) {
    if (e >= -1074 && e <= 1023) {
        return std::ldexp(m, -(int)e);
    }
    return m / std::pow(2, e);
}

constexpr double SQRT_HALF = 0.70710678118654752440;

DoubleDouble sub(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = times_pow2(am, a.exponent - ret_e);
    } else {
        bm = times_pow2(bm, b.exponent - ret_e);
    }
    return DoubleDouble(am - bm, ret_e);
}

DoubleDouble add(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = times_pow2(am, a.exponent - ret_e);
    } else {
        bm = times_pow2(bm, b.exponent - ret_e);
    }
    return DoubleDouble(am + bm, ret_e);
}

DoubleDouble mul(const DoubleDouble& a, const DoubleDouble& b) {
    double m = a.mantissa * b.mantissa;
    double e = a.exponent + b.exponent;
    if (m != 0) {
        // Normalize m to [1/sqrt(2), sqrt(2)), i.e. divide out 2^round(log2(|m|)). frexp does this
        // exactly; log2 is only used where its rounding could change the outcome: within a hair of
        // the 1/sqrt(2) boundary, or for magnitudes far outside the ones that occur here.
        int k;
        double f = std::frexp(m, &k);  // m = f * 2^k, with 0.5 <= |f| < 1
        if (std::isfinite(m) && k > -1000 && k < 1000 && std::abs(std::abs(f) - SQRT_HALF) > 1e-12) {
            if (std::abs(f) < SQRT_HALF) {
                f *= 2;
                k--;
            }
            m = f;
            e += k;
        } else {
            double logm = std::round(std::log2(std::abs(m)));
            m = m / std::pow(2, logm);
            e = e + logm;
        }
    }
    return DoubleDouble(m, e);
}

DoubleDouble maxabs(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = times_pow2(am, a.exponent - ret_e);
    } else {
        bm = times_pow2(bm, b.exponent - ret_e);
    }
    return DoubleDouble(std::max(std::abs(am), std::abs(bm)), ret_e);
}

bool gt(const DoubleDouble& a, const DoubleDouble& b) {
    double ret_e = std::max(a.exponent, b.exponent);
    double am = a.mantissa;
    double bm = b.mantissa;
    if (ret_e > a.exponent) {
        am = times_pow2(am, a.exponent - ret_e);
    } else {
        bm = times_pow2(bm, b.exponent - ret_e);
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
    double radiusExp;
};

OrbitData makeReferenceOrbit(MandelbrotState& state) {
    LOGI("makeReferenceOrbit: Starting orbit generation");

    mpfr_t x, y, cx, cy, radius;
    mpfr_init2(x, MPFR_DIGITS);
    mpfr_init2(y, MPFR_DIGITS);
    mpfr_init2(cx, MPFR_DIGITS);
    mpfr_init2(cy, MPFR_DIGITS);
    mpfr_init2(radius, MPFR_DIGITS);

    int iterations;
    state.copyTo(cx, cy, radius, iterations);

    // Initialize starting point
    mpfr_set_d(x, 0.0, MPFR_RNDN);
    mpfr_set_d(y, 0.0, MPFR_RNDN);

    std::vector<float>& orbit = *state.orbitPtr;
    std::fill(orbit.begin(), orbit.end(), -1.0);

    mpfr_t txx, txy, tyy;
    mpfr_init2(txx, MPFR_DIGITS);
    mpfr_init2(txy, MPFR_DIGITS);
    mpfr_init2(tyy, MPFR_DIGITS);

    int polylim = 0;

    DoubleDouble Bx(0, 0), By(0, 0), Cx(0, 0), Cy(0, 0), Dx(0, 0), Dy(0, 0);
    std::array<DoubleDouble, 6> poly = {Bx, By, Cx, Cy, Dx, Dy};
    bool not_failed = true;
    const mpfr_exp_t radius_exp = mpfr_get_exp(radius);

    // Each orbit entry takes 3 floats (x, y, scale exponent). Stop one entry short of the
    // buffer's capacity so that the final entry keeps its -1 fill value, which the shader
    // treats as the end of the reference orbit (and rebases), instead of reading past the end.
    const int maxIterations = std::min(iterations, (int)(orbit.size() / 3) - 1);

    int i;
    for (i = 0; i < maxIterations; i++) {
        if (state.cancelled.load(std::memory_order_relaxed)) {
            break;
        }

        // Get exponents for scaling
        mpfr_exp_t x_exponent = mpfr_get_exp(x);
        mpfr_exp_t y_exponent = mpfr_get_exp(y);
        mpfr_exp_t scale_exponent = std::max(x_exponent, y_exponent);

        if (scale_exponent < -10000) {
            scale_exponent = 0;
        }

        if (mpfr_zero_p(x) && mpfr_zero_p(y)) {
            orbit[3 * i] = 0.0;
            orbit[3 * i + 1] = 0.0;
            orbit[3 * i + 2] = 0.0;
        } else {
            mpfr_exp_t dummy_exp;
            double x_mantissa = mpfr_get_d_2exp(&dummy_exp, x, MPFR_RNDN);
            double y_mantissa = mpfr_get_d_2exp(&dummy_exp, y, MPFR_RNDN);

            orbit[3 * i] = mpfr_zero_p(x) ? 0.0 : div_pow2(x_mantissa, scale_exponent - x_exponent);
            orbit[3 * i + 1] = mpfr_zero_p(y) ? 0.0 : div_pow2(y_mantissa, scale_exponent - y_exponent);
            orbit[3 * i + 2] = scale_exponent;
        }

        // Once the series approximation has failed, poly and polylim are final, so the
        // coefficients no longer need to be computed.
        if (not_failed) {
            DoubleDouble fx(orbit[3 * i], orbit[3 * i + 2]);
            DoubleDouble fy(orbit[3 * i + 1], orbit[3 * i + 2]);

            std::array<DoubleDouble, 6> prev_poly = {Bx, By, Cx, Cy, Dx, Dy};

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

            DoubleDouble threshold = mul(DoubleDouble(1000, radius_exp), maxabs(Dx, Dy));

            if (i == 0 || gt(maxabs(Cx, Cy), threshold)) {
                poly = prev_poly;
                polylim = i;
            } else {
                not_failed = false;
            }
        }

        // Now do the Mandelbrot iteration: z = z^2 + c
        mpfr_sqr(txx, x, MPFR_RNDN);
        mpfr_mul(txy, x, y, MPFR_RNDN);
        mpfr_sqr(tyy, y, MPFR_RNDN);
        mpfr_sub(x, txx, tyy, MPFR_RNDN);
        mpfr_add(x, x, cx, MPFR_RNDN);
        mpfr_add(y, txy, txy, MPFR_RNDN);
        mpfr_add(y, y, cy, MPFR_RNDN);

        mpfr_exp_t fx_new_exp, fy_new_exp;
        double fx_new_mantissa = mpfr_get_d_2exp(&fx_new_exp, x, MPFR_RNDN);
        double fy_new_mantissa = mpfr_get_d_2exp(&fy_new_exp, y, MPFR_RNDN);
        DoubleDouble fx_new(fx_new_mantissa, fx_new_exp);
        DoubleDouble fy_new(fy_new_mantissa, fy_new_exp);

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

    LOGI("Orbit generation completed: iterations: %d, polylim: %d", i, polylim);

    std::vector<double> poly_double;
    for (const auto& p : poly) {
        poly_double.push_back(floaty(p));
    }

    LOGI("Polynomial coefficients: [%f, %f, %f, %f, %f, %f]",
         poly_double[0], poly_double[1], poly_double[2], poly_double[3], poly_double[4], poly_double[5]);

    mpfr_exp_t rexp = mpfr_get_exp(radius);
    mpfr_exp_t exp_temp;
    double r_mantissa = mpfr_get_d_2exp(&exp_temp, radius, MPFR_RNDN);
    DoubleDouble r(r_mantissa, rexp);
    mpfr_clear(radius);

    // log2(radius), computed from the double mantissa rather than with mpfr_log2: the bundled
    // MPFR isn't built thread-safe, and mpfr_log2 uses its global constant caches.
    double radiusExp = exp_temp + std::log2(r_mantissa);

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

    return { poly_double, polylim, poly_scaled, (int)poly_scale_exp.exponent, radiusExp };
}

// JNI wrapper functions
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_createState(JNIEnv *env, jobject clazz, jdouble x, jdouble y, jdouble r, jint iterations) {
    return reinterpret_cast<jlong>(new MandelbrotState(x, y, r, iterations));
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_destroyState(JNIEnv *env, jobject clazz, jlong statePtr) {
    LOGI("Cleaning up native resources.");
    delete reinterpret_cast<MandelbrotState*>(statePtr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setState(JNIEnv *env, jobject clazz, jlong statePtr, jdouble x, jdouble y, jdouble r, jint iterations) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;
    state->set(x, y, r, iterations);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setIterations(JNIEnv *env, jobject clazz, jlong statePtr, jint iterations) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;
    state->setIterations(iterations);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_zoomIn(JNIEnv *env, jobject clazz, jlong statePtr, jdouble dx, jdouble dy, jdouble factor) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;
    state->zoomIn(dx, dy, factor);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setStateStr(JNIEnv *env, jobject clazz, jlong statePtr, jstring x_str, jstring y_str, jstring r_str, jint iterations) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;

    const char* x_cstr = env->GetStringUTFChars(x_str, nullptr);
    const char* y_cstr = env->GetStringUTFChars(y_str, nullptr);
    const char* r_cstr = env->GetStringUTFChars(r_str, nullptr);
    
    state->set(
        std::string(x_cstr), std::string(y_cstr), std::string(r_cstr), iterations
    );
    
    env->ReleaseStringUTFChars(x_str, x_cstr);
    env->ReleaseStringUTFChars(y_str, y_cstr);
    env->ReleaseStringUTFChars(r_str, r_cstr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_cancel(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;
    state->cancelled = true;
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_zoomOut(JNIEnv *env, jobject clazz, jlong statePtr, jdouble factor) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return;
    state->zoomOut(factor);
}

JNIEXPORT jobject JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_generateOrbit(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return nullptr;
    OrbitData data = makeReferenceOrbit(*state);

    jclass localClass = env->FindClass("com/dmitrybrant/android/mandelbrot/OrbitResult");
    jmethodID ctor = env->GetMethodID(localClass, "<init>", "(Ljava/nio/ByteBuffer;[FIID)V");

    void* dataPtr = state->orbitPtr->data();
    jobject orbitBuffer = env->NewDirectByteBuffer(dataPtr, state->orbitPtr->size() * sizeof(float));

    jfloatArray polyArr = env->NewFloatArray(data.polyScaled.size());
    env->SetFloatArrayRegion(polyArr, 0, data.polyScaled.size(), data.polyScaled.data());

    jobject obj = env->NewObject(localClass, ctor,
                                 orbitBuffer, polyArr,
                                 (jint)data.polylim,
                                 (jint)data.polyScaleExp,
                                 (jdouble)data.radiusExp);
    return obj;
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterX(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return nullptr;
    std::string str = state->getCenterX();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterY(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return nullptr;
    std::string str = state->getCenterY();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getRadius(JNIEnv *env, jobject clazz, jlong statePtr) {
    MandelbrotState* state = reinterpret_cast<MandelbrotState*>(statePtr);
    if (!state) return nullptr;
    std::string str = state->getRadius();
    return env->NewStringUTF(str.c_str());
}

}

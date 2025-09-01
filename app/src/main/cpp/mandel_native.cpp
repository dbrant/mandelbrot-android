#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <string>
#include <algorithm>
#include <mpfr.h>
#include <android/log.h>

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
        mpfr_set_d(center_x, 0.0, MPFR_RNDN);
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
};

OrbitData makeReferenceOrbit(MandelbrotState& state) {
    mpfr_t x, y, cx, cy;
    mpfr_init2(x, 1200);
    mpfr_init2(y, 1200);
    mpfr_init2(cx, 1200);
    mpfr_init2(cy, 1200);

    mpfr_set_d(x, 0.0, MPFR_RNDN);
    mpfr_set_d(y, 0.0, MPFR_RNDN);
    mpfr_set(cx, *state.getCenterX(), MPFR_RNDN);
    mpfr_set(cy, *state.getCenterY(), MPFR_RNDN);

    std::vector<float> orbit(1024 * 1024, -1.0f);

    mpfr_t txx, txy, tyy;
    mpfr_init2(txx, 1200);
    mpfr_init2(txy, 1200);
    mpfr_init2(tyy, 1200);

    int polylim = 0;

    DoubleDouble Bx(0, 0), By(0, 0), Cx(0, 0), Cy(0, 0), Dx(0, 0), Dy(0, 0);
    std::vector<DoubleDouble> poly(6);
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

        // Store orbit data
        if (3 * i + 2 < orbit.size()) {
            mpfr_exp_t exp_temp;
            orbit[3 * i] = mpfr_get_d_2exp(&exp_temp, x, MPFR_RNDN) / std::pow(2, scale_exponent - x_exponent);
            orbit[3 * i + 1] = mpfr_get_d_2exp(&exp_temp, y, MPFR_RNDN) / std::pow(2, scale_exponent - y_exponent);
            orbit[3 * i + 2] = scale_exponent;
        }

        DoubleDouble fx(orbit[3 * i], orbit[3 * i + 2]);
        DoubleDouble fy(orbit[3 * i + 1], orbit[3 * i + 2]);

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

        Bx = add(mul(DoubleDouble(2, 0), sub(mul(fx, Bx), mul(fy, By))), DoubleDouble(1, 0));
        By = mul(DoubleDouble(2, 0), add(mul(fx, By), mul(fy, Bx)));
        Cx = sub(add(mul(DoubleDouble(2, 0), sub(mul(fx, Cx), mul(fy, Cy))), mul(Bx, Bx)), mul(By, By));
        Cy = add(mul(DoubleDouble(2, 0), add(mul(fx, Cy), mul(fy, Cx))), mul(mul(DoubleDouble(2, 0), Bx), By));
        Dx = mul(DoubleDouble(2, 0), add(sub(mul(fx, Dx), mul(fy, Dy)), sub(mul(Cx, Bx), mul(Cy, By))));
        Dy = mul(DoubleDouble(2, 0), add(add(add(mul(fx, Dy), mul(fy, Dx)), mul(Cx, By)), mul(Cy, Bx)));

        mpfr_exp_t exp_temp;
        DoubleDouble fx_new(mpfr_get_d_2exp(&exp_temp, x, MPFR_RNDN), mpfr_get_exp(x));
        DoubleDouble fy_new(mpfr_get_d_2exp(&exp_temp, y, MPFR_RNDN), mpfr_get_exp(y));

        // Check polynomial validity
        if (i == 0 || gt(maxabs(Cx, Cy), mul(DoubleDouble(1000, mpfr_get_exp(*state.getRadius())), maxabs(Dx, Dy)))) {
            if (not_failed) {
                poly = prev_poly;
                polylim = i;
            }
        } else {
            not_failed = false;
        }

        // Check escape condition
        if (gt(add(mul(fx_new, fx_new), mul(fy_new, fy_new)), DoubleDouble(400, 0))) {
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

    // Convert poly to double vector
    std::vector<double> poly_double;
    for (const auto& p : poly) {
        poly_double.push_back(floaty(p));
    }

    return {orbit, poly_double, polylim};
}

// JNI wrapper functions
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_createState(JNIEnv *env, jclass clazz) {
    return reinterpret_cast<jlong>(new MandelbrotState());
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_destroyState(JNIEnv *env, jclass clazz, jlong statePtr) {
    delete reinterpret_cast<MandelbrotState *>(statePtr);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_setState(JNIEnv *env, jclass clazz, jlong statePtr, jdouble x,
                                               jdouble y, jdouble r) {
    reinterpret_cast<MandelbrotState *>(statePtr)->set(x, y, r);
}

JNIEXPORT void JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_updateState(JNIEnv *env, jclass clazz, jlong statePtr,
                                                  jdouble dx, jdouble dy) {
    reinterpret_cast<MandelbrotState *>(statePtr)->update(dx, dy);
}

JNIEXPORT jfloatArray JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_generateOrbit(JNIEnv *env, jclass clazz, jlong statePtr) {
    MandelbrotState *state = reinterpret_cast<MandelbrotState *>(statePtr);
    OrbitData data = makeReferenceOrbit(*state);

    jfloatArray result = env->NewFloatArray(data.orbit.size());
    env->SetFloatArrayRegion(result, 0, data.orbit.size(), data.orbit.data());

    return result;
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterX(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState *>(statePtr)->getCenterXString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getCenterY(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState *>(statePtr)->getCenterYString();
    return env->NewStringUTF(str.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_dmitrybrant_android_mandelbrot_MandelbrotNative_getRadius(JNIEnv *env, jclass clazz, jlong statePtr) {
    std::string str = reinterpret_cast<MandelbrotState *>(statePtr)->getRadiusString();
    return env->NewStringUTF(str.c_str());
}

}
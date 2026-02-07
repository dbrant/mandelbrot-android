#include <metal_stdlib>
using namespace metal;

// Shared uniform struct (must match Swift-side MandelbrotUniforms layout)
struct MandelbrotUniforms {
    float4x4 projectionMatrix;
    float4x4 modelViewMatrix;
    float4 uState;
    float4 poly1;
    float4 poly2;
};

// --- Mandelbrot shader ---

struct MandelbrotVertexOut {
    float4 position [[position]];
    float2 delta;
};

vertex MandelbrotVertexOut mandelbrot_vertex(
    const device float2 *vertices [[buffer(0)]],
    constant MandelbrotUniforms &uniforms [[buffer(1)]],
    uint vid [[vertex_id]]
) {
    MandelbrotVertexOut out;
    float4 pos = float4(vertices[vid], 0.0, 1.0);
    out.position = uniforms.projectionMatrix * uniforms.modelViewMatrix * pos;
    out.delta = vertices[vid];
    return out;
}

/*
 * Adapted from https://github.com/HastingsGreer/mandeljs
 */

float get_orbit_x(texture2d<float, access::read> sequence, int i) {
    i = i * 3;
    return sequence.read(uint2(i % 1024, i / 1024)).r;
}

float get_orbit_y(texture2d<float, access::read> sequence, int i) {
    i = i * 3 + 1;
    return sequence.read(uint2(i % 1024, i / 1024)).r;
}

float get_orbit_scale(texture2d<float, access::read> sequence, int i) {
    i = i * 3 + 2;
    return sequence.read(uint2(i % 1024, i / 1024)).r;
}

fragment float4 mandelbrot_fragment(
    MandelbrotVertexOut in [[stage_in]],
    texture2d<float, access::read> sequence [[texture(0)]],
    constant MandelbrotUniforms &uniforms [[buffer(0)]]
) {
    int q = int(uniforms.uState[2]) - 1;
    int cq = q;
    q = q + int(uniforms.poly2[3]);
    float S = exp2(float(q));
    float dcx = in.delta[0];
    float dcy = in.delta[1];
    float x;
    float y;
    float dcx2mdcy2 = (dcx * dcx - dcy * dcy);
    float dcxdcy2 = (2.0 * dcx * dcy);

    float dx = uniforms.poly1[0] * dcx - uniforms.poly1[1] * dcy + uniforms.poly1[2] * dcx2mdcy2 - uniforms.poly1[3] * dcxdcy2;
    float dy = uniforms.poly1[0] * dcy + uniforms.poly1[1] * dcx + uniforms.poly1[2] * dcxdcy2 + uniforms.poly1[3] * dcx2mdcy2;

    float f1, tx, fx, fy, fx2, fy2, dx2, dy2, S2, scaleExp2;
    float os, unS, unsDy, twodx, twody;

    int k = int(uniforms.poly2[2]);

    int j = k;
    x = get_orbit_x(sequence, k);
    y = get_orbit_y(sequence, k);

    for (int i = k; float(i) < uniforms.uState[3]; i++) {
        j++;
        k++;
        os = get_orbit_scale(sequence, k - 1);

        f1 = exp2(float(-q + cq - int(os)));
        dcx = in.delta[0] * f1;
        dcy = in.delta[1] * f1;
        unS = exp2(float(q) - os);
        unS = isinf(unS) ? 0.0 : unS;

        twodx = 2.0 * dx;
        twody = 2.0 * dy;
        unsDy = unS * dy;
        tx = twodx * x - twody * y + unS * dx * dx - unsDy * dy + dcx;
        dy = twody * x + twodx * y + unsDy * twodx + dcy;
        dx = tx;

        q += int(os);
        S = exp2(float(q));

        x = get_orbit_x(sequence, k);
        y = get_orbit_y(sequence, k);
        scaleExp2 = exp2(get_orbit_scale(sequence, k));
        fx = x * scaleExp2 + S * dx;
        fy = y * scaleExp2 + S * dy;
        fx2 = fx * fx;
        fy2 = fy * fy;
        dx2 = dx * dx;
        dy2 = dy * dy;
        S2 = S * S;

        if (fx2 + fy2 > 4.0) {
            break;
        }

        if (dx2 + dy2 > 1000000.0) {
            dx = dx / 2.0;
            dy = dy / 2.0;
            q++;
            S = exp2(float(q));
            f1 = exp2(float(-q + cq));
            dcx = in.delta[0] * f1;
            dcy = in.delta[1] * f1;
        }

        if (fx2 + fy2 < S2 * dx2 + S2 * dy2 || (x == -1.0 && y == -1.0)) {
            dx = fx;
            dy = fy;
            q = 0;
            S = 1.0;
            dcx = in.delta[0];
            dcy = in.delta[1];
            k = 0;
            x = get_orbit_x(sequence, 0);
            y = get_orbit_y(sequence, 0);
        }
    }
    float c = (uniforms.uState[3] - float(j)) / uniforms.uState[1];
    return float4(float3(cos(c), cos(1.1214 * c), cos(0.8 * c)) / -2.0 + 0.5, 1.0);
}

// --- Framebuffer blit shader ---

struct BlitVertexIn {
    float2 position;
    float2 texCoord;
};

struct BlitVertexOut {
    float4 position [[position]];
    float2 texCoord;
};

vertex BlitVertexOut framebuf_vertex(
    const device BlitVertexIn *vertices [[buffer(0)]],
    uint vid [[vertex_id]]
) {
    BlitVertexOut out;
    out.position = float4(vertices[vid].position, 0.0, 1.0);
    out.texCoord = vertices[vid].texCoord;
    return out;
}

fragment float4 framebuf_fragment(
    BlitVertexOut in [[stage_in]],
    texture2d<float> uTexture [[texture(0)]]
) {
    constexpr sampler texSampler(mag_filter::linear, min_filter::linear,
                                  address::clamp_to_edge);
    return uTexture.sample(texSampler, in.texCoord);
}

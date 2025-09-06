#version 300 es
precision highp float;
in highp vec2 delta;
out vec4 fragColor;
uniform vec4 uState;
uniform vec4 poly1;
uniform vec4 poly2;
uniform sampler2D sequence;

float get_orbit_x(int i) {
  i = i * 3;
  return texelFetch(sequence, ivec2(i % 1024, i / 1024), 0)[0];
}

float get_orbit_y(int i) {
  i = i * 3 + 1;
  return texelFetch(sequence, ivec2(i % 1024, i / 1024), 0)[0];
}

float get_orbit_scale(int i) {
  i = i * 3 + 2;
  return texelFetch(sequence, ivec2(i % 1024, i / 1024), 0)[0];
}

void main() {
  int q = int(uState[2]) - 1;
  int cq = q;
  q = q + int(poly2[3]);
  float S = exp2(float(q));
  float dcx = delta[0];
  float dcy = delta[1];
  float x;
  float y;
  float sqrx = (dcx * dcx - dcy * dcy);
  float sqry = (2. * dcx * dcy);

  float dx = poly1[0] * dcx - poly1[1] * dcy + poly1[2] * sqrx - poly1[3] * sqry;
  float dy = poly1[0] * dcy + poly1[1] * dcx + poly1[2] * sqry + poly1[3] * sqrx;

  float f1, tx, fx, fy, fx2, fy2, dx2, dy2, S2, scaleExp2;
  float unsDy, twodx, twody;

  int k = int(poly2[2]);

  int j = k;
  x = get_orbit_x(k);
  y = get_orbit_y(k);

  for (int i = k; float(i) < uState[3]; i++){
    j += 1;
    k += 1;
    float os = get_orbit_scale(k - 1);

    f1 = exp2(float(-q + cq - int(os)));
    dcx = delta[0] * f1;
    dcy = delta[1] * f1;
    float unS = exp2(float(q) - os);

    if (isinf(unS)) {
      unS = 0.;
    }

    twodx = 2. * dx;
    twody = 2. * dy;
    unsDy = unS * dy;
    tx = twodx * x - twody * y + unS * dx * dx - unsDy * dy + dcx;
    dy = twody * x + twodx * y + unsDy * twodx + dcy;
    dx = tx;

    q = q + int(os);
    S = exp2(float(q));

    x = get_orbit_x(k);
    y = get_orbit_y(k);
    scaleExp2 = exp2(get_orbit_scale(k));
    fx = x * scaleExp2 + S * dx;
    fy = y * scaleExp2 + S * dy;
    fx2 = fx * fx;
    fy2 = fy * fy;
    dx2 = dx * dx;
    dy2 = dy * dy;
    S2 = S * S;

    if (fx2 + fy2 > 4.){
      break;
    }

    if (dx2 + dy2 > 1000000.) {
      dx = dx / 2.;
      dy = dy / 2.;
      q = q + 1;
      S = exp2(float(q));
      f1 = exp2(float(-q + cq));
      dcx = delta[0] * f1;
      dcy = delta[1] * f1;
    }

    if (fx2 + fy2 < S2 * dx2 + S2 * dy2 || (x == -1. && y == -1.)) {
      dx  = fx;
      dy = fy;
      q = 0;
      S = 1.0;
      dcx = delta[0];
      dcy = delta[1];
      k = 0;
      x = get_orbit_x(0);
      y = get_orbit_y(0);
    }
  }
  float c = (uState[3] - float(j)) / uState[1];
  fragColor = vec4(vec3(cos(c), cos(1.1214 * c) , cos(.8 * c)) / -2. + .5, 1.);
}

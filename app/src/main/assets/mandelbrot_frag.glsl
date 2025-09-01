#version 300 es
precision highp float;
in highp vec2 vDelta;
out vec4 fragColor;
uniform vec4 uState;    // [unused, cmapscale, qstart+1, iterations] like JS  :contentReference[oaicite:7]{index=7}
uniform vec4 poly1;     // [p0r, p0i, p1r, p1i]
uniform vec4 poly2;     // [p2r, p2i, polylim, poly_scale_exp]
uniform sampler2D sequence;

float get_orbit_x(int i){ i = i*3; int row=i/1024; return texelFetch(sequence, ivec2(i%1024,row), 0).r; }
float get_orbit_y(int i){ i = i*3+1; int row=i/1024; return texelFetch(sequence, ivec2(i%1024,row), 0).r; }
float get_orbit_scale(int i){ i=i*3+2; int row=i/1024; return texelFetch(sequence, ivec2(i%1024,row), 0).r; }

void main() {
  int q = int(uState[2]) - 1;
  int cq = q;
  q = q + int(poly2[3]);
  float S = pow(2.0, float(q));
  float dcx = vDelta.x;
  float dcy = vDelta.y;

  float sqrx = (dcx*dcx - dcy*dcy);
  float sqry = (2.0*dcx*dcy);

  float dx = poly1[0]*dcx - poly1[1]*dcy + poly1[2]*sqrx - poly1[3]*sqry;
  float dy = poly1[0]*dcy + poly1[1]*dcx + poly1[2]*sqry + poly1[3]*sqrx;

  int k = int(poly2[2]);
  int j = k;
  float x = get_orbit_x(k);
  float y = get_orbit_y(k);

  for (int i=k; float(i) < uState[3]; i++){
    j += 1; k += 1;
    float os = get_orbit_scale(k-1);
    dcx = vDelta.x * pow(2.0, float(-q + cq - int(os)));
    dcy = vDelta.y * pow(2.0, float(-q + cq - int(os)));
    float unS = pow(2.0, float(q) - get_orbit_scale(k-1));
    if (isinf(unS)) { unS = 0.0; }

    float tx = 2.0*x*dx - 2.0*y*dy + unS*dx*dx - unS*dy*dy + dcx;
    dy = 2.0*x*dy + 2.0*y*dx + unS*2.0*dx*dy + dcy;
    dx = tx;

    q += int(os);
    S = pow(2.0, float(q));
    x = get_orbit_x(k);
    y = get_orbit_y(k);
    float fx = x * pow(2.0, get_orbit_scale(k)) + S * dx;
    float fy = y * pow(2.0, get_orbit_scale(k)) + S * dy;
    if (fx*fx + fy*fy > 4.0){ break; }

    if (dx*dx + dy*dy > 1000000.0) {
      dx *= 0.5; dy *= 0.5; q += 1; S = pow(2.0, float(q));
      dcx = vDelta.x * pow(2.0, float(-q + cq));
      dcy = vDelta.y * pow(2.0, float(-q + cq));
    }
    if (fx*fx + fy*fy < S*S*dx*dx + S*S*dy*dy || (x == -1.0 && y == -1.0)) {
      dx = fx; dy = fy; q = 0; S = 1.0;
      dcx = vDelta.x; dcy = vDelta.y; k = 0;
      x = get_orbit_x(0); y = get_orbit_y(0);
    }
  }
  float c = (uState[3] - float(j)) / uState[1]; // cmapscale like JS
  fragColor = vec4(vec3(cos(c), cos(1.1214*c), cos(0.8*c)) * -0.5 + 0.5, 1.0);
}

#version 300 es
in vec4 aVertexPosition;
uniform mat4 uModelViewMatrix;
uniform mat4 uProjectionMatrix;
out highp vec2 vDelta;
void main() {
  gl_Position = uProjectionMatrix * uModelViewMatrix * aVertexPosition;
  vDelta = aVertexPosition.xy; // same as JS 'delta'  :contentReference[oaicite:5]{index=5}
}

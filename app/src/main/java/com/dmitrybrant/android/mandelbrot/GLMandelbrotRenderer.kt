package com.dmitrybrant.android.mandelbrot

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * OpenGL ES renderer that uses shaders to render the Mandelbrot set,
 * directly porting the WebGL implementation from main.js
 */
class GLMandelbrotRenderer : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "GLMandelbrotRenderer"
        private const val ORBIT_SIZE = 1024 * 1024
    }
    
    // Shader sources - direct port from main.js
    private val vertexShaderSource = """
        #version 300 es
        in vec4 aVertexPosition;
        uniform mat4 uModelViewMatrix;
        uniform mat4 uProjectionMatrix;
        out highp vec2 delta;
        void main() {
            gl_Position = uProjectionMatrix * uModelViewMatrix * aVertexPosition;
            delta = vec2(aVertexPosition[0], aVertexPosition[1]);
        }
    """.trimIndent()
    
    private val fragmentShaderSource = """
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
            int row = i / 1024;
            return texelFetch(sequence, ivec2(i % 1024, row), 0)[0];
        }
        
        float get_orbit_y(int i) {
            i = i * 3 + 1;
            int row = i / 1024;
            return texelFetch(sequence, ivec2(i % 1024, row), 0)[0];
        }
        
        float get_orbit_scale(int i) {
            i = i * 3 + 2;
            int row = i / 1024;
            return texelFetch(sequence, ivec2(i % 1024, row), 0)[0];
        }
        
        void main() {
            int q = int(uState[2]) - 1;
            int cq = q;
            q = q + int(poly2[3]);
            float S = pow(2., float(q));
            float dcx = delta[0];
            float dcy = delta[1];
            float x;
            float y;
            
            // Series approximation: dx + dyi = (p0 + p1 i) * (dcx, dcy) + (p2 + p3i) * (dcx + dcy * i) * (dcx + dcy * i)
            float sqrx = (dcx * dcx - dcy * dcy);
            float sqry = (2. * dcx * dcy);
            
            float cux = (dcx * sqrx - dcy * sqry);
            float cuy = (dcx * sqry + dcy * sqrx);
            float dx = poly1[0] * dcx - poly1[1] * dcy + poly1[2] * sqrx - poly1[3] * sqry;
            float dy = poly1[0] * dcy + poly1[1] * dcx + poly1[2] * sqry + poly1[3] * sqrx;
            
            int k = int(poly2[2]);
            int j = k;
            x = get_orbit_x(k);
            y = get_orbit_y(k);
            
            for (int i = k; float(i) < uState[3]; i++) {
                j += 1;
                k += 1;
                float os = get_orbit_scale(k - 1);
                dcx = delta[0] * pow(2., float(-q + cq - int(os)));
                dcy = delta[1] * pow(2., float(-q + cq - int(os)));
                float unS = pow(2., float(q) - get_orbit_scale(k - 1));
                
                if (isinf(unS)) {
                    unS = 0.;
                }
                
                float tx = 2. * x * dx - 2. * y * dy + unS * dx * dx - unS * dy * dy + dcx;
                dy = 2. * x * dy + 2. * y * dx + unS * 2. * dx * dy + dcy;
                dx = tx;
                
                q = q + int(os);
                S = pow(2., float(q));
                
                x = get_orbit_x(k);
                y = get_orbit_y(k);
                float fx = x * pow(2., get_orbit_scale(k)) + S * dx;
                float fy = y * pow(2., get_orbit_scale(k)) + S * dy;
                
                if (fx * fx + fy * fy > 4.) {
                    break;
                }
                
                // Adaptive scaling
                if (dx * dx + dy * dy > 1000000.) {
                    dx = dx / 2.;
                    dy = dy / 2.;
                    q = q + 1;
                    S = pow(2., float(q));
                    dcx = delta[0] * pow(2., float(-q + cq));
                    dcy = delta[1] * pow(2., float(-q + cq));
                }
                
                // Glitch detection
                if (fx * fx + fy * fy < S * S * dx * dx + S * S * dy * dy || (x == -1. && y == -1.)) {
                    dx = fx;
                    dy = fy;
                    q = 0;
                    S = pow(2., float(q));
                    dcx = delta[0] * pow(2., float(-q + cq));
                    dcy = delta[1] * pow(2., float(-q + cq));
                    k = 0;
                    x = get_orbit_x(0);
                    y = get_orbit_y(0);
                }
            }
            
            // Color calculation
            float c = (uState[3] - float(j)) / uState[1];
            fragColor = vec4(vec3(cos(c), cos(1.1214 * c), cos(.8 * c)) / -2. + .5, 1.);
        }
    """.trimIndent()
    
    // OpenGL objects
    private var shaderProgram = 0
    private var vertexBuffer: FloatBuffer? = null
    private var orbitTexture = 0
    
    // Uniform locations
    private var projectionMatrixLocation = 0
    private var modelViewMatrixLocation = 0
    private var stateLocation = 0
    private var poly1Location = 0
    private var poly2Location = 0
    private var sequenceLocation = 0
    
    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    
    // Mandelbrot state
    private var centerX = BigDecimal("-0.5")
    private var centerY = BigDecimal("0.0")
    private var radius = BigDecimal("2.0")
    private var iterations = 1000
    private var cmapScale = 20.0f
    
    // Reference orbit and polynomial data
    private val orbitCalculator = ReferenceOrbitCalculator()
    private var orbitData = FloatArray(ORBIT_SIZE)
    private var orbitLength = 0
    private var poly1 = floatArrayOf(0f, 0f, 0f, 0f)
    private var poly2 = floatArrayOf(0f, 0f, 0f, 0f)
    
    init {
        // Initialize vertex buffer for full-screen quad
        val vertices = floatArrayOf(
            1.0f, 1.0f,    // top right
            -1.0f, 1.0f,   // top left  
            1.0f, -1.0f,   // bottom right
            -1.0f, -1.0f   // bottom left
        )
        
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer?.position(0)
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // Create shader program
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)
        
        shaderProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(shaderProgram, vertexShader)
        GLES30.glAttachShader(shaderProgram, fragmentShader)
        GLES30.glLinkProgram(shaderProgram)
        
        // Get uniform locations
        projectionMatrixLocation = GLES30.glGetUniformLocation(shaderProgram, "uProjectionMatrix")
        modelViewMatrixLocation = GLES30.glGetUniformLocation(shaderProgram, "uModelViewMatrix")
        stateLocation = GLES30.glGetUniformLocation(shaderProgram, "uState")
        poly1Location = GLES30.glGetUniformLocation(shaderProgram, "poly1")
        poly2Location = GLES30.glGetUniformLocation(shaderProgram, "poly2")
        sequenceLocation = GLES30.glGetUniformLocation(shaderProgram, "sequence")
        
        // Create orbit texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        orbitTexture = textures[0]
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, orbitTexture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        
        Log.d(TAG, "OpenGL ES shader program initialized")
        
        // Calculate initial reference orbit
        updateReferenceOrbit()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        
        // Set up orthographic projection (identity for full-screen quad)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(modelViewMatrix, 0)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        
        GLES30.glUseProgram(shaderProgram)
        
        // Set matrices
        GLES30.glUniformMatrix4fv(projectionMatrixLocation, 1, false, projectionMatrix, 0)
        GLES30.glUniformMatrix4fv(modelViewMatrixLocation, 1, false, modelViewMatrix, 0)
        
        // Set uniforms
        val state = floatArrayOf(
            centerX.toFloat(),
            cmapScale,
            (1 + getExp(radius)).toFloat(),
            iterations.toFloat()
        )
        
        GLES30.glUniform4fv(stateLocation, 1, state, 0)
        GLES30.glUniform4fv(poly1Location, 1, poly1, 0)
        GLES30.glUniform4fv(poly2Location, 1, poly2, 0)
        
        // Bind orbit texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, orbitTexture)
        GLES30.glUniform1i(sequenceLocation, 0)
        
        // Draw full-screen quad
        val positionHandle = GLES30.glGetAttribLocation(shaderProgram, "aVertexPosition")
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        
        GLES30.glDisableVertexAttribArray(positionHandle)
    }
    
    /**
     * Update Mandelbrot parameters and recalculate reference orbit
     */
    fun setParameters(centerX: BigDecimal, centerY: BigDecimal, radius: BigDecimal, iterations: Int) {
        this.centerX = centerX
        this.centerY = centerY
        this.radius = radius
        this.iterations = iterations
        
        Log.d(TAG, "Setting parameters: center=($centerX, $centerY), radius=$radius, iterations=$iterations")
        updateReferenceOrbit()
    }
    
    private fun updateReferenceOrbit() {
        // Calculate reference orbit using the same algorithm as main.js
        val (orbit, poly1Array, poly2Array, length) = orbitCalculator.makeReferenceOrbit(
            centerX, centerY, radius, iterations
        )
        
        orbitData = orbit
        orbitLength = length
        poly1 = poly1Array
        poly2 = poly2Array
        
        // Update orbit texture
        if (orbitTexture != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, orbitTexture)
            
            // Convert orbit data to texture format (1024 width, dynamic height)
            val textureWidth = 1024
            val textureHeight = (orbitData.size + textureWidth - 1) / textureWidth
            val textureData = FloatArray(textureWidth * textureHeight)
            System.arraycopy(orbitData, 0, textureData, 0, minOf(orbitData.size, textureData.size))
            
            val buffer = ByteBuffer.allocateDirect(textureData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureData)
            buffer.position(0)
            
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R32F, textureWidth, textureHeight,
                0, GLES30.GL_RED, GLES30.GL_FLOAT, buffer
            )
            
            Log.d(TAG, "Updated orbit texture: ${textureWidth}x${textureHeight}, orbit length: $orbitLength")
        }
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation failed: $error")
            GLES30.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    private fun getExp(value: BigDecimal): Int {
        if (value.compareTo(BigDecimal.ZERO) == 0) return 0
        return (ln(value.abs().toDouble()) / ln(2.0)).roundToInt()
    }
}
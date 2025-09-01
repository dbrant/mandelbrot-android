package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES30.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

class MandelbrotRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val mandelbrotState = MandelbrotNative.MandelbrotState()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var stateChangeCallback: ((String) -> Unit)? = null


    // OpenGL objects
    private var shaderProgram: Int = 0
    private var vertexBuffer: Int = 0
    private var orbitTexture: Int = 0

    // Uniform locations
    private var uProjectionMatrix: Int = 0
    private var uModelViewMatrix: Int = 0
    private var uState: Int = 0
    private var uPoly1: Int = 0
    private var uPoly2: Int = 0
    private var aVertexPosition: Int = 0

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)

    // Vertex data for full-screen quad
    private val vertices = floatArrayOf(
        1.0f,  1.0f,   // top right
        -1.0f, 1.0f,   // top left
        1.0f, -1.0f,   // bottom right
        -1.0f, -1.0f   // bottom left
    )

    private val vertexBufferData: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertices)
            position(0)
        }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        drawScene(mandelbrotState, surfaceWidth, surfaceHeight)

        // Update UI with current state
        stateChangeCallback?.invoke(mandelbrotState.stateString)
    }

    fun handleTouch(x: Float, y: Float, width: Int, height: Int) {
        //handleTouch(x, y, width, height, mandelbrotState)
    }

    fun setIterations(iterations: Int) {
        mandelbrotState.iterations = iterations
    }

    fun setCmapScale(scale: Double) {
        mandelbrotState.cmapscale = scale
    }

    fun reset() {
        mandelbrotState.reset()
    }

    fun zoomOut() {
        mandelbrotState.zoomOut()
    }

    fun setFromCoordinateString(coords: String): Boolean {
        return try {
            // Parse coordinate string format: "re=...; im=...; r=...; iterations=..."
            val parts = coords.split(";").map { it.trim() }
            var re: String? = null
            var im: String? = null
            var r: String? = null
            var iterations: Int? = null

            for (part in parts) {
                val keyValue = part.split("=")
                if (keyValue.size == 2) {
                    when (keyValue[0].trim()) {
                        "re" -> re = keyValue[1].trim()
                        "im" -> im = keyValue[1].trim()
                        "r" -> r = keyValue[1].trim()
                        "iterations" -> iterations = keyValue[1].trim().toIntOrNull()
                    }
                }
            }

            if (re != null && im != null && r != null) {
                mandelbrotState.setFromStrings(re, im, r)
                iterations?.let { mandelbrotState.iterations = it }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setStateChangeCallback(callback: (String) -> Unit) {
        stateChangeCallback = callback
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Create and compile shaders
        val vertexShader = loadShader(GL_VERTEX_SHADER, readAsset("mandelbrot_vert.glsl"))
        val fragmentShader = loadShader(GL_FRAGMENT_SHADER, readAsset("mandelbrot_frag.glsl"))

        // Create shader program
        shaderProgram = glCreateProgram().also { program ->
            glAttachShader(program, vertexShader)
            glAttachShader(program, fragmentShader)
            glLinkProgram(program)

            // Check for linking errors
            val linkStatus = IntArray(1)
            glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = glGetProgramInfoLog(program)
                glDeleteProgram(program)
                throw RuntimeException("Error linking shader program: $error")
            }
        }

        // Get uniform and attribute locations
        uProjectionMatrix = glGetUniformLocation(shaderProgram, "uProjectionMatrix")
        uModelViewMatrix = glGetUniformLocation(shaderProgram, "uModelViewMatrix")
        uState = glGetUniformLocation(shaderProgram, "uState")
        uPoly1 = glGetUniformLocation(shaderProgram, "poly1")
        uPoly2 = glGetUniformLocation(shaderProgram, "poly2")
        aVertexPosition = glGetAttribLocation(shaderProgram, "aVertexPosition")

        // Create vertex buffer
        val buffers = IntArray(1)
        glGenBuffers(1, buffers, 0)
        vertexBuffer = buffers[0]

        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, vertexBufferData, GL_STATIC_DRAW)

        // Create texture for orbit data
        val textures = IntArray(1)
        glGenTextures(1, textures, 0)
        orbitTexture = textures[0]

        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

        // Clean up shaders (they're now linked into the program)
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return glCreateShader(type).also { shader ->
            glShaderSource(shader, shaderCode)
            glCompileShader(shader)

            // Check for compilation errors
            val compileStatus = IntArray(1)
            glGetShaderiv(shader, GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = glGetShaderInfoLog(shader)
                glDeleteShader(shader)
                throw RuntimeException("Error compiling shader: $error")
            }
        }
    }

    fun drawScene(mandelbrotState: MandelbrotNative.MandelbrotState, width: Int, height: Int) {


        //val foo = MandelbrotNative.testBasicFunctionality()
        //println("Test result: $foo")


        // Generate reference orbit and polynomial data
        val orbitData = mandelbrotState.generateOrbit()
        val polyCoefficients = mandelbrotState.polynomialCoefficients
        val polyLimit = mandelbrotState.polynomialLimit
        val polyScaleExp = mandelbrotState.polynomialScaleExp

        // Find minimum orbit scale for debugging
        var minVal = 2.0f
        for (i in 2 until orbitData.size step 3) {
            if (orbitData[i] != -1.0f) {
                minVal = minOf(minVal, abs(orbitData[i]))
            }
        }
        println("Smallest orbit bit: $minVal")

        // Upload orbit data to texture
        val orbitBuffer = ByteBuffer.allocateDirect(orbitData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(orbitData)
                position(0)
            }

        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 1024, 1024, 0, GL_RED, GL_FLOAT, orbitBuffer)

        // Set up viewport and clear
        glViewport(0, 0, width, height)
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClearDepthf(1.0f)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Set up matrices
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(modelViewMatrix, 0)

        // Bind vertex buffer and set up attributes
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glVertexAttribPointer(aVertexPosition, 2, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(aVertexPosition)

        // Use shader program
        glUseProgram(shaderProgram)

        // Set matrices
        glUniformMatrix4fv(uProjectionMatrix, 1, false, projectionMatrix, 0)
        glUniformMatrix4fv(uModelViewMatrix, 1, false, modelViewMatrix, 0)

        // Calculate state parameters
        val radiusExp = mandelbrotState.radiusExponent
        val centerX = 0.0f // This represents the center offset in the shader coordinate system

        println("Radius exponent: $radiusExp")

        // Set state uniform (center_x, cmapscale, radius_exp + 1, iterations)
        glUniform4f(
            uState, centerX, mandelbrotState.cmapscale.toFloat(),
            (1 + radiusExp).toFloat(), mandelbrotState.iterations.toFloat()
        )

        println("Polynomial coefficients: ${polyCoefficients.contentToString()}")
        println("Polynomial limit: $polyLimit, Scale exp: $polyScaleExp")

        // Set polynomial uniforms
        if (polyCoefficients.size >= 6) {
            glUniform4f(
                uPoly1, polyCoefficients[0], polyCoefficients[1],
                polyCoefficients[2], polyCoefficients[3]
            )
            glUniform4f(
                uPoly2, polyCoefficients[4], polyCoefficients[5],
                polyLimit.toFloat(), polyScaleExp.toFloat()
            )
        } else {
            // Fallback to identity coefficients
            glUniform4f(uPoly1, 1.0f, 0.0f, 0.0f, 0.0f)
            glUniform4f(uPoly2, 0.0f, 0.0f, 0.0f, 0.0f)
        }

        // Bind texture
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glUniform1i(glGetUniformLocation(shaderProgram, "sequence"), 0)

        // Draw the quad
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        // Draw the quad
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        // Clean up
        glDisableVertexAttribArray(aVertexPosition)
    }

    fun cleanup() {
        if (shaderProgram != 0) {
            glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }

        if (vertexBuffer != 0) {
            glDeleteBuffers(1, intArrayOf(vertexBuffer), 0)
            vertexBuffer = 0
        }

        if (orbitTexture != 0) {
            glDeleteTextures(1, intArrayOf(orbitTexture), 0)
            orbitTexture = 0
        }

        mandelbrotState.destroy()
    }

    private fun getRadiusExponent(radiusString: String): Int {
        // Parse the high-precision radius string to extract exponent
        // This is a simplified version - you might need more sophisticated parsing
        return try {
            val radius = radiusString.toDouble()
            if (radius == 0.0) 0 else floor(log2(abs(radius))).toInt()
        } catch (e: NumberFormatException) {
            // For very high precision numbers, you'd need to parse the string format
            // that MPFR outputs (e.g., scientific notation)
            0
        }
    }

    private fun readAsset(name: String) =
        context.assets.open(name).bufferedReader().use { it.readText() }
}


// Extension function for MandelbrotNative.MandelbrotState
fun MandelbrotNative.MandelbrotState.setFromStrings(re: String, im: String, r: String) {
    // This would need to be implemented in the native code
    // For now, try to parse as doubles for approximate positioning
    try {
        val reDouble = re.toDouble()
        val imDouble = im.toDouble()
        val rDouble = r.toDouble()
        set(reDouble, imDouble, rDouble)
    } catch (e: NumberFormatException) {
        // For high-precision strings, we'd need a native method
        // that can handle MPFR string format directly
        println("Could not parse high-precision coordinates: $e")
    }
}

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

class MandelbrotRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var mandelbrotState: MandelbrotNative.MandelbrotState? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var shaderProgram: Int = 0
    private var vertexBuffer: Int = 0
    private var orbitTexture: Int = 0

    private var uProjectionMatrix: Int = 0
    private var uModelViewMatrix: Int = 0
    private var uState: Int = 0
    private var uPoly1: Int = 0
    private var uPoly2: Int = 0
    private var aVertexPosition: Int = 0

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
        
        glViewport(0, 0, width, height)
    }

    fun handleTouch(x: Float, y: Float, width: Int, height: Int) {
        // Convert screen coordinates to normalized coordinates (-1 to 1)
        val normalizedX = (x / (width / 2.0f) - 1.0f).toDouble()
        val normalizedY = (y / (height / 2.0f) - 1.0f).toDouble()
        mandelbrotState?.update(normalizedX, normalizedY)
    }

    fun setIterations(iterations: Int) {
        mandelbrotState?.iterations = iterations
    }

    fun setCmapScale(scale: Double) {
        mandelbrotState?.cmapscale = scale
    }

    fun reset() {
        mandelbrotState?.reset()
    }

    fun zoomOut() {
        mandelbrotState?.zoomOut()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (mandelbrotState == null) {
            mandelbrotState = MandelbrotNative.MandelbrotState()
        }

        val vertexShader = loadShader(GL_VERTEX_SHADER, readAsset("mandelbrot_vert.glsl"))
        val fragmentShader = loadShader(GL_FRAGMENT_SHADER, readAsset("mandelbrot_frag.glsl"))

        shaderProgram = glCreateProgram().also { program ->
            glAttachShader(program, vertexShader)
            glAttachShader(program, fragmentShader)
            glLinkProgram(program)

            val linkStatus = IntArray(1)
            glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = glGetProgramInfoLog(program)
                glDeleteProgram(program)
                throw RuntimeException("Error linking shader program: $error")
            }
        }

        uProjectionMatrix = glGetUniformLocation(shaderProgram, "uProjectionMatrix")
        uModelViewMatrix = glGetUniformLocation(shaderProgram, "uModelViewMatrix")
        uState = glGetUniformLocation(shaderProgram, "uState")
        uPoly1 = glGetUniformLocation(shaderProgram, "poly1")
        uPoly2 = glGetUniformLocation(shaderProgram, "poly2")
        aVertexPosition = glGetAttribLocation(shaderProgram, "aVertexPosition")

        // Create and populate vertex buffer
        val buffers = IntArray(1)
        glGenBuffers(1, buffers, 0)
        vertexBuffer = buffers[0]
        
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, vertexBufferData, GL_STATIC_DRAW)

        val textures = IntArray(1)
        glGenTextures(1, textures, 0)
        orbitTexture = textures[0]

        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

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

    override fun onDrawFrame(gl: GL10?) {
        if (mandelbrotState == null) {
            return
        }
        val orbitResult = mandelbrotState!!.generateOrbit()

        val orbitBuffer = orbitResult.orbit
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 1024, 1024, 0, GL_RED, GL_FLOAT, orbitBuffer)

        glViewport(0, 0, surfaceWidth, surfaceHeight)
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        glClearDepthf(1.0f)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Set up projection matrix for center-crop behavior
        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        if (aspect > 1.0f) {
            // Wider than square - expand horizontally for center-crop
            Matrix.orthoM(projectionMatrix, 0, -aspect, aspect, -1f, 1f, -1f, 1f)
        } else {
            // Taller than square - expand vertically for center-crop
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f/aspect, 1f/aspect, -1f, 1f)
        }
        Matrix.setIdentityM(modelViewMatrix, 0)

        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glVertexAttribPointer(aVertexPosition, 2, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(aVertexPosition)

        glUseProgram(shaderProgram)

        glUniformMatrix4fv(uProjectionMatrix, 1, false, projectionMatrix, 0)
        glUniformMatrix4fv(uModelViewMatrix, 1, false, modelViewMatrix, 0)

        println("Radius exponent: ${orbitResult.radiusExp}")

        glUniform4f(
            uState, 0.0f, mandelbrotState!!.cmapscale.toFloat(),
            (1 + orbitResult.radiusExp).toFloat(), mandelbrotState!!.iterations.toFloat()
        )

        println("Polynomial coefficients: ${orbitResult.polyScaled.contentToString()}")
        println("Polynomial limit: ${orbitResult.polyLim}, Scale exp: ${orbitResult.polyScaleExp}")

        glUniform4f(
            uPoly1, orbitResult.polyScaled[0], orbitResult.polyScaled[1],
            orbitResult.polyScaled[2], orbitResult.polyScaled[3]
        )
        glUniform4f(
            uPoly2, orbitResult.polyScaled[4], orbitResult.polyScaled[5],
            orbitResult.polyLim.toFloat(), orbitResult.polyScaleExp.toFloat()
        )

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        glUniform1i(glGetUniformLocation(shaderProgram, "sequence"), 0)

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        glDisableVertexAttribArray(aVertexPosition)
    }

    fun cleanup() {
        mandelbrotState?.destroy()
        mandelbrotState = null
    }

    private fun updateVerticesForAspectRatio(width: Int, height: Int) {
        val viewportAspect = width.toFloat() / height.toFloat()
        val textureAspect = 1.0f // 1024x1024 = 1:1
        
        println("Updating vertices for aspect ratio: viewport=${width}x${height}, aspect=${viewportAspect}")
        
        val scaleX: Float
        val scaleY: Float
        
        if (viewportAspect > textureAspect) {
            // Viewport is wider than texture - scale by width for center-crop
            scaleX = viewportAspect / textureAspect  // > 1, expands horizontally
            scaleY = 1.0f
        } else {
            // Viewport is taller than texture - scale by height for center-crop
            scaleX = 1.0f
            scaleY = textureAspect / viewportAspect  // > 1, expands vertically
        }
        
        println("Scale factors: scaleX=${scaleX}, scaleY=${scaleY}")
        
        // Update vertices with center-crop scaling
        vertices = floatArrayOf(
            scaleX,  scaleY,    // top right
            -scaleX, scaleY,    // top left
            scaleX, -scaleY,    // bottom right
            -scaleX, -scaleY    // bottom left
        )
        
        // Update the vertex buffer
        vertexBufferData = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        // Update the GPU buffer with the new vertex data (only if vertex buffer is created)
        if (vertexBuffer != 0) {
            glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
            glBufferData(GL_ARRAY_BUFFER, vertices.size * 4, vertexBufferData, GL_STATIC_DRAW)
            println("Updated GPU vertex buffer with new aspect ratio data")
        }
    }

    private fun readAsset(name: String) =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

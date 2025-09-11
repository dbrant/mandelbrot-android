package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.opengl.GLES30.*
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.OutputStream
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MandelbrotRenderer(private val context: Context, val callback: Callback) : GLSurfaceView.Renderer {
    fun interface Callback {
        fun onNeedRedraw()
    }

    var mandelbrotState: MandelbrotNative.MandelbrotState? = null

    var colorMapScale = MandelbrotNative.INIT_COLOR_SCALE
        set(value) {
            field = if (value > 0f) value else MandelbrotNative.INIT_COLOR_SCALE
        }

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

    private var curOrbitResult: OrbitResult? = null
    private var curOrbitBuffer: FloatBuffer? = null
    private var recalculateOrbit = false
    private val tileQueue = ArrayDeque<Int>()
    private var tileHeight = 0
    private var tilesPerDraw = 9
    private val minTilesPerDraw = 5
    private var lastFrameMillis = 0L
    private var heaviestFrameMillis = 0L

    private var pendingStreamForScreenshot: OutputStream? = null


    private var frameBufferBlitProgram = 0
    private var frameBufferaPositionLoc = 0
    private var frameBufferaTexCoordLoc = 0
    private var frameBufferuTextureLoc = 0
    private lateinit var fullScreenQuadBuffer: FloatBuffer
    private lateinit var fullScreenTexCoordBuffer: FloatBuffer
    private var frameBufferTexture = -1
    private var frameBufferRef = -1
    private var frameBufferQuadVBO = -1
    private var frameBufferTexCoordVBO = -1

    private val vertexBufferData: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertices)
            position(0)
        }

    init {
        mandelbrotState = MandelbrotNative.MandelbrotState()
    }

    fun handleTouch(x: Float, y: Float, width: Int, height: Int) {
        // Convert screen coordinates to normalized coordinates (-1 to 1)
        var normalizedX: Double
        var normalizedY: Double

        val aspect = width.toDouble() / height.toDouble()
        if (aspect > 1.0f) {
            normalizedX = x / (width / 2.0) - 1.0
            normalizedY = (y + (width - height) / 2.0) / (width / 2.0) - 1.0
        } else {
            normalizedX = (x + (height - width) / 2.0) / (height / 2.0) - 1.0
            normalizedY = y / (height / 2.0) - 1.0
        }
        
        mandelbrotState?.zoomIn(normalizedX, normalizedY, 0.5)
    }

    fun setIterations(iterations: Int) {
        mandelbrotState?.numIterations = iterations
    }

    fun reset() {
        mandelbrotState?.reset()
        colorMapScale = MandelbrotNative.INIT_COLOR_SCALE
    }

    fun zoomOut(factor: Double) {
        mandelbrotState?.zoomOut(factor)
    }

    fun queueDraw() {
        recalculateOrbit = true
    }

    fun doRecalculateOrbit() {
        tileQueue.clear()

        curOrbitResult = mandelbrotState!!.generateOrbit()
        curOrbitBuffer = curOrbitResult!!.orbit
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        tileHeight = (surfaceHeight / tilesPerDraw) + 1
        val yOffset = surfaceHeight / 2 - tileHeight / 2
        var offsetHi = yOffset
        var offsetLo = yOffset
        tileQueue.add(yOffset)
        do {
            offsetLo -= tileHeight
            offsetHi += tileHeight
            if (offsetLo + tileHeight > 0) {
                tileQueue.add(offsetLo)
            }
            if (offsetHi < surfaceHeight) {
                tileQueue.add(offsetHi)
            }
        } while (offsetLo + tileHeight > 0 && offsetHi - tileHeight < surfaceHeight)
    }


    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
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

        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)

        // create framebuffer stuff

        val frameBufferVertexShader = loadShader(GL_VERTEX_SHADER, readAsset("framebuf_vert.glsl"))
        val frameBufferFragmentShader = loadShader(GL_FRAGMENT_SHADER, readAsset("framebuf_frag.glsl"))

        frameBufferBlitProgram = glCreateProgram().also { program ->
            glAttachShader(program, frameBufferVertexShader)
            glAttachShader(program, frameBufferFragmentShader)
            glLinkProgram(program)

            val linkStatus = IntArray(1)
            glGetProgramiv(program, GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = glGetProgramInfoLog(program)
                glDeleteProgram(program)
                throw RuntimeException("Error linking shader program: $error")
            }
        }
        frameBufferaPositionLoc = glGetAttribLocation(frameBufferBlitProgram, "aPosition")
        frameBufferaTexCoordLoc = glGetAttribLocation(frameBufferBlitProgram, "aTexCoord")
        frameBufferuTextureLoc  = glGetUniformLocation(frameBufferBlitProgram, "uTexture")

        // Setup fullscreen quad (two triangles) - triangle strip order
        val quadVertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        fullScreenQuadBuffer = ByteBuffer
            .allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        fullScreenQuadBuffer.position(0)

        fullScreenTexCoordBuffer = ByteBuffer
            .allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        fullScreenTexCoordBuffer.position(0)

        // Create VBOs for fullscreen quad
        val vbos = IntArray(2)
        glGenBuffers(2, vbos, 0)
        frameBufferQuadVBO = vbos[0]
        frameBufferTexCoordVBO = vbos[1]
        
        glBindBuffer(GL_ARRAY_BUFFER, frameBufferQuadVBO)
        glBufferData(GL_ARRAY_BUFFER, quadVertices.size * 4, fullScreenQuadBuffer, GL_STATIC_DRAW)
        
        glBindBuffer(GL_ARRAY_BUFFER, frameBufferTexCoordVBO)
        glBufferData(GL_ARRAY_BUFFER, texCoords.size * 4, fullScreenTexCoordBuffer, GL_STATIC_DRAW)
        
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glDeleteShader(frameBufferVertexShader)
        glDeleteShader(frameBufferFragmentShader)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        // Set up projection matrix for center-crop behavior
        val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        if (aspect > 1.0f) {
            // Wider than square - expand horizontally for center-crop
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f/aspect, 1f/aspect, -1f, 1f)
        } else {
            // Taller than square - crop left/right, fill height completely
            Matrix.orthoM(projectionMatrix, 0, -aspect, aspect, -1f, 1f, -1f, 1f)
        }
        Matrix.setIdentityM(modelViewMatrix, 0)

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



        // 1. Create texture
        val texIds = IntArray(1)
        glGenTextures(1, texIds, 0)
        frameBufferTexture = texIds[0]

        glBindTexture(GL_TEXTURE_2D, frameBufferTexture)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        // Allocate empty texture storage
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)

        // 2. Create framebuffer
        val fbos = IntArray(1)
        glGenFramebuffers(1, fbos, 0)
        frameBufferRef = fbos[0]

        glBindFramebuffer(GL_FRAMEBUFFER, frameBufferRef)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, frameBufferTexture, 0)

        // Check status
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete, status=$status")
        }

        // Unbind for now
        glBindFramebuffer(GL_FRAMEBUFFER, 0)



        queueDraw()
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
        if (tileQueue.isEmpty() && !recalculateOrbit) {
            drawFrameBuffer(frameBufferTexture)

            if (heaviestFrameMillis > 250) {
                tilesPerDraw += 4
            } else {
                tilesPerDraw -= 4
            }
            if (tilesPerDraw < minTilesPerDraw) { tilesPerDraw = minTilesPerDraw }
            if (tilesPerDraw > 25) { tilesPerDraw = 25 }

            if (pendingStreamForScreenshot != null) {
                saveToBitmap(pendingStreamForScreenshot!!)
                pendingStreamForScreenshot = null
            }
            return
        }

        glBindFramebuffer(GL_FRAMEBUFFER, frameBufferRef)
        glViewport(0, 0, surfaceWidth, surfaceHeight)

        glBindTexture(GL_TEXTURE_2D, orbitTexture)
        if (recalculateOrbit) {
            heaviestFrameMillis = 0
            lastFrameMillis = System.currentTimeMillis()
            doRecalculateOrbit()
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 1024, 1024, 0, GL_RED, GL_FLOAT, curOrbitBuffer)
            recalculateOrbit = false

            glClearColor(0.2f, 0.2f, 0.4f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)
        } else {
            val m = System.currentTimeMillis() - lastFrameMillis
            lastFrameMillis = System.currentTimeMillis()
            if (m > heaviestFrameMillis) {
                heaviestFrameMillis = m
            }
        }
        glDisable(GL_DEPTH_TEST)

        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer)
        glVertexAttribPointer(aVertexPosition, 2, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(aVertexPosition)

        glUseProgram(shaderProgram)

        glUniformMatrix4fv(uProjectionMatrix, 1, false, projectionMatrix, 0)
        glUniformMatrix4fv(uModelViewMatrix, 1, false, modelViewMatrix, 0)

        glUniform4f(uState, 0.0f, colorMapScale,
            (1 + curOrbitResult!!.radiusExp).toFloat(), mandelbrotState!!.numIterations.toFloat())

        glUniform4f(uPoly1, curOrbitResult!!.polyScaled[0], curOrbitResult!!.polyScaled[1],
            curOrbitResult!!.polyScaled[2], curOrbitResult!!.polyScaled[3])
        glUniform4f(uPoly2, curOrbitResult!!.polyScaled[4], curOrbitResult!!.polyScaled[5],
            curOrbitResult!!.polyLim.toFloat(), curOrbitResult!!.polyScaleExp.toFloat())

        glActiveTexture(GL_TEXTURE0)
        glUniform1i(glGetUniformLocation(shaderProgram, "sequence"), 0)

        val yOffset = tileQueue.removeFirstOrNull()
        if (yOffset != null) {
            glEnable(GL_SCISSOR_TEST)
            glScissor(0, yOffset, surfaceWidth, tileHeight)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            glDisable(GL_SCISSOR_TEST)
        }

        glDisableVertexAttribArray(aVertexPosition)
        
        // Unbind framebuffer before blitting
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        drawFrameBuffer(frameBufferTexture)

        callback.onNeedRedraw()
    }

    fun cleanup() {
        mandelbrotState?.destroy()
        mandelbrotState = null
    }


    private fun drawFrameBuffer(textureId: Int) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, surfaceWidth, surfaceHeight)
        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT)

        glUseProgram(frameBufferBlitProgram)

        // Bind texture to texture unit 0
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)
        glUniform1i(frameBufferuTextureLoc, 0)

        glEnableVertexAttribArray(frameBufferaPositionLoc)
        glEnableVertexAttribArray(frameBufferaTexCoordLoc)

        // Bind and set vertex positions
        glBindBuffer(GL_ARRAY_BUFFER, frameBufferQuadVBO)
        glVertexAttribPointer(frameBufferaPositionLoc, 2, GL_FLOAT, false, 0, 0)
        
        // Bind and set texture coordinates  
        glBindBuffer(GL_ARRAY_BUFFER, frameBufferTexCoordVBO)
        glVertexAttribPointer(frameBufferaTexCoordLoc, 2, GL_FLOAT, false, 0, 0)

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

        glDisableVertexAttribArray(frameBufferaPositionLoc)
        glDisableVertexAttribArray(frameBufferaTexCoordLoc)
    }

    fun enqueueScreenshot(stream: OutputStream) {
        pendingStreamForScreenshot = stream
    }

    private fun saveToBitmap(stream: OutputStream) {
        val pixels = IntArray(surfaceWidth * surfaceHeight)
        val buffer = IntBuffer.wrap(pixels)
        buffer.position(0)

        glReadPixels(0, 0, surfaceWidth, surfaceHeight, GL_RGBA, GL_UNSIGNED_BYTE, buffer)

        // OpenGL returns data in bottom-to-top order, so flip it vertically
        val flipped = IntArray(surfaceWidth * surfaceHeight)
        for (y in 0..<surfaceHeight) {
            System.arraycopy(pixels, y * surfaceWidth, flipped, (surfaceHeight - y - 1) * surfaceWidth, surfaceWidth)
        }

        // Convert to Bitmap (RGBA → ARGB)
        val bitmap = createBitmap(surfaceWidth, surfaceHeight)
        bitmap.copyPixelsFromBuffer(IntBuffer.wrap(flipped))

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.flush()
        stream.close()
    }

    private fun readAsset(name: String) =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

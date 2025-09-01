package com.dmitrybrant.android.mandelbrot

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

class MandelbrotRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var program = 0
    private var posLoc = 0
    private var uProj = 0
    private var uModel = 0
    private var uState = 0
    private var uPoly1 = 0
    private var uPoly2 = 0
    private var seqTex = 0
    private var vbo = 0

    var iterations = 1000
    var cmapScale = 20f
    var centerRe = "0"
    var centerIm = "0"
    var radius = "2"

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f,0f,0f,1f)
        program = buildProgram(
            readAsset("mandelbrot_vert.glsl"), readAsset("mandelbrot_frag.glsl")
        )
        posLoc = GLES30.glGetAttribLocation(program, "aVertexPosition")
        uProj = GLES30.glGetUniformLocation(program, "uProjectionMatrix")
        uModel = GLES30.glGetUniformLocation(program, "uModelViewMatrix")
        uState = GLES30.glGetUniformLocation(program, "uState")
        uPoly1 = GLES30.glGetUniformLocation(program, "poly1")
        uPoly2 = GLES30.glGetUniformLocation(program, "poly2")

        // Fullscreen quad
        val verts = floatArrayOf( 1f, 1f,  -1f, 1f,  1f,-1f,  -1f,-1f ) // :contentReference[oaicite:8]{index=8}
        val bb = ByteBuffer.allocateDirect(verts.size*4).order(ByteOrder.nativeOrder())
        bb.asFloatBuffer().put(verts).position(0)
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids,0); vbo=ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size*4, bb, GLES30.GL_STATIC_DRAW)

        // Texture for orbit (R32F, 1024x1024)
        GLES30.glGenTextures(1, ids,0); seqTex=ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, seqTex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)

        // initial upload
        uploadOrbit()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES30.glViewport(0,0,w,h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        // (identity matrices; you can add pan/zoom matrix if desired)
        val I = FloatArray(16); Matrix.setIdentityM(I,0)
        GLES30.glUniformMatrix4fv(uProj, 1, false, I, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, I, 0)

        // Bind quad
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 0, 0)

        // State uniforms
        val qStart = lastQStart // from native result
        val uStateVals = floatArrayOf(0f, cmapScale, (1+qStart).toFloat(), iterations.toFloat())
        GLES30.glUniform4fv(uState, 1, uStateVals, 0)

        // Poly uniforms from native result
        GLES30.glUniform4f(uPoly1, polyScaled[0], polyScaled[1], polyScaled[2], polyScaled[3])
        GLES30.glUniform4f(uPoly2, polyScaled[4], polyScaled[5], polyLim.toFloat(), polyScaleExp.toFloat())

        // Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, seqTex)
        // sampler is 0 by default; if you add a uniform location, set it to 0

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private var polyScaled = FloatArray(6)
    private var polyLim = 0
    private var polyScaleExp = 0
    private var lastQStart = 0

    fun uploadOrbit() {



/*

        // create a 1024x1024 float array where R channel = x/1023, G unused, B unused
        val size = 1024 * 1024
        val arr = FloatArray(size)
        for (y in 0 until 1024) {
            for (x in 0 until 1024) {
                val i = y * 1024 + x
                // store a simple ramp 0..1, but keep same packing as shader expects:
                // shader expects each texel to hold a single float in the RED channel,
                // and the shader reads triplets by indexing into the flattened float array.
                arr[i] = x.toFloat() / 1023f
            }
        }

        val fb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(arr).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, seqTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0,
            GLES30.GL_R32F, 1024, 1024, 0,
            GLES30.GL_RED, GLES30.GL_FLOAT, fb
        )
        val err = GLES30.glGetError()
        Log.i("Mandel", "uploadOrbitTestGradient: glGetError = $err")

*/



        val res = MandelNative.makeReferenceOrbit(centerRe, centerIm, radius, iterations)

        Log.i("Mandel", "orbit len = ${res.orbit.size}")
        var minv = Float.POSITIVE_INFINITY
        var maxv = Float.NEGATIVE_INFINITY
        for (i in res.orbit.indices) {
            val v = res.orbit[i]
            if (v.isFinite()) {
                if (v < minv) minv = v
                if (v > maxv) maxv = v
            }
        }
        Log.i("Mandel", "orbit min=$minv max=$maxv")
        val show = min(30, res.orbit.size)
        val sb = StringBuilder("orbit[0..${show-1}]:")
        for (i in 0 until show) {
            sb.append(" ${res.orbit[i]}")
        }
        Log.i("Mandel", sb.toString())

        Log.i("Mandel", "polyScaled: ${res.polyScaled.joinToString()} polyLim=${res.polyLim} polyScaleExp=${res.polyScaleExp} qStart=${res.qStart}")


        // store polys/state
        polyScaled = res.polyScaled
        polyLim = res.polyLim
        polyScaleExp = res.polyScaleExp
        lastQStart = res.qStart

        // Upload orbit as R32F, 1024x1024, single channel
        val fb = ByteBuffer.allocateDirect(res.orbit.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(res.orbit).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, seqTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0,
            GLES30.GL_R32F, 1024, 1024, 0,
            GLES30.GL_RED, GLES30.GL_FLOAT, fb
        )

    }

    private fun readAsset(name: String) =
        context.assets.open(name).bufferedReader().use { it.readText() }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileProgram(GLES30.GL_VERTEX_SHADER, vs)
        val f = compileProgram(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok,0)
        if (ok[0]==0) throw RuntimeException(GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun compileProgram(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok,0)
        if (ok[0] == 0) throw RuntimeException(GLES30.glGetShaderInfoLog(s))
        return s
    }
}

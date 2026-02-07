import Foundation
import GLKit
import OpenGLES

class MandelbrotGLRenderer {
    var mandelbrotState: MandelbrotState?
    var colorMapScale: Float = MandelbrotState.INIT_COLOR_SCALE {
        didSet {
            if colorMapScale <= 0 { colorMapScale = MandelbrotState.INIT_COLOR_SCALE }
        }
    }

    var onNeedRedraw: (() -> Void)?

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private var shaderProgram: GLuint = 0
    private var vertexBuffer: GLuint = 0
    private var orbitTexture: GLuint = 0

    private var uProjectionMatrix: GLint = 0
    private var uModelViewMatrix: GLint = 0
    private var uState: GLint = 0
    private var uPoly1: GLint = 0
    private var uPoly2: GLint = 0
    private var aVertexPosition: GLint = 0

    private var projectionMatrix = [Float](repeating: 0, count: 16)
    private var modelViewMatrix = [Float](repeating: 0, count: 16)

    private let vertices: [Float] = [
        1.0,  1.0,
       -1.0,  1.0,
        1.0, -1.0,
       -1.0, -1.0
    ]

    private var curOrbitResult: OrbitResult?
    private var recalculateOrbit = false
    private var tileQueue = [Int]()
    private var tileHeight: Int = 0
    private var tilesPerDraw: Int = 30
    private let minTilesPerDraw = 20
    private var lastFrameTime: CFTimeInterval = 0
    private var heaviestFrameMillis: Int64 = 0

    private var saveBitmapCallback: ((UIImage?) -> Void)?

    private var frameBufferBlitProgram: GLuint = 0
    private var frameBufferaPositionLoc: GLint = 0
    private var frameBufferaTexCoordLoc: GLint = 0
    private var frameBufferuTextureLoc: GLint = 0
    private var frameBufferTexture: GLuint = 0
    private var frameBufferRef: GLuint = 0
    private var frameBufferQuadVBO: GLuint = 0
    private var frameBufferTexCoordVBO: GLuint = 0
    private var viewDefaultFBO: GLint = 0

    var hasTilesRemaining: Bool {
        return !tileQueue.isEmpty || recalculateOrbit
    }

    init() {
        mandelbrotState = MandelbrotState()
    }

    func handleTouch(x: Float, y: Float, width: Int, height: Int) {
        var normalizedX: Double
        var normalizedY: Double

        let aspect = Double(width) / Double(height)
        if aspect > 1.0 {
            normalizedX = Double(x) / (Double(width) / 2.0) - 1.0
            normalizedY = (Double(y) + Double(width - height) / 2.0) / (Double(width) / 2.0) - 1.0
        } else {
            normalizedX = (Double(x) + Double(height - width) / 2.0) / (Double(height) / 2.0) - 1.0
            normalizedY = Double(y) / (Double(height) / 2.0) - 1.0
        }

        mandelbrotState?.zoomIn(dx: normalizedX, dy: normalizedY, factor: 0.5)
    }

    func setIterations(_ iterations: Int) {
        mandelbrotState?.numIterations = iterations
    }

    func reset() {
        mandelbrotState?.reset()
        colorMapScale = MandelbrotState.INIT_COLOR_SCALE
    }

    func zoomOut(_ factor: Double) {
        mandelbrotState?.zoomOut(factor: factor)
    }

    func queueDraw() {
        recalculateOrbit = true
    }

    func doRecalculateOrbit() {
        tileQueue.removeAll()

        curOrbitResult = mandelbrotState!.generateOrbit()

        tileHeight = (surfaceHeight / tilesPerDraw) + 1
        let yOffset = surfaceHeight / 2 - tileHeight / 2
        var offsetHi = yOffset
        var offsetLo = yOffset
        tileQueue.append(yOffset)
        repeat {
            offsetLo -= tileHeight
            offsetHi += tileHeight
            if offsetLo + tileHeight > 0 {
                tileQueue.append(offsetLo)
            }
            if offsetHi < surfaceHeight {
                tileQueue.append(offsetHi)
            }
        } while offsetLo + tileHeight > 0 && offsetHi - tileHeight < surfaceHeight
    }

    func setupGL() {
        let vertexShader = loadShader(type: GLenum(GL_VERTEX_SHADER), name: "mandelbrot_vert")
        let fragmentShader = loadShader(type: GLenum(GL_FRAGMENT_SHADER), name: "mandelbrot_frag")

        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        var linkStatus: GLint = 0
        glGetProgramiv(shaderProgram, GLenum(GL_LINK_STATUS), &linkStatus)
        if linkStatus == 0 {
            var logLength: GLint = 0
            glGetProgramiv(shaderProgram, GLenum(GL_INFO_LOG_LENGTH), &logLength)
            var log = [GLchar](repeating: 0, count: Int(logLength))
            glGetProgramInfoLog(shaderProgram, logLength, nil, &log)
            print("Error linking mandelbrot shader program: \(String(cString: log))")
            glDeleteProgram(shaderProgram)
        }

        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)

        let fbVertexShader = loadShader(type: GLenum(GL_VERTEX_SHADER), name: "framebuf_vert")
        let fbFragmentShader = loadShader(type: GLenum(GL_FRAGMENT_SHADER), name: "framebuf_frag")

        frameBufferBlitProgram = glCreateProgram()
        glAttachShader(frameBufferBlitProgram, fbVertexShader)
        glAttachShader(frameBufferBlitProgram, fbFragmentShader)
        glLinkProgram(frameBufferBlitProgram)

        var fbLinkStatus: GLint = 0
        glGetProgramiv(frameBufferBlitProgram, GLenum(GL_LINK_STATUS), &fbLinkStatus)
        if fbLinkStatus == 0 {
            var logLength: GLint = 0
            glGetProgramiv(frameBufferBlitProgram, GLenum(GL_INFO_LOG_LENGTH), &logLength)
            var log = [GLchar](repeating: 0, count: Int(logLength))
            glGetProgramInfoLog(frameBufferBlitProgram, logLength, nil, &log)
            print("Error linking framebuffer shader program: \(String(cString: log))")
            glDeleteProgram(frameBufferBlitProgram)
        }

        frameBufferaPositionLoc = glGetAttribLocation(frameBufferBlitProgram, "aPosition")
        frameBufferaTexCoordLoc = glGetAttribLocation(frameBufferBlitProgram, "aTexCoord")
        frameBufferuTextureLoc = glGetUniformLocation(frameBufferBlitProgram, "uTexture")

        let quadVertices: [Float] = [-1, -1, 1, -1, -1, 1, 1, 1]
        let texCoords: [Float] = [0, 0, 1, 0, 0, 1, 1, 1]

        var vbos: [GLuint] = [0, 0]
        glGenBuffers(2, &vbos)
        frameBufferQuadVBO = vbos[0]
        frameBufferTexCoordVBO = vbos[1]

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), frameBufferQuadVBO)
        glBufferData(GLenum(GL_ARRAY_BUFFER), quadVertices.count * MemoryLayout<Float>.size, quadVertices, GLenum(GL_STATIC_DRAW))

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), frameBufferTexCoordVBO)
        glBufferData(GLenum(GL_ARRAY_BUFFER), texCoords.count * MemoryLayout<Float>.size, texCoords, GLenum(GL_STATIC_DRAW))

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), 0)

        glDeleteShader(fbVertexShader)
        glDeleteShader(fbFragmentShader)
    }

    func setupSurface(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        let aspect = Float(surfaceWidth) / Float(surfaceHeight)
        if aspect > 1.0 {
            loadOrthoMatrix(&projectionMatrix, left: -1, right: 1, bottom: -1/aspect, top: 1/aspect, near: -1, far: 1)
        } else {
            loadOrthoMatrix(&projectionMatrix, left: -aspect, right: aspect, bottom: -1, top: 1, near: -1, far: 1)
        }
        loadIdentityMatrix(&modelViewMatrix)

        uProjectionMatrix = glGetUniformLocation(shaderProgram, "uProjectionMatrix")
        uModelViewMatrix = glGetUniformLocation(shaderProgram, "uModelViewMatrix")
        uState = glGetUniformLocation(shaderProgram, "uState")
        uPoly1 = glGetUniformLocation(shaderProgram, "poly1")
        uPoly2 = glGetUniformLocation(shaderProgram, "poly2")
        aVertexPosition = glGetAttribLocation(shaderProgram, "aVertexPosition")

        var buffers: [GLuint] = [0]
        glGenBuffers(1, &buffers)
        vertexBuffer = buffers[0]

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), vertexBuffer)
        glBufferData(GLenum(GL_ARRAY_BUFFER), vertices.count * MemoryLayout<Float>.size, vertices, GLenum(GL_STATIC_DRAW))

        var textures: [GLuint] = [0]
        glGenTextures(1, &textures)
        orbitTexture = textures[0]

        glBindTexture(GLenum(GL_TEXTURE_2D), orbitTexture)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_MIN_FILTER), GL_NEAREST)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_MAG_FILTER), GL_NEAREST)
        glPixelStorei(GLenum(GL_UNPACK_ALIGNMENT), 1)

        var texIds: [GLuint] = [0]
        glGenTextures(1, &texIds)
        frameBufferTexture = texIds[0]

        glBindTexture(GLenum(GL_TEXTURE_2D), frameBufferTexture)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_MIN_FILTER), GL_LINEAR)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_MAG_FILTER), GL_LINEAR)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_WRAP_S), GL_CLAMP_TO_EDGE)
        glTexParameteri(GLenum(GL_TEXTURE_2D), GLenum(GL_TEXTURE_WRAP_T), GL_CLAMP_TO_EDGE)

        glTexImage2D(GLenum(GL_TEXTURE_2D), 0, GL_RGBA, GLsizei(width), GLsizei(height), 0, GLenum(GL_RGBA), GLenum(GL_UNSIGNED_BYTE), nil)

        var fbos: [GLuint] = [0]
        glGenFramebuffers(1, &fbos)
        frameBufferRef = fbos[0]

        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), frameBufferRef)
        glClearColor(0, 0, 0, 1)
        glClear(GLbitfield(GL_COLOR_BUFFER_BIT))
        glFramebufferTexture2D(GLenum(GL_FRAMEBUFFER), GLenum(GL_COLOR_ATTACHMENT0), GLenum(GL_TEXTURE_2D), frameBufferTexture, 0)

        let status = glCheckFramebufferStatus(GLenum(GL_FRAMEBUFFER))
        if status != GLenum(GL_FRAMEBUFFER_COMPLETE) {
            print("Framebuffer not complete, status=\(status)")
        }

        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), 0)

        queueDraw()
    }

    func drawFrame() {
        guard mandelbrotState != nil else { return }

        // Save the GLKView's framebuffer (it's NOT 0 on iOS)
        glGetIntegerv(GLenum(GL_FRAMEBUFFER_BINDING), &viewDefaultFBO)

        if tileQueue.isEmpty && !recalculateOrbit {
            drawFrameBuffer(frameBufferTexture)

            if heaviestFrameMillis > 250 {
                tilesPerDraw += 4
            } else {
                tilesPerDraw -= 4
            }
            tilesPerDraw = max(tilesPerDraw, minTilesPerDraw)
            tilesPerDraw = min(tilesPerDraw, 60)

            if let callback = saveBitmapCallback {
                let image = captureFrameBuffer()
                callback(image)
                saveBitmapCallback = nil
            }
            return
        }

        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), frameBufferRef)
        glViewport(0, 0, GLsizei(surfaceWidth), GLsizei(surfaceHeight))

        glBindTexture(GLenum(GL_TEXTURE_2D), orbitTexture)
        if recalculateOrbit {
            heaviestFrameMillis = 0
            lastFrameTime = CACurrentMediaTime()
            doRecalculateOrbit()

            glTexImage2D(GLenum(GL_TEXTURE_2D), 0, GL_R32F, 1024, 1024, 0, GLenum(GL_RED), GLenum(GL_FLOAT), curOrbitResult!.orbitData)
            recalculateOrbit = false

            glClearColor(0.2, 0.2, 0.4, 1)
            glClear(GLbitfield(GL_COLOR_BUFFER_BIT))
        } else {
            let m = Int64((CACurrentMediaTime() - lastFrameTime) * 1000)
            lastFrameTime = CACurrentMediaTime()
            if m > heaviestFrameMillis {
                heaviestFrameMillis = m
            }
        }
        glDisable(GLenum(GL_DEPTH_TEST))

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), vertexBuffer)
        glVertexAttribPointer(GLuint(aVertexPosition), 2, GLenum(GL_FLOAT), GLboolean(GL_FALSE), 0, nil)
        glEnableVertexAttribArray(GLuint(aVertexPosition))

        glUseProgram(shaderProgram)

        glUniformMatrix4fv(uProjectionMatrix, 1, GLboolean(GL_FALSE), projectionMatrix)
        glUniformMatrix4fv(uModelViewMatrix, 1, GLboolean(GL_FALSE), modelViewMatrix)

        glUniform4f(uState, 0.0, colorMapScale,
                    Float(1.0 + curOrbitResult!.radiusExp), Float(mandelbrotState!.numIterations))

        glUniform4f(uPoly1, curOrbitResult!.polyScaled[0], curOrbitResult!.polyScaled[1],
                    curOrbitResult!.polyScaled[2], curOrbitResult!.polyScaled[3])
        glUniform4f(uPoly2, curOrbitResult!.polyScaled[4], curOrbitResult!.polyScaled[5],
                    Float(curOrbitResult!.polyLim), Float(curOrbitResult!.polyScaleExp))

        glActiveTexture(GLenum(GL_TEXTURE0))
        glUniform1i(glGetUniformLocation(shaderProgram, "sequence"), 0)

        if !tileQueue.isEmpty {
            let yOffset = tileQueue.removeFirst()
            glEnable(GLenum(GL_SCISSOR_TEST))
            glScissor(0, GLint(yOffset), GLsizei(surfaceWidth), GLsizei(tileHeight))
            glDrawArrays(GLenum(GL_TRIANGLE_STRIP), 0, 4)
            glDisable(GLenum(GL_SCISSOR_TEST))
        }

        glDisableVertexAttribArray(GLuint(aVertexPosition))

        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), GLuint(viewDefaultFBO))
        drawFrameBuffer(frameBufferTexture)

        onNeedRedraw?()
    }

    func cleanup() {
        mandelbrotState?.destroy()
        mandelbrotState = nil
    }

    private func drawFrameBuffer(_ textureId: GLuint) {
        glBindFramebuffer(GLenum(GL_FRAMEBUFFER), GLuint(viewDefaultFBO))
        glViewport(0, 0, GLsizei(surfaceWidth), GLsizei(surfaceHeight))
        glDisable(GLenum(GL_DEPTH_TEST))
        glClear(GLbitfield(GL_COLOR_BUFFER_BIT))

        glUseProgram(frameBufferBlitProgram)

        glActiveTexture(GLenum(GL_TEXTURE0))
        glBindTexture(GLenum(GL_TEXTURE_2D), textureId)
        glUniform1i(frameBufferuTextureLoc, 0)

        glEnableVertexAttribArray(GLuint(frameBufferaPositionLoc))
        glEnableVertexAttribArray(GLuint(frameBufferaTexCoordLoc))

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), frameBufferQuadVBO)
        glVertexAttribPointer(GLuint(frameBufferaPositionLoc), 2, GLenum(GL_FLOAT), GLboolean(GL_FALSE), 0, nil)

        glBindBuffer(GLenum(GL_ARRAY_BUFFER), frameBufferTexCoordVBO)
        glVertexAttribPointer(GLuint(frameBufferaTexCoordLoc), 2, GLenum(GL_FLOAT), GLboolean(GL_FALSE), 0, nil)

        glDrawArrays(GLenum(GL_TRIANGLE_STRIP), 0, 4)

        glDisableVertexAttribArray(GLuint(frameBufferaPositionLoc))
        glDisableVertexAttribArray(GLuint(frameBufferaTexCoordLoc))
    }

    func enqueueScreenshot(callback: @escaping (UIImage?) -> Void) {
        saveBitmapCallback = callback
    }

    private func captureFrameBuffer() -> UIImage? {
        let dataSize = surfaceWidth * surfaceHeight * 4
        var pixels = [UInt8](repeating: 0, count: dataSize)
        glReadPixels(0, 0, GLsizei(surfaceWidth), GLsizei(surfaceHeight), GLenum(GL_RGBA), GLenum(GL_UNSIGNED_BYTE), &pixels)

        // Flip vertically (OpenGL returns bottom-to-top)
        let rowSize = surfaceWidth * 4
        var flipped = [UInt8](repeating: 0, count: dataSize)
        for y in 0..<surfaceHeight {
            let srcOffset = y * rowSize
            let dstOffset = (surfaceHeight - y - 1) * rowSize
            flipped[dstOffset..<dstOffset + rowSize] = pixels[srcOffset..<srcOffset + rowSize]
        }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(data: &flipped, width: surfaceWidth, height: surfaceHeight,
                                       bitsPerComponent: 8, bytesPerRow: rowSize,
                                       space: colorSpace,
                                       bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return nil
        }
        guard let cgImage = context.makeImage() else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private func loadShader(type: GLenum, name: String) -> GLuint {
        guard let path = Bundle.main.path(forResource: name, ofType: "glsl") else {
            print("Could not find shader: \(name).glsl")
            return 0
        }
        guard let source = try? String(contentsOfFile: path, encoding: .utf8) else {
            print("Could not read shader: \(name).glsl")
            return 0
        }

        let shader = glCreateShader(type)
        var cSource = (source as NSString).utf8String
        var length = GLint(source.count)
        glShaderSource(shader, 1, &cSource, &length)
        glCompileShader(shader)

        var compileStatus: GLint = 0
        glGetShaderiv(shader, GLenum(GL_COMPILE_STATUS), &compileStatus)
        if compileStatus == 0 {
            var logLength: GLint = 0
            glGetShaderiv(shader, GLenum(GL_INFO_LOG_LENGTH), &logLength)
            var log = [GLchar](repeating: 0, count: Int(logLength))
            glGetShaderInfoLog(shader, logLength, nil, &log)
            print("Error compiling shader \(name): \(String(cString: log))")
            glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private func loadOrthoMatrix(_ m: inout [Float], left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        let ral = right + left
        let rsl = right - left
        let tab = top + bottom
        let tsb = top - bottom
        let fan = far + near
        let fsn = far - near

        m = [
            2.0 / rsl, 0, 0, 0,
            0, 2.0 / tsb, 0, 0,
            0, 0, -2.0 / fsn, 0,
            -ral / rsl, -tab / tsb, -fan / fsn, 1.0
        ]
    }

    private func loadIdentityMatrix(_ m: inout [Float]) {
        m = [
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        ]
    }
}

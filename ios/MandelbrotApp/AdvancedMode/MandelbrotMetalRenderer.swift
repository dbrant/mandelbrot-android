import Foundation
import UIKit
import MetalKit
import simd

struct MandelbrotUniforms {
    var projectionMatrix: simd_float4x4
    var modelViewMatrix: simd_float4x4
    var uState: SIMD4<Float>
    var poly1: SIMD4<Float>
    var poly2: SIMD4<Float>
}

class MandelbrotMetalRenderer {
    var mandelbrotState: MandelbrotState?
    var colorMapScale: Float = MandelbrotState.INIT_COLOR_SCALE {
        didSet {
            if colorMapScale <= 0 { colorMapScale = MandelbrotState.INIT_COLOR_SCALE }
        }
    }

    var onNeedRedraw: (() -> Void)?

    private var device: MTLDevice!
    private var commandQueue: MTLCommandQueue!

    private var mandelbrotPipelineState: MTLRenderPipelineState!
    private var blitPipelineState: MTLRenderPipelineState!

    private var mandelbrotVertexBuffer: MTLBuffer!
    private var blitVertexBuffer: MTLBuffer!

    private var orbitTexture: MTLTexture?
    private var accumulationTexture: MTLTexture?

    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    private var projectionMatrix = matrix_identity_float4x4
    private var modelViewMatrix = matrix_identity_float4x4

    private var curOrbitResult: OrbitResult?
    private var recalculateOrbit = false
    private var tileQueue = [Int]()
    private let tileHeight: Int = 128
    private var needsClear = false

    private var saveBitmapCallback: ((UIImage?) -> Void)?

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

    private func doRecalculateOrbit() {
        tileQueue.removeAll()

        curOrbitResult = mandelbrotState!.generateOrbit()

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

    func setupMetal(device: MTLDevice) {
        self.device = device
        self.commandQueue = device.makeCommandQueue()!

        guard let library = device.makeDefaultLibrary() else {
            print("Failed to load Metal library")
            return
        }

        // Mandelbrot pipeline
        let mandelbrotPipelineDescriptor = MTLRenderPipelineDescriptor()
        mandelbrotPipelineDescriptor.vertexFunction = library.makeFunction(name: "mandelbrot_vertex")
        mandelbrotPipelineDescriptor.fragmentFunction = library.makeFunction(name: "mandelbrot_fragment")
        mandelbrotPipelineDescriptor.colorAttachments[0].pixelFormat = .rgba8Unorm

        do {
            mandelbrotPipelineState = try device.makeRenderPipelineState(descriptor: mandelbrotPipelineDescriptor)
        } catch {
            print("Failed to create mandelbrot pipeline state: \(error)")
            return
        }

        // Blit pipeline
        let blitPipelineDescriptor = MTLRenderPipelineDescriptor()
        blitPipelineDescriptor.vertexFunction = library.makeFunction(name: "framebuf_vertex")
        blitPipelineDescriptor.fragmentFunction = library.makeFunction(name: "framebuf_fragment")
        blitPipelineDescriptor.colorAttachments[0].pixelFormat = .bgra8Unorm

        do {
            blitPipelineState = try device.makeRenderPipelineState(descriptor: blitPipelineDescriptor)
        } catch {
            print("Failed to create blit pipeline state: \(error)")
            return
        }

        // Mandelbrot vertex buffer (fullscreen quad as triangle strip)
        let mandelbrotVertices: [Float] = [
            1.0,  1.0,
           -1.0,  1.0,
            1.0, -1.0,
           -1.0, -1.0
        ]
        mandelbrotVertexBuffer = device.makeBuffer(
            bytes: mandelbrotVertices,
            length: mandelbrotVertices.count * MemoryLayout<Float>.size,
            options: .storageModeShared)

        // Blit vertex buffer (interleaved position + texcoord)
        // Metal texture origin is top-left, so V is flipped compared to GL
        let blitVertices: [Float] = [
            // pos.x, pos.y, tex.u, tex.v
            -1, -1,   0, 1,   // bottom-left screen  → bottom of texture (v=1)
             1, -1,   1, 1,   // bottom-right screen → (v=1)
            -1,  1,   0, 0,   // top-left screen     → top of texture (v=0)
             1,  1,   1, 0,   // top-right screen    → (v=0)
        ]
        blitVertexBuffer = device.makeBuffer(
            bytes: blitVertices,
            length: blitVertices.count * MemoryLayout<Float>.size,
            options: .storageModeShared)
    }

    func setupSurface(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        let aspect = Float(surfaceWidth) / Float(surfaceHeight)
        if aspect > 1.0 {
            projectionMatrix = makeOrthoMatrix(left: -1, right: 1, bottom: -1/aspect, top: 1/aspect, near: -1, far: 1)
        } else {
            projectionMatrix = makeOrthoMatrix(left: -aspect, right: aspect, bottom: -1, top: 1, near: -1, far: 1)
        }
        modelViewMatrix = matrix_identity_float4x4

        // Orbit texture (R32Float, 1024x1024)
        let orbitDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .r32Float, width: 1024, height: 1024, mipmapped: false)
        orbitDescriptor.usage = .shaderRead
        orbitDescriptor.storageMode = .shared
        orbitTexture = device.makeTexture(descriptor: orbitDescriptor)

        // Accumulation texture (RGBA8, screen-sized, render target + readable)
        let accumDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false)
        accumDescriptor.usage = [.renderTarget, .shaderRead]
        accumDescriptor.storageMode = .shared
        accumulationTexture = device.makeTexture(descriptor: accumDescriptor)

        queueDraw()
    }

    func drawFrame(view: MTKView) {
        guard mandelbrotState != nil else { return }
        guard let drawable = view.currentDrawable else { return }
        guard let commandBuffer = commandQueue.makeCommandBuffer() else { return }

        if !tileQueue.isEmpty || recalculateOrbit {
            if recalculateOrbit {
                doRecalculateOrbit()

                // Upload orbit data to texture
                let region = MTLRegion(
                    origin: MTLOrigin(x: 0, y: 0, z: 0),
                    size: MTLSize(width: 1024, height: 1024, depth: 1))
                orbitTexture?.replace(
                    region: region,
                    mipmapLevel: 0,
                    withBytes: curOrbitResult!.orbitData,
                    bytesPerRow: 1024 * MemoryLayout<Float>.size)
                recalculateOrbit = false
                needsClear = true
            }

            // Create render pass targeting accumulation texture
            let rpd = MTLRenderPassDescriptor()
            rpd.colorAttachments[0].texture = accumulationTexture

            if needsClear {
                rpd.colorAttachments[0].loadAction = .clear
                rpd.colorAttachments[0].clearColor = MTLClearColor(red: 0.2, green: 0.2, blue: 0.4, alpha: 1.0)
                needsClear = false
            } else {
                rpd.colorAttachments[0].loadAction = .load
            }
            rpd.colorAttachments[0].storeAction = .store

            guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: rpd) else { return }

            encoder.setRenderPipelineState(mandelbrotPipelineState)
            encoder.setViewport(MTLViewport(
                originX: 0, originY: 0,
                width: Double(surfaceWidth), height: Double(surfaceHeight),
                znear: 0, zfar: 1))

            // Scissor to single tile
            if !tileQueue.isEmpty {
                let yOffset = tileQueue.removeFirst()
                let clampedY = max(0, yOffset)
                let clampedH = min(tileHeight, surfaceHeight - clampedY)
                if clampedH > 0 {
                    encoder.setScissorRect(MTLScissorRect(
                        x: 0, y: clampedY, width: surfaceWidth, height: clampedH))
                }
            }

            // Vertex buffer
            encoder.setVertexBuffer(mandelbrotVertexBuffer, offset: 0, index: 0)

            // Uniforms
            var uniforms = MandelbrotUniforms(
                projectionMatrix: projectionMatrix,
                modelViewMatrix: modelViewMatrix,
                uState: SIMD4<Float>(0.0, colorMapScale,
                    Float(1.0 + curOrbitResult!.radiusExp), Float(mandelbrotState!.numIterations)),
                poly1: SIMD4<Float>(curOrbitResult!.polyScaled[0], curOrbitResult!.polyScaled[1],
                    curOrbitResult!.polyScaled[2], curOrbitResult!.polyScaled[3]),
                poly2: SIMD4<Float>(curOrbitResult!.polyScaled[4], curOrbitResult!.polyScaled[5],
                    Float(curOrbitResult!.polyLim), Float(curOrbitResult!.polyScaleExp))
            )
            encoder.setVertexBytes(&uniforms, length: MemoryLayout<MandelbrotUniforms>.size, index: 1)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<MandelbrotUniforms>.size, index: 0)

            // Orbit texture
            encoder.setFragmentTexture(orbitTexture, index: 0)

            encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
            encoder.endEncoding()
        }

        // Blit accumulation texture to screen
        guard let blitRPD = view.currentRenderPassDescriptor else { return }
        blitRPD.colorAttachments[0].loadAction = .clear
        blitRPD.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)

        guard let blitEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: blitRPD) else { return }
        blitEncoder.setRenderPipelineState(blitPipelineState)
        blitEncoder.setVertexBuffer(blitVertexBuffer, offset: 0, index: 0)
        blitEncoder.setFragmentTexture(accumulationTexture, index: 0)
        blitEncoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        blitEncoder.endEncoding()

        commandBuffer.present(drawable)

        // Screenshot capture (if requested and all tiles done)
        if let callback = saveBitmapCallback, tileQueue.isEmpty && !recalculateOrbit {
            commandBuffer.addCompletedHandler { [weak self] _ in
                guard let self = self else { return }
                let image = self.captureAccumulationTexture()
                DispatchQueue.main.async {
                    callback(image)
                }
            }
            saveBitmapCallback = nil
        }

        commandBuffer.commit()

        // Request next frame if tiles remain
        if hasTilesRemaining {
            onNeedRedraw?()
        }
    }

    func cleanup() {
        mandelbrotState?.destroy()
        mandelbrotState = nil
        orbitTexture = nil
        accumulationTexture = nil
        mandelbrotVertexBuffer = nil
        blitVertexBuffer = nil
    }

    func enqueueScreenshot(callback: @escaping (UIImage?) -> Void) {
        saveBitmapCallback = callback
    }

    private func captureAccumulationTexture() -> UIImage? {
        guard let texture = accumulationTexture else { return nil }
        let bytesPerRow = surfaceWidth * 4
        let dataSize = bytesPerRow * surfaceHeight
        var pixels = [UInt8](repeating: 0, count: dataSize)

        texture.getBytes(&pixels, bytesPerRow: bytesPerRow,
            from: MTLRegion(
                origin: MTLOrigin(x: 0, y: 0, z: 0),
                size: MTLSize(width: surfaceWidth, height: surfaceHeight, depth: 1)),
            mipmapLevel: 0)

        // No vertical flip needed -- Metal texture origin is top-left, same as CGImage
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let context = CGContext(data: &pixels, width: surfaceWidth, height: surfaceHeight,
                                       bitsPerComponent: 8, bytesPerRow: bytesPerRow,
                                       space: colorSpace,
                                       bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return nil
        }
        guard let cgImage = context.makeImage() else { return nil }
        return UIImage(cgImage: cgImage)
    }

    private func makeOrthoMatrix(left: Float, right: Float, bottom: Float, top: Float,
                                  near: Float, far: Float) -> simd_float4x4 {
        let rsl = right - left
        let tsb = top - bottom
        let fsn = far - near
        return simd_float4x4(columns: (
            SIMD4<Float>(2.0 / rsl, 0, 0, 0),
            SIMD4<Float>(0, 2.0 / tsb, 0, 0),
            SIMD4<Float>(0, 0, -2.0 / fsn, 0),
            SIMD4<Float>(-(right + left) / rsl, -(top + bottom) / tsb, -(far + near) / fsn, 1.0)
        ))
    }
}

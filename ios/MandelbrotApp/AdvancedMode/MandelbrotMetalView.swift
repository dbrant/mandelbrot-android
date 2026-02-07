import SwiftUI
import MetalKit

class MandelbrotMetalViewController: NSObject {
    let renderer = MandelbrotMetalRenderer()
    var metalView: MTKView?

    var onUpdateState: ((String, String, String, Int, Float) -> Void)?

    func setupView(_ view: MTKView) {
        guard let device = MTLCreateSystemDefaultDevice() else {
            print("Metal is not supported on this device")
            return
        }
        view.device = device
        view.colorPixelFormat = .bgra8Unorm
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        metalView = view

        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleTapGesture(_:)))
        view.addGestureRecognizer(tapRecognizer)

        renderer.setupMetal(device: device)

        renderer.onNeedRedraw = { [weak self] in
            self?.scheduleRedraw()
        }
    }

    func surfaceChanged(width: Int, height: Int) {
        renderer.setupSurface(width: width, height: height)
    }

    func draw(in view: MTKView) {
        renderer.drawFrame(view: view)
    }

    func initState(centerX: String, centerY: String, radius: String, iterations: Int, colorScale: Float) {
        renderer.mandelbrotState?.set(x: centerX, y: centerY, r: radius, iterations: iterations)
        renderer.colorMapScale = colorScale
        renderer.queueDraw()
        scheduleRedraw()
    }

    func zoomOut(_ factor: Double) {
        renderer.zoomOut(factor)
        requestRender()
        doCallback()
    }

    func reset() {
        renderer.reset()
        requestRender()
        doCallback()
    }

    func setIterations(_ iterations: Int) {
        renderer.setIterations(iterations)
        requestRender()
        doCallback()
    }

    func setCmapScale(_ scale: Float) {
        renderer.colorMapScale = scale
        requestRender()
        doCallback()
    }

    func saveToBitmap(callback: @escaping (UIImage?) -> Void) {
        renderer.enqueueScreenshot(callback: callback)
        metalView?.setNeedsDisplay()
    }

    func requestRender() {
        renderer.queueDraw()
        scheduleRedraw()
    }

    @objc private func handleTapGesture(_ gesture: UITapGestureRecognizer) {
        guard let view = metalView else { return }
        let location = gesture.location(in: view)
        let drawableSize = view.drawableSize
        let scaleX = drawableSize.width / view.bounds.width
        let scaleY = drawableSize.height / view.bounds.height
        renderer.handleTouch(
            x: Float(location.x * scaleX),
            y: Float(location.y * scaleY),
            width: Int(drawableSize.width),
            height: Int(drawableSize.height)
        )
        requestRender()
        doCallback()
    }

    private func doCallback() {
        guard let state = renderer.mandelbrotState else { return }
        onUpdateState?(state.centerX, state.centerY, state.radius, state.numIterations, renderer.colorMapScale)
    }

    private func scheduleRedraw() {
        DispatchQueue.main.async { [weak self] in
            self?.metalView?.setNeedsDisplay()
        }
    }

    func cleanup() {
        renderer.cleanup()
    }
}

struct MandelbrotMetalView: UIViewRepresentable {
    let controller: MandelbrotMetalViewController

    func makeUIView(context: Context) -> MTKView {
        let view = MTKView()
        view.delegate = context.coordinator
        controller.setupView(view)
        return view
    }

    func updateUIView(_ uiView: MTKView, context: Context) {
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(controller: controller)
    }

    class Coordinator: NSObject, MTKViewDelegate {
        let controller: MandelbrotMetalViewController
        private var hasSetupSurface = false

        init(controller: MandelbrotMetalViewController) {
            self.controller = controller
        }

        func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
            let width = Int(size.width)
            let height = Int(size.height)
            if width > 0 && height > 0 {
                hasSetupSurface = true
                controller.surfaceChanged(width: width, height: height)
            }
        }

        func draw(in view: MTKView) {
            if !hasSetupSurface {
                let size = view.drawableSize
                let width = Int(size.width)
                let height = Int(size.height)
                if width > 0 && height > 0 {
                    hasSetupSurface = true
                    controller.surfaceChanged(width: width, height: height)
                }
            }
            controller.draw(in: view)
        }
    }
}

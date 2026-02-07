import SwiftUI
import GLKit
import OpenGLES

class MandelbrotGLViewController: NSObject {
    let renderer = MandelbrotGLRenderer()
    var glView: GLKView?
    private var displayLink: CADisplayLink?
    private var context: EAGLContext?

    private var touchDownX: CGFloat = 0
    private var touchDownY: CGFloat = 0
    private var touchSlop: CGFloat = 16

    var onUpdateState: ((String, String, String, Int, Float) -> Void)?

    func setupView(_ view: GLKView) {
        guard let ctx = EAGLContext(api: .openGLES3) else {
            print("Failed to create OpenGL ES 3.0 context")
            return
        }
        context = ctx
        view.context = ctx
        view.drawableColorFormat = .RGBA8888
        view.drawableDepthFormat = .formatNone
        view.enableSetNeedsDisplay = true
        glView = view

        let tapRecognizer = UITapGestureRecognizer(target: self, action: #selector(handleTapGesture(_:)))
        view.addGestureRecognizer(tapRecognizer)

        touchSlop = 16 * UIScreen.main.scale

        EAGLContext.setCurrent(ctx)
        renderer.setupGL()

        renderer.onNeedRedraw = { [weak self] in
            self?.scheduleRedraw()
        }
    }

    func surfaceChanged(width: Int, height: Int) {
        EAGLContext.setCurrent(context)
        renderer.setupSurface(width: width, height: height)
    }

    func draw() {
        EAGLContext.setCurrent(context)
        renderer.drawFrame()
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
        glView?.setNeedsDisplay()
    }

    func requestRender() {
        renderer.queueDraw()
        scheduleRedraw()
    }

    @objc private func handleTapGesture(_ gesture: UITapGestureRecognizer) {
        guard let view = glView else { return }
        let location = gesture.location(in: view)
        let scale = UIScreen.main.scale
        renderer.handleTouch(
            x: Float(location.x * scale),
            y: Float(location.y * scale),
            width: Int(view.bounds.width * scale),
            height: Int(view.bounds.height * scale)
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
            self?.glView?.setNeedsDisplay()
        }
    }

    func cleanup() {
        displayLink?.invalidate()
        displayLink = nil
        renderer.cleanup()
    }
}

struct MandelbrotGLView: UIViewRepresentable {
    let controller: MandelbrotGLViewController

    func makeUIView(context: Context) -> GLKView {
        let view = GLKView()
        view.delegate = context.coordinator
        controller.setupView(view)
        return view
    }

    func updateUIView(_ uiView: GLKView, context: Context) {
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(controller: controller)
    }

    class Coordinator: NSObject, GLKViewDelegate {
        let controller: MandelbrotGLViewController
        private var hasSetupSurface = false

        init(controller: MandelbrotGLViewController) {
            self.controller = controller
        }

        func glkView(_ view: GLKView, drawIn rect: CGRect) {
            let scale = UIScreen.main.scale
            let width = Int(view.bounds.width * scale)
            let height = Int(view.bounds.height * scale)

            if !hasSetupSurface && width > 0 && height > 0 {
                hasSetupSurface = true
                controller.surfaceChanged(width: width, height: height)
            }
            controller.draw()
        }
    }
}

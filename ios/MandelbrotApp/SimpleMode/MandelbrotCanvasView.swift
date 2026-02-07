import SwiftUI
import UIKit

struct MandelbrotCanvasView: UIViewRepresentable {
    let isJulia: Bool
    var onPointSelected: ((Double, Double) -> Void)?
    var onCoordsUpdated: ((Double, Double, Double) -> Void)?

    @Binding var numIterations: Int
    @Binding var power: Int
    @Binding var colorScheme: [UInt32]
    @Binding var renderTrigger: Int

    // For Julia view: seed coordinates
    var juliaX: Double = 0
    var juliaY: Double = 0

    // For resetting
    @Binding var resetTrigger: Int

    func makeUIView(context: Context) -> MandelbrotUIView {
        let view = MandelbrotUIView(isJulia: isJulia)
        view.onPointSelected = onPointSelected
        view.onCoordsUpdated = onCoordsUpdated
        view.isUserInteractionEnabled = !isJulia
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ uiView: MandelbrotUIView, context: Context) {
        var needsRender = false

        if uiView.currentIterations != numIterations {
            uiView.currentIterations = numIterations
            needsRender = true
        }
        if uiView.currentPower != power {
            uiView.currentPower = power
            needsRender = true
        }
        if uiView.colorScheme != colorScheme {
            uiView.colorScheme = colorScheme
            needsRender = true
        }
        if isJulia && (uiView.juliaX != juliaX || uiView.juliaY != juliaY) {
            uiView.juliaX = juliaX
            uiView.juliaY = juliaY
            needsRender = true
        }
        if uiView.lastResetTrigger != resetTrigger {
            uiView.lastResetTrigger = resetTrigger
            uiView.reset()
            return
        }
        if uiView.lastRenderTrigger != renderTrigger {
            uiView.lastRenderTrigger = renderTrigger
            needsRender = true
        }
        if needsRender && uiView.hasValidSize {
            uiView.render()
        }
    }
}

class MandelbrotUIView: UIView {
    private let calculator = MandelbrotCalculator()
    private let isJulia: Bool

    var showCrosshairs = false
    var onPointSelected: ((Double, Double) -> Void)?
    var onCoordsUpdated: ((Double, Double, Double) -> Void)?

    var currentIterations = MandelbrotCalculator.DEFAULT_ITERATIONS
    var currentPower = MandelbrotCalculator.DEFAULT_POWER
    var colorScheme: [UInt32] = []
    var juliaX: Double = MandelbrotCalculator.DEFAULT_JULIA_X_CENTER
    var juliaY: Double = MandelbrotCalculator.DEFAULT_JULIA_Y_CENTER

    var lastRenderTrigger = 0
    var lastResetTrigger = 0
    var hasValidSize = false

    private var pixelBuffer: UnsafeMutablePointer<UInt32>?
    private var bitmapContext: CGContext?
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var bufferSize: Int = 0

    private var xCenter: Double = MandelbrotCalculator.DEFAULT_X_CENTER
    private var yCenter: Double = MandelbrotCalculator.DEFAULT_Y_CENTER
    private var xExtent: Double = MandelbrotCalculator.DEFAULT_X_EXTENT

    private var xmin: Double = 0
    private var xmax: Double = 0
    private var ymin: Double = 0
    private var ymax: Double = 0

    private let startCoarseness = 16
    private var endCoarseness = 1

    private var previousX: CGFloat = 0
    private var previousY: CGFloat = 0
    private var pinchStartDistance: CGFloat = 0
    private var pinchStartPoint = CGPoint.zero
    private var touchMode = 0 // 0=none, 1=pan, 2=zoom

    private var renderQueue = DispatchQueue(label: "mandelbrot.render", attributes: .concurrent)
    private var terminateThreads = false
    private let renderGroup = DispatchGroup()

    private static let TOUCH_NONE = 0
    private static let TOUCH_PAN = 1
    private static let TOUCH_ZOOM = 2
    private static let CROSSHAIR_WIDTH: CGFloat = 16

    init(isJulia: Bool) {
        self.isJulia = isJulia
        if isJulia {
            xCenter = MandelbrotCalculator.DEFAULT_JULIA_X_CENTER
            yCenter = MandelbrotCalculator.DEFAULT_JULIA_Y_CENTER
            xExtent = MandelbrotCalculator.DEFAULT_JULIA_EXTENT
        }
        super.init(frame: .zero)
        isMultipleTouchEnabled = true
        contentMode = .redraw
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        terminateThreads = true
        calculator.signalTerminate()
        pixelBuffer?.deallocate()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = Int(bounds.width * contentScaleFactor)
        let h = Int(bounds.height * contentScaleFactor)
        if w > 0 && h > 0 && (w != screenWidth || h != screenHeight) {
            setupBitmap(width: w, height: h)
            hasValidSize = true
            render()
        }
    }

    private func setupBitmap(width: Int, height: Int) {
        terminateCurrentRender()
        screenWidth = width
        screenHeight = height
        bufferSize = width * height

        pixelBuffer?.deallocate()
        pixelBuffer = UnsafeMutablePointer<UInt32>.allocate(capacity: bufferSize)
        pixelBuffer?.initialize(repeating: 0, count: bufferSize)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        bitmapContext = CGContext(
            data: pixelBuffer,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
        )
        calculator.setPixelBuffer(pixelBuffer)
        initMinMax()
    }

    private func initMinMax() {
        let ratio = Double(screenHeight) / Double(max(screenWidth, 1))
        xmin = xCenter - xExtent / 2.0
        xmax = xCenter + xExtent / 2.0
        ymin = yCenter - ratio * xExtent / 2.0
        ymax = yCenter + ratio * xExtent / 2.0
    }

    func reset() {
        terminateCurrentRender()
        if isJulia {
            xCenter = MandelbrotCalculator.DEFAULT_JULIA_X_CENTER
            yCenter = MandelbrotCalculator.DEFAULT_JULIA_Y_CENTER
            xExtent = MandelbrotCalculator.DEFAULT_JULIA_EXTENT
        } else {
            xCenter = MandelbrotCalculator.DEFAULT_X_CENTER
            yCenter = MandelbrotCalculator.DEFAULT_Y_CENTER
            xExtent = MandelbrotCalculator.DEFAULT_X_EXTENT
        }
        currentIterations = MandelbrotCalculator.DEFAULT_ITERATIONS
        initMinMax()
        render()
    }

    func render() {
        terminateCurrentRender()
        guard screenWidth > 0, screenHeight > 0 else { return }

        xExtent = xmax - xmin
        xCenter = xmin + xExtent / 2.0
        yCenter = ymin + (ymax - ymin) / 2.0

        onCoordsUpdated?(xCenter, yCenter, xExtent)

        calculator.setColorPalette(colorScheme)
        calculator.setParameters(
            power: currentPower,
            numIterations: currentIterations,
            xMin: xmin, xMax: xmax,
            yMin: ymin, yMax: ymax,
            isJulia: isJulia,
            juliaX: juliaX, juliaY: juliaY,
            viewWidth: screenWidth, viewHeight: screenHeight
        )

        terminateThreads = false
        let numThreads = 2
        let stripHeight = screenHeight / numThreads

        for i in 0..<numThreads {
            let startY = i * stripHeight
            let height = (i == numThreads - 1) ? (screenHeight - startY) : stripHeight
            let startCoarseness = self.startCoarseness
            let endCoarseness = self.endCoarseness

            renderGroup.enter()
            renderQueue.async { [weak self] in
                defer { self?.renderGroup.leave() }
                guard let self = self else { return }

                var curLevel = startCoarseness
                while true {
                    self.calculator.drawFractal(
                        startX: 0, startY: startY,
                        startWidth: self.screenWidth, startHeight: height,
                        level: curLevel, doAll: curLevel == startCoarseness
                    )
                    DispatchQueue.main.async {
                        self.setNeedsDisplay()
                    }
                    if self.terminateThreads { break }
                    if curLevel <= endCoarseness { break }
                    curLevel /= 2
                }
            }
        }
    }

    private func terminateCurrentRender() {
        calculator.signalTerminate()
        terminateThreads = true
        renderGroup.wait()
        terminateThreads = false
    }

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext(),
              let bitmapContext = bitmapContext,
              let image = bitmapContext.makeImage() else { return }

        // CGContext draws images flipped, so we flip the coordinate system
        ctx.saveGState()
        ctx.translateBy(x: 0, y: bounds.height)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: bounds)
        ctx.restoreGState()

        if showCrosshairs {
            let midX = bounds.midX
            let midY = bounds.midY
            let hw = MandelbrotUIView.CROSSHAIR_WIDTH
            ctx.setStrokeColor(UIColor.white.cgColor)
            ctx.setLineWidth(1.5)
            ctx.move(to: CGPoint(x: midX - hw, y: midY))
            ctx.addLine(to: CGPoint(x: midX + hw, y: midY))
            ctx.move(to: CGPoint(x: midX, y: midY - hw))
            ctx.addLine(to: CGPoint(x: midX, y: midY + hw))
            ctx.strokePath()
        }
    }

    // MARK: - Touch handling

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        previousX = touch.location(in: self).x
        previousY = touch.location(in: self).y
        endCoarseness = startCoarseness
        touchMode = MandelbrotUIView.TOUCH_NONE
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let allTouches = event?.allTouches else { return }
        let touchArray = Array(allTouches)

        if touchArray.count == 1 {
            // Pan
            if touchMode != MandelbrotUIView.TOUCH_PAN {
                let loc = touchArray[0].location(in: self)
                previousX = loc.x
                previousY = loc.y
                touchMode = MandelbrotUIView.TOUCH_PAN
            }
            let loc = touchArray[0].location(in: self)
            let dx = loc.x - previousX
            let dy = loc.y - previousY
            previousX = loc.x
            previousY = loc.y

            if dx != 0 || dy != 0 {
                let scale = contentScaleFactor
                let amountX = Double(dx * scale) / Double(screenWidth) * (xmax - xmin)
                let amountY = Double(dy * scale) / Double(screenHeight) * (ymax - ymin)
                xmin -= amountX
                xmax -= amountX
                ymin -= amountY
                ymax -= amountY
            }
            endCoarseness = startCoarseness
            notifyPointSelected(touchArray[0])
        } else if touchArray.count >= 2 {
            // Pinch zoom
            let p1 = touchArray[0].location(in: self)
            let p2 = touchArray[1].location(in: self)
            let dist = pinchDistance(p1, p2)
            let center = CGPoint(x: (p1.x + p2.x) * 0.5, y: (p1.y + p2.y) * 0.5)

            if touchMode != MandelbrotUIView.TOUCH_ZOOM {
                pinchStartDistance = dist
                pinchStartPoint = center
                previousX = center.x
                previousY = center.y
                touchMode = MandelbrotUIView.TOUCH_ZOOM
            } else {
                previousX = center.x
                previousY = center.y
                let pinchScale = dist / pinchStartDistance
                pinchStartDistance = dist

                if pinchScale > 0 {
                    let scale = contentScaleFactor
                    let ptX = Double(center.x * scale) / Double(screenWidth)
                    let ptY = Double(center.y * scale) / Double(screenHeight)
                    let cx = xmin + (xmax - xmin) * ptX
                    let cy = ymin + (ymax - ymin) * ptY
                    xmin = cx - (cx - xmin) / Double(pinchScale)
                    xmax = cx + (xmax - cx) / Double(pinchScale)
                    ymin = cy - (cy - ymin) / Double(pinchScale)
                    ymax = cy + (ymax - cy) / Double(pinchScale)
                }
            }
            endCoarseness = startCoarseness
        }
        render()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        touchMode = MandelbrotUIView.TOUCH_NONE
        endCoarseness = 1
        if let touch = touches.first {
            notifyPointSelected(touch)
        }
        render()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        touchesEnded(touches, with: event)
    }

    private func notifyPointSelected(_ touch: UITouch) {
        let loc = touch.location(in: self)
        let scale = contentScaleFactor
        let fx = xmin + Double(loc.x * scale) * (xmax - xmin) / Double(screenWidth)
        let fy = ymin + Double(loc.y * scale) * (ymax - ymin) / Double(screenHeight)
        onPointSelected?(fx, fy)
    }

    private func pinchDistance(_ p1: CGPoint, _ p2: CGPoint) -> CGFloat {
        let dx = p1.x - p2.x
        let dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    // MARK: - Save image

    func saveImage() -> UIImage? {
        guard let bitmapContext = bitmapContext,
              let cgImage = bitmapContext.makeImage() else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

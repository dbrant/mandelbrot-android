import Foundation

struct OrbitResult {
    let orbitData: UnsafeMutablePointer<Float>
    let orbitSize: Int
    let polyScaled: [Float]
    let polyLim: Int
    let polyScaleExp: Int
    let radiusExp: Double
}

class MandelbrotState {
    static let ITERATIONS = 2048
    static let INIT_X = -0.5
    static let INIT_Y = 0.0
    static let INIT_R = 2.0
    static let INIT_COLOR_SCALE: Float = 23.0

    private var nativePtr: OpaquePointer?

    var numIterations: Int = ITERATIONS {
        didSet {
            numIterations = max(numIterations, 2)
            if let ptr = nativePtr {
                mandel_setIterations(UnsafeMutableRawPointer(ptr), Int32(numIterations))
            }
        }
    }

    init() {
        let ptr = mandel_createState(MandelbrotState.INIT_X, MandelbrotState.INIT_Y, MandelbrotState.INIT_R, Int32(numIterations))
        nativePtr = OpaquePointer(ptr)
    }

    deinit {
        destroy()
    }

    func destroy() {
        if let ptr = nativePtr {
            mandel_destroyState(UnsafeMutableRawPointer(ptr))
            nativePtr = nil
        }
    }

    func set(x: Double, y: Double, r: Double, iterations: Int) {
        guard let ptr = nativePtr else { return }
        mandel_setState(UnsafeMutableRawPointer(ptr), x, y, r, Int32(iterations))
        numIterations = iterations
    }

    func set(x: String, y: String, r: String, iterations: Int) {
        guard let ptr = nativePtr else { return }
        mandel_setStateStr(UnsafeMutableRawPointer(ptr), x, y, r, Int32(iterations))
        numIterations = iterations
    }

    func zoomIn(dx: Double, dy: Double, factor: Double) {
        guard let ptr = nativePtr else { return }
        mandel_zoomIn(UnsafeMutableRawPointer(ptr), dx, dy, factor)
    }

    func zoomOut(factor: Double) {
        guard let ptr = nativePtr else { return }
        mandel_zoomOut(UnsafeMutableRawPointer(ptr), factor)
    }

    func generateOrbit() -> OrbitResult {
        let result = mandel_generateOrbit(UnsafeMutableRawPointer(nativePtr!))
        let t = result.polyScaled
        let polyScaled: [Float] = [t.0, t.1, t.2, t.3, t.4, t.5]
        return OrbitResult(
            orbitData: result.orbitData,
            orbitSize: Int(result.orbitSize),
            polyScaled: polyScaled,
            polyLim: Int(result.polyLim),
            polyScaleExp: Int(result.polyScaleExp),
            radiusExp: result.radiusExp
        )
    }

    var centerX: String {
        guard let ptr = nativePtr else { return "" }
        return String(cString: mandel_getCenterX(UnsafeMutableRawPointer(ptr)))
    }

    var centerY: String {
        guard let ptr = nativePtr else { return "" }
        return String(cString: mandel_getCenterY(UnsafeMutableRawPointer(ptr)))
    }

    var radius: String {
        guard let ptr = nativePtr else { return "" }
        return String(cString: mandel_getRadius(UnsafeMutableRawPointer(ptr)))
    }

    func reset() {
        set(x: MandelbrotState.INIT_X, y: MandelbrotState.INIT_Y, r: MandelbrotState.INIT_R, iterations: MandelbrotState.ITERATIONS)
    }
}

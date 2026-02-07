import Foundation

class MandelbrotCalculator {
    static let DEFAULT_POWER = 2
    static let DEFAULT_ITERATIONS = 128
    static let MAX_ITERATIONS = 2048
    static let MIN_ITERATIONS = 2
    static let DEFAULT_X_CENTER = -0.5
    static let DEFAULT_Y_CENTER = 0.0
    static let DEFAULT_X_EXTENT = 3.0
    static let DEFAULT_JULIA_X_CENTER = 0.0
    static let DEFAULT_JULIA_Y_CENTER = 0.0
    static let DEFAULT_JULIA_EXTENT = 3.0
    static let BAILOUT = 4.0

    private var power: Int = DEFAULT_POWER
    private var numIterations: Int = DEFAULT_ITERATIONS
    private var xmin: Double = DEFAULT_X_CENTER - DEFAULT_X_EXTENT / 2.0
    private var xmax: Double = DEFAULT_X_CENTER + DEFAULT_X_EXTENT / 2.0
    private var ymin: Double = DEFAULT_Y_CENTER - DEFAULT_X_EXTENT / 2.0
    private var ymax: Double = DEFAULT_Y_CENTER + DEFAULT_X_EXTENT / 2.0
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var isJulia: Bool = false
    private var juliaX: Double = DEFAULT_JULIA_X_CENTER
    private var juliaY: Double = DEFAULT_JULIA_Y_CENTER

    private var colorPalette: UnsafeMutablePointer<UInt32>?
    private var numPaletteColors: Int = 0

    private var pixelBuffer: UnsafeMutablePointer<UInt32>?
    private var x0array: UnsafeMutablePointer<Double>?
    private var x0arrayCapacity: Int = 0
    private var colorPaletteCapacity: Int = 0

    private var terminateJob: Bool = false

    init() {}

    deinit {
        x0array?.deallocate()
        colorPalette?.deallocate()
    }

    func setParameters(
        power: Int,
        numIterations: Int,
        xMin: Double, xMax: Double,
        yMin: Double, yMax: Double,
        isJulia: Bool,
        juliaX: Double, juliaY: Double,
        viewWidth: Int, viewHeight: Int
    ) {
        self.power = power
        self.numIterations = numIterations
        self.xmin = xMin
        self.xmax = xMax
        self.ymin = yMin
        self.ymax = yMax
        self.viewWidth = viewWidth
        self.viewHeight = viewHeight
        self.isJulia = isJulia
        self.juliaX = juliaX
        self.juliaY = juliaY
        let needed = viewWidth * 2
        if needed > x0arrayCapacity {
            x0array?.deallocate()
            x0array = UnsafeMutablePointer<Double>.allocate(capacity: needed)
            x0arrayCapacity = needed
        }
        self.terminateJob = false
    }

    func setPixelBuffer(_ buffer: UnsafeMutablePointer<UInt32>?) {
        self.pixelBuffer = buffer
    }

    func setColorPalette(_ colors: [UInt32]) {
        let count = colors.count
        if count > colorPaletteCapacity {
            colorPalette?.deallocate()
            colorPalette = UnsafeMutablePointer<UInt32>.allocate(capacity: count)
            colorPaletteCapacity = count
        }
        colors.withUnsafeBufferPointer { src in
            colorPalette?.update(from: src.baseAddress!, count: count)
        }
        numPaletteColors = count
    }

    func signalTerminate() {
        terminateJob = true
    }

    func drawFractal(
        startX: Int, startY: Int,
        startWidth: Int, startHeight: Int,
        level: Int, doAll: Bool
    ) {
        guard level >= 1, let pixelBuffer = pixelBuffer, let x0array = x0array, let colorPalette = colorPalette else { return }

        let maxY = startY + startHeight
        let maxX = startX + startWidth
        let xscale = (xmax - xmin) / Double(viewWidth)
        let yscale = (ymax - ymin) / Double(viewHeight)

        let iterScale = numIterations < numPaletteColors ? numPaletteColors / numIterations : 1

        // Pre-calculate x values
        for px in startX..<maxX {
            x0array[px] = xmin + Double(px) * xscale
        }

        var yindex = 0
        var py = startY
        while py < maxY && !terminateJob {
            let y0 = ymin + Double(py) * yscale
            let yptr = py * viewWidth

            var xindex = 0
            var px = startX
            while px < maxX {
                if !doAll {
                    if (yindex % 2 == 0) && (xindex % 2 == 0) {
                        px += level
                        xindex += 1
                        continue
                    }
                }

                let iteration: Int
                switch power {
                case 3:
                    iteration = calculateIterations3(x0Val: x0array[px], y0Val: y0, maxIterations: numIterations)
                case 4:
                    iteration = calculateIterations4(x0Val: x0array[px], y0Val: y0, maxIterations: numIterations)
                default:
                    iteration = calculateIterations2(x0Val: x0array[px], y0Val: y0, maxIterations: numIterations)
                }

                let color: UInt32
                if iteration >= numIterations {
                    color = 0
                } else {
                    color = colorPalette[(iteration * iterScale) % numPaletteColors]
                }

                // Fill the level x level block
                if level > 1 {
                    var yptr2 = yptr
                    for _ in py..<min(py + level, maxY) {
                        let maxIx = min(px + level, maxX)
                        for ix in px..<maxIx {
                            pixelBuffer[yptr2 + ix] = color
                        }
                        yptr2 += viewWidth
                    }
                } else {
                    pixelBuffer[yptr + px] = color
                }

                px += level
                xindex += 1
            }
            py += level
            yindex += 1
        }
    }

    private func calculateIterations2(x0Val: Double, y0Val: Double, maxIterations: Int) -> Int {
        if isJulia {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            while iteration < maxIterations {
                let x2 = x * x
                let y2 = y * y
                if x2 + y2 > MandelbrotCalculator.BAILOUT { break }
                y = 2 * x * y + juliaY
                x = x2 - y2 + juliaX
                iteration += 1
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var iteration = 0
            while x2 + y2 < MandelbrotCalculator.BAILOUT && iteration <= maxIterations {
                y = 2 * x * y + y0Val
                x = x2 - y2 + x0Val
                x2 = x * x
                y2 = y * y
                iteration += 1
            }
            return iteration
        }
    }

    private func calculateIterations3(x0Val: Double, y0Val: Double, maxIterations: Int) -> Int {
        if isJulia {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            while iteration < maxIterations {
                let x2 = x * x
                let y2 = y * y
                let x3 = x2 * x
                let y3 = y2 * y
                if x2 + y2 > MandelbrotCalculator.BAILOUT { break }
                y = (3 * x2 * y) - y3 + juliaY
                x = x3 - (3 * y2 * x) + juliaX
                iteration += 1
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var x3 = 0.0
            var y3 = 0.0
            var iteration = 0
            while x2 + y2 < MandelbrotCalculator.BAILOUT && iteration <= maxIterations {
                y = (3 * x2 * y) - y3 + y0Val
                x = x3 - (3 * y2 * x) + x0Val
                x2 = x * x
                y2 = y * y
                x3 = x2 * x
                y3 = y2 * y
                iteration += 1
            }
            return iteration
        }
    }

    private func calculateIterations4(x0Val: Double, y0Val: Double, maxIterations: Int) -> Int {
        if isJulia {
            var x = x0Val
            var y = y0Val
            var iteration = 0
            while iteration < maxIterations {
                let x2 = x * x
                let y2 = y * y
                let x3 = x2 * x
                let y3 = y2 * y
                let x4 = x3 * x
                let y4 = y3 * y
                if x2 + y2 > MandelbrotCalculator.BAILOUT { break }
                y = (4 * x3 * y) - (4 * y3 * x) + juliaY
                x = x4 + y4 - (6 * x2 * y2) + juliaX
                iteration += 1
            }
            return iteration
        } else {
            var x = 0.0
            var y = 0.0
            var x2 = 0.0
            var y2 = 0.0
            var x3 = 0.0
            var y3 = 0.0
            var x4 = 0.0
            var y4 = 0.0
            var iteration = 0
            while x2 + y2 < MandelbrotCalculator.BAILOUT && iteration <= maxIterations {
                y = (4 * x3 * y) - (4 * y3 * x) + y0Val
                x = x4 + y4 - (6 * x2 * y2) + x0Val
                x2 = x * x
                y2 = y * y
                x3 = x2 * x
                y3 = y2 * y
                x4 = x3 * x
                y4 = y3 * y
                iteration += 1
            }
            return iteration
        }
    }
}

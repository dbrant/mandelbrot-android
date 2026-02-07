import Foundation
import Combine

class AdvancedModeViewModel: ObservableObject {
    @Published var xCenter: String
    @Published var yCenter: String
    @Published var xExtent: String
    @Published var numIterations: Int
    @Published var colorScale: Float

    private let defaults = UserDefaults.standard

    init() {
        xCenter = defaults.string(forKey: "gmp_xcenter") ?? String(MandelbrotState.INIT_X)
        yCenter = defaults.string(forKey: "gmp_ycenter") ?? String(MandelbrotState.INIT_Y)
        xExtent = defaults.string(forKey: "gmp_xextent") ?? String(MandelbrotState.INIT_R)
        numIterations = defaults.object(forKey: "gmp_iterations") as? Int ?? MandelbrotState.ITERATIONS
        colorScale = defaults.object(forKey: "gmp_colorscale") as? Float ?? MandelbrotState.INIT_COLOR_SCALE
    }

    func save() {
        defaults.set(xCenter, forKey: "gmp_xcenter")
        defaults.set(yCenter, forKey: "gmp_ycenter")
        defaults.set(xExtent, forKey: "gmp_xextent")
        defaults.set(numIterations, forKey: "gmp_iterations")
        defaults.set(colorScale, forKey: "gmp_colorscale")
    }
}

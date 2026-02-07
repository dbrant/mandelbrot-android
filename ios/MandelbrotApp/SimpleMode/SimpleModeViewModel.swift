import Foundation

class SimpleModeViewModel: ObservableObject {
    @Published var xCenter: Double
    @Published var yCenter: Double
    @Published var xExtent: Double
    @Published var numIterations: Int
    @Published var power: Int
    @Published var currentColorScheme: Int
    @Published var juliaEnabled: Bool

    private let defaults = UserDefaults.standard

    init() {
        xCenter = defaults.object(forKey: "simple_xcenter") as? Double ?? MandelbrotCalculator.DEFAULT_X_CENTER
        yCenter = defaults.object(forKey: "simple_ycenter") as? Double ?? MandelbrotCalculator.DEFAULT_Y_CENTER
        xExtent = defaults.object(forKey: "simple_xextent") as? Double ?? MandelbrotCalculator.DEFAULT_X_EXTENT
        numIterations = defaults.object(forKey: "simple_iterations") as? Int ?? MandelbrotCalculator.DEFAULT_ITERATIONS
        power = defaults.object(forKey: "simple_power") as? Int ?? MandelbrotCalculator.DEFAULT_POWER
        currentColorScheme = defaults.object(forKey: "simple_colorscheme") as? Int ?? 0
        juliaEnabled = defaults.bool(forKey: "simple_juliaEnabled")
    }

    func save() {
        defaults.set(xCenter, forKey: "simple_xcenter")
        defaults.set(yCenter, forKey: "simple_ycenter")
        defaults.set(xExtent, forKey: "simple_xextent")
        defaults.set(numIterations, forKey: "simple_iterations")
        defaults.set(power, forKey: "simple_power")
        defaults.set(currentColorScheme, forKey: "simple_colorscheme")
        defaults.set(juliaEnabled, forKey: "simple_juliaEnabled")
    }
}

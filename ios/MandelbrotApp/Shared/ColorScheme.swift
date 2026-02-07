import Foundation

struct ColorSchemeManager {
    // Android ARGB color constants
    private static let BLUE:   UInt32 = 0xFF0000FF
    private static let GREEN:  UInt32 = 0xFF00FF00
    private static let RED:    UInt32 = 0xFFFF0000
    private static let YELLOW: UInt32 = 0xFFFFFF00
    private static let MAGENTA: UInt32 = 0xFFFF00FF
    private static let WHITE:  UInt32 = 0xFFFFFFFF
    private static let BLACK:  UInt32 = 0xFF000000

    private(set) static var colorSchemes: [[UInt32]] = []

    static func initColorSchemes() {
        colorSchemes = [
            createColorScheme(colors: [BLUE, GREEN, RED, BLUE], numElements: 256),
            createColorScheme(colors: [YELLOW, MAGENTA, BLUE, GREEN, YELLOW], numElements: 256),
            createColorScheme(colors: [WHITE, BLACK, WHITE], numElements: 256),
            createColorScheme(colors: [BLACK, WHITE, BLACK], numElements: 256),
            [BLACK, WHITE]
        ]
    }

    static func getShiftedScheme(colors: [UInt32], shiftAmount: Int) -> [UInt32] {
        let count = colors.count
        var shifted = [UInt32](repeating: 0, count: count)
        for i in 0..<count {
            shifted[i] = colors[(i + shiftAmount) % count]
        }
        return shifted
    }

    private static func createColorScheme(colors colorArray: [UInt32], numElements: Int) -> [UInt32] {
        let elementsPerStep = numElements / (colorArray.count - 1)
        var colors = [UInt32](repeating: 0, count: numElements)
        var r: Float = 0
        var g: Float = 0
        var b: Float = 0
        var rInc: Float = 0
        var gInc: Float = 0
        var bInc: Float = 0
        var cIndex = 0
        var cCounter = 0

        for i in 0..<numElements {
            if cCounter == 0 {
                b = Float(colorArray[cIndex] & 0xFF)
                g = Float((colorArray[cIndex] & 0xFF00) >> 8)
                r = Float((colorArray[cIndex] & 0xFF0000) >> 16)
                if cIndex < colorArray.count - 1 {
                    bInc = (Float(colorArray[cIndex + 1] & 0xFF) - b) / Float(elementsPerStep)
                    gInc = (Float((colorArray[cIndex + 1] & 0xFF00) >> 8) - g) / Float(elementsPerStep)
                    rInc = (Float((colorArray[cIndex + 1] & 0xFF0000) >> 16) - r) / Float(elementsPerStep)
                }
                cIndex += 1
                cCounter = elementsPerStep
            }
            colors[i] = 0xFF000000 | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
            b += bInc
            g += gInc
            r += rInc
            b = min(max(b, 0), 255)
            g = min(max(g, 0), 255)
            r = min(max(r, 0), 255)
            cCounter -= 1
        }
        return colors
    }
}

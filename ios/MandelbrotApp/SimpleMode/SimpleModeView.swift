import SwiftUI

struct SimpleModeView: View {
    @StateObject private var viewModel = SimpleModeViewModel()

    @State private var showSettings = false
    @State private var showAbout = false
    @State private var showSaveSuccess = false
    @State private var showSaveError = false
    @State private var saveErrorMessage = ""

    @State private var renderTrigger = 0
    @State private var resetTrigger = 0
    @State private var juliaRenderTrigger = 0
    @State private var juliaResetTrigger = 0

    @State private var juliaX: Double = MandelbrotCalculator.DEFAULT_X_CENTER
    @State private var juliaY: Double = MandelbrotCalculator.DEFAULT_Y_CENTER

    @State private var iterationSliderValue: Double = sqrt(Double(MandelbrotCalculator.DEFAULT_ITERATIONS))

    // Reference to the main mandelbrot UIView for saving
    @State private var mandelbrotView: MandelbrotUIView?

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height

            ZStack(alignment: .leading) {
                // Main Mandelbrot view
                MandelbrotCanvasView(
                    isJulia: false,
                    onPointSelected: { x, y in
                        juliaX = x
                        juliaY = y
                        if viewModel.juliaEnabled {
                            juliaRenderTrigger += 1
                        }
                    },
                    onCoordsUpdated: { cx, cy, ext in
                        viewModel.xCenter = cx
                        viewModel.yCenter = cy
                        viewModel.xExtent = ext
                    },
                    numIterations: $viewModel.numIterations,
                    power: $viewModel.power,
                    colorScheme: Binding(
                        get: { currentColors },
                        set: { _ in }
                    ),
                    renderTrigger: $renderTrigger,
                    resetTrigger: $resetTrigger
                )
                .ignoresSafeArea()

                // Julia view
                if viewModel.juliaEnabled {
                    MandelbrotCanvasView(
                        isJulia: true,
                        numIterations: $viewModel.numIterations,
                        power: $viewModel.power,
                        colorScheme: Binding(
                            get: { juliaColors },
                            set: { _ in }
                        ),
                        renderTrigger: $juliaRenderTrigger,
                        juliaX: juliaX,
                        juliaY: juliaY,
                        resetTrigger: $juliaResetTrigger
                    )
                    .frame(
                        width: isLandscape ? geometry.size.width / 2 - 24 : nil,
                        height: isLandscape ? nil : geometry.size.height / 2 - 24
                    )
                    .frame(
                        maxWidth: isLandscape ? nil : .infinity,
                        maxHeight: isLandscape ? .infinity : nil,
                        alignment: isLandscape ? .leading : .bottom
                    )
                    .border(Color.white.opacity(0.3), width: 1)
                    .allowsHitTesting(false)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: isLandscape ? .leading : .bottom)
                }

                // Settings panel
                if showSettings {
                    settingsPanel
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button(action: { showSettings.toggle() }) {
                    Image(systemName: "slider.horizontal.3")
                }

                Button(action: toggleJulia) {
                    Image(systemName: viewModel.juliaEnabled ? "square.split.2x1.fill" : "square.split.2x1")
                }

                Button(action: cycleColorScheme) {
                    Image(systemName: "paintpalette")
                }

                Menu {
                    Button(action: saveImage) {
                        Label("Save Image", systemImage: "square.and.arrow.down")
                    }
                    Button(action: resetView) {
                        Label("Reset", systemImage: "arrow.counterclockwise")
                    }
                    Button(action: { showAbout = true }) {
                        Label("About", systemImage: "info.circle")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .alert("About", isPresented: $showAbout) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Mandelbrot Set explorer.\nBy Dmitry Brant.")
        }
        .alert("Image Saved", isPresented: $showSaveSuccess) {
            Button("OK", role: .cancel) {}
        }
        .alert("Save Error", isPresented: $showSaveError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(saveErrorMessage)
        }
        .onAppear {
            ColorSchemeManager.initColorSchemes()
            iterationSliderValue = sqrt(Double(viewModel.numIterations))
        }
        .onDisappear {
            viewModel.save()
        }
    }

    private var currentColors: [UInt32] {
        let schemes = ColorSchemeManager.colorSchemes
        guard !schemes.isEmpty else { return [0xFF000000, 0xFFFFFFFF] }
        let idx = viewModel.currentColorScheme % schemes.count
        return schemes[idx]
    }

    private var juliaColors: [UInt32] {
        let schemes = ColorSchemeManager.colorSchemes
        guard !schemes.isEmpty else { return [0xFF000000, 0xFFFFFFFF] }
        let idx = viewModel.currentColorScheme % schemes.count
        let scheme = schemes[idx]
        return ColorSchemeManager.getShiftedScheme(colors: scheme, shiftAmount: scheme.count / 2)
    }

    private var settingsPanel: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Iterations: \(viewModel.numIterations)")
                    .foregroundColor(.white)
                Spacer()
            }

            Slider(
                value: $iterationSliderValue,
                in: Double(MandelbrotCalculator.MIN_ITERATIONS).squareRoot()...Double(MandelbrotCalculator.MAX_ITERATIONS).squareRoot(),
                step: 1
            )
            .onChange(of: iterationSliderValue) { newValue in
                let newIter = Int(newValue * newValue)
                if newIter != viewModel.numIterations {
                    viewModel.numIterations = max(MandelbrotCalculator.MIN_ITERATIONS, min(newIter, MandelbrotCalculator.MAX_ITERATIONS))
                    renderTrigger += 1
                    juliaRenderTrigger += 1
                }
            }

            HStack(spacing: 16) {
                Text("Power:")
                    .foregroundColor(.white)

                ForEach([2, 3, 4], id: \.self) { p in
                    Button(action: {
                        viewModel.power = p
                        renderTrigger += 1
                        juliaRenderTrigger += 1
                    }) {
                        Text("z^\(p)")
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(viewModel.power == p ? Color.accentColor : Color.gray)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                }
                Spacer()
            }
        }
        .padding()
        .background(Color.black.opacity(0.8))
    }

    private func toggleJulia() {
        viewModel.juliaEnabled.toggle()
    }

    private func cycleColorScheme() {
        viewModel.currentColorScheme += 1
        if viewModel.currentColorScheme >= ColorSchemeManager.colorSchemes.count {
            viewModel.currentColorScheme = 0
        }
        renderTrigger += 1
        juliaRenderTrigger += 1
    }

    private func resetView() {
        viewModel.numIterations = MandelbrotCalculator.DEFAULT_ITERATIONS
        viewModel.power = MandelbrotCalculator.DEFAULT_POWER
        iterationSliderValue = sqrt(Double(MandelbrotCalculator.DEFAULT_ITERATIONS))
        resetTrigger += 1
        juliaResetTrigger += 1
    }

    private func saveImage() {
        // Find the MandelbrotUIView in the view hierarchy
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first,
              let mandelbrotUIView = findMandelbrotUIView(in: window) else {
            saveErrorMessage = "Could not find the view to save."
            showSaveError = true
            return
        }

        guard let image = mandelbrotUIView.saveImage() else {
            saveErrorMessage = "Could not create image."
            showSaveError = true
            return
        }

        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        showSaveSuccess = true
    }

    private func findMandelbrotUIView(in view: UIView) -> MandelbrotUIView? {
        if let mv = view as? MandelbrotUIView, !mv.isUserInteractionEnabled == false {
            // Return the first non-Julia view (which has interaction enabled)
            if mv.isUserInteractionEnabled {
                return mv
            }
        }
        for subview in view.subviews {
            if let found = findMandelbrotUIView(in: subview) {
                if found.isUserInteractionEnabled {
                    return found
                }
            }
        }
        return nil
    }
}

import SwiftUI
import Foundation

struct AdvancedModeView: View {
    @StateObject private var viewModel = AdvancedModeViewModel()
    @State private var showSettings = false
    @State private var showAbout = false
    @State private var showSaveSuccess = false
    @State private var saveMessage = ""

    @State private var metalController = MandelbrotMetalViewController()
    @State private var hasInitialized = false

    private var iterationsSliderValue: Int {
        let val = log2(Double(viewModel.numIterations)) - 10
        return max(0, min(12, Int(val)))
    }

    var body: some View {
        ZStack {
            MandelbrotMetalView(controller: metalController)
                .ignoresSafeArea()
                .onAppear {
                    if !hasInitialized {
                        hasInitialized = true
                        metalController.onUpdateState = { centerX, centerY, radius, iterations, colorScale in
                            viewModel.xCenter = centerX
                            viewModel.yCenter = centerY
                            viewModel.xExtent = radius
                            viewModel.numIterations = iterations
                            viewModel.colorScale = colorScale
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            metalController.initState(
                                centerX: viewModel.xCenter,
                                centerY: viewModel.yCenter,
                                radius: viewModel.xExtent,
                                iterations: viewModel.numIterations,
                                colorScale: viewModel.colorScale
                            )
                        }
                    }
                }
                .onDisappear {
                    viewModel.save()
                }

            VStack {
                Spacer()

                if showSettings {
                    settingsPanel
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .ignoresSafeArea(.container, edges: .bottom)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button(action: { withAnimation { showSettings.toggle() } }) {
                    Image(systemName: "slider.horizontal.3")
                }

                Button(action: { metalController.zoomOut(2.0) }) {
                    Image(systemName: "minus.magnifyingglass")
                }

                Button(action: saveImage) {
                    Image(systemName: "square.and.arrow.down")
                }

                Button(action: {
                    metalController.reset()
                    viewModel.numIterations = MandelbrotState.ITERATIONS
                    viewModel.colorScale = MandelbrotState.INIT_COLOR_SCALE
                }) {
                    Image(systemName: "arrow.counterclockwise")
                }

                Button(action: { showAbout = true }) {
                    Image(systemName: "info.circle")
                }
            }
        }
        .alert("About", isPresented: $showAbout) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("GPU-based perturbation theory renderer using arbitrary-precision arithmetic (GMP/MPFR) for deep zooming beyond double-precision limits.\n\nTap to zoom in.")
        }
        .alert(saveMessage, isPresented: $showSaveSuccess) {
            Button("OK", role: .cancel) {}
        }
    }

    private var settingsPanel: some View {
        VStack(spacing: 12) {
            // Info text
            VStack(alignment: .leading, spacing: 2) {
                Text("Re: \(viewModel.xCenter)")
                    .font(.caption2)
                    .lineLimit(1)
                Text("Im: \(viewModel.yCenter)")
                    .font(.caption2)
                    .lineLimit(1)
                Text("Radius: \(viewModel.xExtent)")
                    .font(.caption2)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundColor(.white)

            // Iterations slider
            HStack {
                Text("Iterations: \(viewModel.numIterations)")
                    .font(.caption)
                    .foregroundColor(.white)
                    .frame(width: 120, alignment: .leading)

                Button(action: {
                    let newVal = max(0, iterationsSliderValue - 1)
                    doSetIterations(newVal)
                }) {
                    Image(systemName: "minus.circle")
                        .foregroundColor(.white)
                }

                Slider(value: Binding(
                    get: { Double(iterationsSliderValue) },
                    set: { doSetIterations(Int($0)) }
                ), in: 0...12, step: 1)

                Button(action: {
                    let newVal = min(12, iterationsSliderValue + 1)
                    doSetIterations(newVal)
                }) {
                    Image(systemName: "plus.circle")
                        .foregroundColor(.white)
                }
            }

            // Color scale slider
            HStack {
                Text("Color: \(Int(viewModel.colorScale))")
                    .font(.caption)
                    .foregroundColor(.white)
                    .frame(width: 120, alignment: .leading)

                Button(action: {
                    let newVal = max(0, viewModel.colorScale - 1)
                    viewModel.colorScale = newVal
                    metalController.setCmapScale(newVal)
                }) {
                    Image(systemName: "minus.circle")
                        .foregroundColor(.white)
                }

                Slider(value: Binding(
                    get: { Double(viewModel.colorScale) },
                    set: {
                        viewModel.colorScale = Float($0)
                        metalController.setCmapScale(Float($0))
                    }
                ), in: 0...200, step: 1)

                Button(action: {
                    let newVal = min(200, viewModel.colorScale + 1)
                    viewModel.colorScale = newVal
                    metalController.setCmapScale(newVal)
                }) {
                    Image(systemName: "plus.circle")
                        .foregroundColor(.white)
                }
            }
        }
        .padding()
        .background(.ultraThinMaterial.opacity(0.9))
        .background(Color.black.opacity(0.5))
    }

    private func doSetIterations(_ sliderValue: Int) {
        let iterations = Int(pow(2.0, Double(sliderValue + 10)))
        viewModel.numIterations = iterations
        metalController.setIterations(iterations)
    }

    private func saveImage() {
        metalController.saveToBitmap { image in
            guard let image = image else {
                saveMessage = "Failed to capture image."
                showSaveSuccess = true
                return
            }
            UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
            saveMessage = "Image saved to Photos."
            showSaveSuccess = true
        }
    }
}

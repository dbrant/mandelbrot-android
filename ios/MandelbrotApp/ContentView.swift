import SwiftUI

struct ContentView: View {
    @State private var showAbout = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                Text("Mandelbrot Set")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Spacer()

                NavigationLink(destination: SimpleModeView()) {
                    Label("Simple rendering", systemImage: "square.grid.3x3")
                        .font(.title3)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }

                NavigationLink(destination: AdvancedModeView()) {
                    Label("Experimental rendering", systemImage: "cpu")
                        .font(.title3)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.gray)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                }

                Spacer()
            }
            .padding(.horizontal, 32)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("About") {
                        showAbout = true
                    }
                }
            }
            .alert("About", isPresented: $showAbout) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Mandelbrot Set explorer.\nBy Dmitry Brant.")
            }
        }
    }
}
